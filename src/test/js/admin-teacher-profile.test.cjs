const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM,VirtualConsole}=require('jsdom');

const html=fs.readFileSync(path.join(__dirname,'../../main/resources/html/admin/teacher.html'),'utf8');
const script=fs.readFileSync(path.join(__dirname,'../../main/resources/js/admin/build_teacher.js'),'utf8');
const siteScript=fs.readFileSync(path.join(__dirname,'../../main/resources/js/site/student-database.js'),'utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,20));

const teachers=[
  {id:7,firstName:'Stale',lastName:'Copy',email:'stale@example.test'},
  {id:42,firstName:'Ada <b>',lastName:'Lovelace',email:'ada.login@example.test'}
];

function stripTemplate(value) {
  return value
    .replace('%[site;title=Lehrkraft bearbeiten;content=!FOLLOWS]','')
    .replace('%[admin_teacher_nav]','')
    .replace('<script src="build_teacher.js"></script>','');
}

async function setup(options={}) {
  const calls={fetchJson:[],confirms:[]};
  let innerHTMLWrites=0;
  const virtualConsole=new VirtualConsole();
  virtualConsole.on('jsdomError',()=>{});
  const dom=new JSDOM(`<!doctype html><html><body>${stripTemplate(html)}</body></html>`,{
    url:'https://school.example.invalid/teacher',
    runScripts:'outside-only',
    virtualConsole
  });
  if (options.trackInnerHTML) {
    const descriptor=Object.getOwnPropertyDescriptor(dom.window.Element.prototype,'innerHTML');
    Object.defineProperty(dom.window.Element.prototype,'innerHTML',{
      get: descriptor.get,
      set(value) {
        innerHTMLWrites += 1;
        return descriptor.set.call(this,value);
      }
    });
  }
  if (options.selectedTeacherId !== undefined) {
    dom.window.sessionStorage.setItem('selectedTeacherId',String(options.selectedTeacherId));
  }
  if (options.currentTeacher !== undefined) {
    dom.window.sessionStorage.setItem('currentTeacher',JSON.stringify(options.currentTeacher));
  }
  dom.window.fetchJson=async url=>{
    calls.fetchJson.push(url);
    if (options.fetchReject) throw new Error('hidden backend failure');
    return options.teachers === undefined ? teachers : options.teachers;
  };
  dom.window.confirm=message=>{
    calls.confirms.push(message);
    return options.confirm !== false;
  };
  dom.window.eval(script);
  dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
  await tick();
  await tick();
  return {dom,calls,innerHTMLWrites:()=>innerHTMLWrites};
}

function message(document) {
  return document.getElementById('teacher-load-message').textContent;
}

test('viewTeacher stores selectedTeacherId and clears currentTeacher',()=>{
  const virtualConsole=new VirtualConsole();
  virtualConsole.on('jsdomError',()=>{});
  const dom=new JSDOM('<!doctype html><html><body></body></html>',{
    url:'https://school.example.invalid/manage_teachers',
    runScripts:'outside-only',
    virtualConsole
  });
  try {
    dom.window.sessionStorage.setItem('currentTeacher',JSON.stringify({id:42}));
    dom.window.eval(siteScript);
    dom.window.viewTeacher(42);
    assert.equal(dom.window.sessionStorage.getItem('selectedTeacherId'),'42');
    assert.equal(dom.window.sessionStorage.getItem('currentTeacher'),null);
    assert.match(siteScript,/window\.location\.href = '\/teacher'/);
  } finally {
    dom.window.close();
  }
});

test('viewTeacher rejects missing invalid and non-positive ids',()=>{
  const dom=new JSDOM('<!doctype html><html><body></body></html>',{
    url:'https://school.example.invalid/manage_teachers',
    runScripts:'outside-only'
  });
  try {
    dom.window.eval(siteScript);
    dom.window.viewTeacher('abc');
    dom.window.viewTeacher(0);
    dom.window.viewTeacher(-1);
    dom.window.viewTeacher(1.5);
    assert.equal(dom.window.sessionStorage.getItem('selectedTeacherId'),null);
    assert.equal(dom.window.location.pathname,'/manage_teachers');
  } finally {
    dom.window.close();
  }
});

test('teacher profile loads current server data by selectedTeacherId',async()=>{
  const {dom,calls}=await setup({
    selectedTeacherId:42,
    currentTeacher:{id:42,firstName:'Old',lastName:'Name',email:'old@example.test'}
  });
  try {
    const document=dom.window.document;
    assert.deepEqual(calls.fetchJson,['/teachers']);
    assert.equal(document.getElementById('teacher-name').textContent,'Ada <b> Lovelace');
    assert.equal(document.getElementById('teacherId').value,'42');
    assert.equal(document.getElementById('teacherFirstName').value,'Ada <b>');
    assert.equal(document.getElementById('teacherLastName').value,'Lovelace');
    assert.equal(document.getElementById('teacherEmail').value,'ada.login@example.test');
    assert.equal(document.getElementById('teacherPassword').value,'');
    assert.equal(document.getElementById('teacher-profile-form').hidden,false);
  } finally {
    dom.window.close();
  }
});

test('teacher profile confirmation is bound in JavaScript',async()=>{
  const {dom,calls}=await setup({selectedTeacherId:42,confirm:false});
  try {
    const event=new dom.window.Event('submit',{bubbles:true,cancelable:true});
    dom.window.document.getElementById('teacher-profile-form').dispatchEvent(event);
    assert.deepEqual(calls.confirms,['Änderungen an dieser Lehrkraft speichern?']);
    assert.equal(event.defaultPrevented,true);
  } finally {
    dom.window.close();
  }
});

test('teacher profile removes legacy assignment block and links to school year assignments',()=>{
  assert.doesNotMatch(html,/%\\[teacher_infos\\]/);
  assert.doesNotMatch(html,/onsubmit=/i);
  assert.match(html,/Neues Passwort \(optional\)/);
  assert.match(html,/\/dashboard#schuljahr/);
  assert.match(html,/Klassen-, Fach- und Semesterzuordnungen/);
});

test('teacher profile script no longer calls legacy assignment helpers',()=>{
  assert.doesNotMatch(script,/currentTeacher/);
  assert.doesNotMatch(script,/console\\.log/);
  assert.doesNotMatch(script,/fetchTeacherClasses|fetchSubjects|buildTeacherDashboard/);
});

test('missing selectedTeacherId shows no-selection without fetching',async()=>{
  const {dom,calls}=await setup();
  try {
    assert.deepEqual(calls.fetchJson,[]);
    assert.equal(message(dom.window.document),'Keine Lehrkraft ausgewählt.');
    assert.equal(dom.window.document.getElementById('teacher-profile-form').hidden,true);
    assert.equal(dom.window.document.querySelector('#teacher-back-link a').getAttribute('href'),'/manage_teachers');
  } finally {
    dom.window.close();
  }
});

test('invalid selectedTeacherId shows no-selection without fetching',async()=>{
  const {dom,calls}=await setup({selectedTeacherId:'abc'});
  try {
    assert.deepEqual(calls.fetchJson,[]);
    assert.equal(message(dom.window.document),'Keine Lehrkraft ausgewählt.');
    assert.equal(dom.window.document.getElementById('teacher-profile-form').hidden,true);
  } finally {
    dom.window.close();
  }
});

test('unknown selectedTeacherId shows German load error',async()=>{
  const {dom,calls}=await setup({selectedTeacherId:99});
  try {
    assert.deepEqual(calls.fetchJson,['/teachers']);
    assert.equal(message(dom.window.document),'Die Lehrkraft konnte nicht geladen werden.');
    assert.equal(dom.window.document.getElementById('teacher-profile-form').hidden,true);
  } finally {
    dom.window.close();
  }
});

test('teacher fetch failure shows German load error',async()=>{
  const {dom,calls}=await setup({selectedTeacherId:42,fetchReject:true});
  try {
    assert.deepEqual(calls.fetchJson,['/teachers']);
    assert.equal(message(dom.window.document),'Die Lehrkraft konnte nicht geladen werden.');
    assert.equal(dom.window.document.getElementById('teacher-profile-form').hidden,true);
  } finally {
    dom.window.close();
  }
});

test('teacher profile renders teacher data without innerHTML',async()=>{
  const {dom,innerHTMLWrites}=await setup({selectedTeacherId:42,trackInnerHTML:true});
  try {
    assert.equal(innerHTMLWrites(),0);
    assert.equal(dom.window.document.getElementById('teacher-name').innerHTML.includes('<b>'),false);
  } finally {
    dom.window.close();
  }
});
