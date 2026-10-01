const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM,VirtualConsole}=require('jsdom');

const subjectHtml=fs.readFileSync(path.join(__dirname,'../../main/resources/html/admin/subject.html'),'utf8');
const subjectScript=fs.readFileSync(path.join(__dirname,'../../main/resources/js/admin/build_subject.js'),'utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,20));

function html() {
  return `<!doctype html><html><body>${subjectHtml.replace('%[site;title=Fach verwalten;content=!FOLLOWS]','').replace('%[admin_subject_nav]','')}</body></html>`;
}

function setupSubjectPage() {
  const virtualConsole=new VirtualConsole();
  virtualConsole.on('jsdomError',()=>{});
  return new JSDOM(html(),{url:'https://school.example.invalid/subject',runScripts:'outside-only',virtualConsole});
}

async function runSubjectScript(options={}) {
  const dom=setupSubjectPage();
  const alerts=[],confirms=[],deletes=[];
  if (dom.window.document.readyState === 'loading') {
    await new Promise(resolve=>dom.window.document.addEventListener('DOMContentLoaded',resolve,{once:true}));
  }
  dom.window.sessionStorage.setItem('currentSubject',JSON.stringify(options.subject||{id:42,name:'Mathematik'}));
  dom.window.alert=message=>alerts.push(message);
  dom.window.confirm=message=>{confirms.push(message);return options.confirm!==false;};
  dom.window.fetch=async (url,request={})=>{
    const body=request.body?JSON.parse(request.body):{};
    if (url==='/curriculum-enrollment-catalog') return {ok:true,json:async()=>({subjects:[]})};
    if (url==='/delete-subject') {
      deletes.push({id:body.id,action:body.action});
      if (body.action==='preflight') return {ok:true,json:async()=>options.blocked
        ? {deletable:false,groups:[{key:'course_groups',label:'Lerngruppen',count:1}],protectedReason:null}
        : {deletable:true,groups:[],protectedReason:null}};
      return options.deleteOk===false
        ? {ok:false,status:409,json:async()=>({message:'Wird verwendet',preflight:{deletable:false,groups:[{label:'Lerngruppen',count:1}],protectedReason:null}})}
        : {ok:true,status:200,json:async()=>({deleted:true})};
    }
    throw new Error(`Unexpected request ${url}`);
  };
  dom.window.eval(subjectScript);
  dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
  await tick();
  return {dom,alerts,confirms,deletes};
}

test('subject page keeps subject master data editing and central curriculum link',()=>{
  const dom=setupSubjectPage();
  try {
    assert.match(dom.window.document.querySelector('h1')?.textContent||'',/Fach bearbeiten/);
    assert.ok(dom.window.document.querySelector('#subjectNameField'));
    const form=dom.window.document.querySelector('#editSubjectForm');
    assert.equal(form?.getAttribute('action'),'/edit-subject');
    assert.match(form?.textContent||'',/Speichern/);
    const link=dom.window.document.querySelector('a[href="/dashboard#curriculum"]');
    assert.equal(link?.textContent.trim(),'Zentrales Curriculum verwalten');
    assert.match(dom.window.document.body.textContent,/Zentrale Themen, Etappen und Münzwerte werden semesterbezogen im zentralen Curriculum verwaltet\./);
  } finally {
    dom.window.close();
  }
});

test('subject page no longer embeds legacy curriculum administration',()=>{
  const dom=setupSubjectPage();
  try {
    for (const id of ['curriculum','topicTable','deleteAllTopics','grade-list','add-grade','topic-list','gradeSelect']) {
      assert.equal(dom.window.document.querySelector(`#${id}`),null);
    }
    assert.equal(subjectHtml.includes('/curriculum.js'),false);
    assert.doesNotMatch(dom.window.document.body.textContent,/Alle löschen|Klassenstufe hinzufügen|Themen in diesem Fach|Anteil/);
  } finally {
    dom.window.close();
  }
});

test('build_subject no longer calls legacy curriculum helpers or delete-topics',()=>{
  assert.doesNotMatch(subjectScript,/populateTopicTable/);
  assert.doesNotMatch(subjectScript,/populateGradeList/);
  assert.doesNotMatch(subjectScript,/populateGradeSelect/);
  assert.doesNotMatch(subjectScript,/delete-topics/);
});

test('build_subject loads subject name and id safely from session storage',async()=>{
  const {dom}=await runSubjectScript({subject:{id:77,name:'Deutsch <b>'}});
  try {
    assert.equal(dom.window.document.querySelector('#subjectNameField').value,'Deutsch <b>');
    assert.equal(dom.window.document.querySelector('.subjectId').value,'77');
    assert.equal(dom.window.document.querySelector('#editSubjectForm').getAttribute('action'),'/edit-subject');
  } finally {
    dom.window.close();
  }
});

test('subjectDeleteAvailableWhenUnusedAndUsesAccessibleInlineConfirmation',async()=>{
  const {dom,alerts,confirms,deletes}=await runSubjectScript();
  try {
    const button=dom.window.document.querySelector('#deleteSubjectButton');
    assert.equal(button.disabled,false);
    button.click();
    assert.equal(dom.window.document.querySelector('#subjectDeleteConfirmation').hidden,false);
    assert.match(dom.window.document.querySelector('#subjectDeleteName').textContent,/Mathematik/);
    dom.window.document.querySelector('#confirmDeleteSubject').click();
    await tick();
    assert.deepEqual(deletes,[{id:42,action:'preflight'},{id:42,action:'delete'}]);
    assert.deepEqual(alerts,[]);
    assert.deepEqual(confirms,[]);
  } finally {
    dom.window.close();
  }
});

test('subjectDeleteShowsBlockingReasonsAndDisablesButtonWhenInUse',async()=>{
  const {dom,deletes}=await runSubjectScript({blocked:true});
  try {
    assert.equal(dom.window.document.querySelector('#deleteSubjectButton').disabled,true);
    assert.match(dom.window.document.querySelector('#subjectDeleteStatus').textContent,/kann derzeit nicht gelöscht werden/);
    assert.match(dom.window.document.querySelector('#subjectDeleteReasons').textContent,/1 · Lerngruppen/);
    assert.deepEqual(deletes,[{id:42,action:'preflight'}]);
  } finally {
    dom.window.close();
  }
});

test('subjectDeleteConflictRefreshesPreflightAndNeverUsesAlerts',async()=>{
  const {dom,alerts}=await runSubjectScript({deleteOk:false});
  try {
    dom.window.document.querySelector('#deleteSubjectButton').click();
    dom.window.document.querySelector('#confirmDeleteSubject').click();
    await tick();
    assert.match(dom.window.document.querySelector('#subjectDeleteReasons').textContent,/Lerngruppen/);
    assert.deepEqual(alerts,[]);
  } finally { dom.window.close(); }
});
