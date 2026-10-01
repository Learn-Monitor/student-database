const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM,VirtualConsole}=require('jsdom');

const resources=path.join(__dirname,'../../main/resources');
const html=fs.readFileSync(path.join(resources,'html/admin/teacher.html'),'utf8');
const script=fs.readFileSync(path.join(resources,'js/admin/build_teacher.js'),'utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,30));

async function page(options={}) {
  const virtualConsole=new VirtualConsole();
  virtualConsole.on('jsdomError',()=>{});
  const dom=new JSDOM(`<!doctype html><body>${html.replace('%[site;title=Lehrkraft verwalten;content=!FOLLOWS]','')}</body>`,{
    url:'https://school.invalid/teacher',runScripts:'outside-only',virtualConsole
  });
  dom.window.sessionStorage.setItem('selectedTeacherId','51');
  const requests=[];
  dom.window.fetchJson=async url=>url==='/teachers'?[{id:51,firstName:'Ada',lastName:'Example',email:'ada@example.invalid'}]:null;
  dom.window.fetch=async (url,request={})=>{
    const body=request.body?JSON.parse(request.body):{};
    requests.push({url,body});
    if(url==='/delete-teacher'&&body.action==='preflight') return {ok:true,json:async()=>options.blocked
      ? {deletable:false,groups:[{key:'tutor_assignments',label:'Tutorzuordnungen',count:1}],protectedReason:null}
      : {deletable:true,groups:[],protectedReason:null}};
    if(url==='/delete-teacher'&&body.action==='delete') return {ok:true,json:async()=>({deleted:true})};
    throw new Error(`Unexpected request ${url}`);
  };
  dom.window.eval(script);
  dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
  await tick();
  return {dom,requests};
}

test('teacherDeleteShowsBlockingReasonsAndDisablesButton',async()=>{
  const {dom,requests}=await page({blocked:true});
  try {
    assert.match(dom.window.document.querySelector('#teacherDeleteStatus').textContent,/kann derzeit nicht gelöscht werden/);
    assert.match(dom.window.document.querySelector('#teacherDeleteReasons').textContent,/Tutorzuordnungen/);
    assert.equal(dom.window.document.querySelector('#deleteTeacherButton').disabled,true);
    assert.deepEqual(requests,[{url:'/delete-teacher',body:{id:51,action:'preflight'}}]);
  } finally {dom.window.close();}
});

test('teacherDeleteAvailableWhenUnusedRequiresAccessibleConfirmation',async()=>{
  const {dom,requests}=await page();
  try {
    const button=dom.window.document.querySelector('#deleteTeacherButton');
    assert.equal(button.disabled,false);
    button.click();
    assert.equal(dom.window.document.querySelector('#teacherDeleteConfirmation').hidden,false);
    assert.match(dom.window.document.querySelector('#teacherDeleteName').textContent,/Ada Example/);
    dom.window.document.querySelector('#confirmDeleteTeacher').click();
    await tick();
    assert.deepEqual(requests.map(request=>request.body.action),['preflight','delete']);
    assert.equal(dom.window.document.querySelector('#teacher-profile-form').querySelector('[name="password"]')?.getAttribute('autocomplete'),'new-password');
  } finally {dom.window.close();}
});

test('teacherDetailUsesAdminShellAndPreservesAssignmentAndProfileFlows',()=>{
  const dom=new JSDOM(`<!doctype html><body>${html.replace('%[site;title=Lehrkraft verwalten;content=!FOLLOWS]','')}</body>`);
  try {
    assert.ok(dom.window.document.querySelector('#dashboard.admin-shell'));
    assert.ok(dom.window.document.querySelector('a[href="/manage_teachers"]'));
    assert.equal(dom.window.document.querySelector('#teacher-profile-form').getAttribute('action'),'/edit-teacher-profile');
    assert.match(dom.window.document.body.textContent,/Schuljahr & Zuordnungen/);
    assert.equal(dom.window.document.querySelector('#teacherDeleteConfirmation button[type="button"]')?.textContent.includes('Abbrechen'),true);
  } finally {dom.window.close();}
});
