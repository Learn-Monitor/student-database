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
  dom.window.deleteSubject=async id=>{deletes.push(id);return {ok:options.deleteOk!==false};};
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

test('build_subject deletes subject with German confirmation and messages',async()=>{
  const {dom,alerts,confirms,deletes}=await runSubjectScript();
  try {
    dom.window.document.querySelector('#deleteSubjectButton').click();
    await tick();
    assert.deepEqual(deletes,[42]);
    assert.equal(confirms[0],'Fach wirklich löschen? Dies ist nur möglich, wenn keine Zuordnungen, Curriculuminhalte oder Leistungsdaten vorhanden sind.');
    assert.equal(alerts[0],'Fach wurde gelöscht.');
  } finally {
    dom.window.close();
  }
});

test('build_subject shows German failure message when deletion is blocked',async()=>{
  const {dom,alerts,deletes}=await runSubjectScript({deleteOk:false});
  try {
    dom.window.document.querySelector('#deleteSubjectButton').click();
    await tick();
    assert.deepEqual(deletes,[42]);
    assert.equal(alerts[0],'Das Fach konnte nicht gelöscht werden. Möglicherweise wird es noch verwendet.');
  } finally {
    dom.window.close();
  }
});
