const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM,VirtualConsole}=require('jsdom');

const html=fs.readFileSync(path.join(__dirname,'../../main/resources/html/admin/student.html'),'utf8');
const adminScript=fs.readFileSync(path.join(__dirname,'../../main/resources/js/admin/admin-student-profile.js'),'utf8');
const teacherScript=fs.readFileSync(path.join(__dirname,'../../main/resources/js/teacher/build_student.js'),'utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,20));

const student={
  id:17,
  firstName:'Ada <b>',
  lastName:'Lovelace',
  email:'ada.login@example.test',
  schoolClass:{id:12,label:'7b'},
  graduationLevel:2
};

function stripTemplate(value) {
  return value
    .replace('%[student_dashboard;script=build_student.js;nav=!FOLLOWS]','')
    .replace('%[admin_student_nav]','')
    .replace('<script src="/admin-student-profile.js" defer></script>','');
}

async function setup(options={}) {
  const calls={fetchStudentData:[],fetchClasses:[],confirms:[]};
  let innerHTMLWrites=0;
  const virtualConsole=new VirtualConsole();
  virtualConsole.on('jsdomError',()=>{});
  const dom=new JSDOM(`<!doctype html><html><body>${stripTemplate(html)}</body></html>`,{
    url:'https://school.example.invalid/student',
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
  dom.window.graduationLevels=['Neustarter','Starter','Durchstarter','Lernprofi'];
  if (options.selectedStudentId !== undefined) {
    dom.window.sessionStorage.setItem('selectedStudentId',String(options.selectedStudentId));
  }
  if (options.currentStudent !== undefined) {
    dom.window.sessionStorage.setItem('currentStudent',JSON.stringify(options.currentStudent));
  }
  dom.window.fetchStudentData=async id=>{
    calls.fetchStudentData.push(id);
    if (options.fetchReject) throw new Error('hidden backend failure');
    return options.student === undefined ? student : options.student;
  };
  dom.window.fetchClasses=async()=>{
    calls.fetchClasses.push(true);
    return options.classes || [
      {id:11,label:'6a'},
      {id:12,label:'7b'},
      {id:13,label:'Altklasse',active:false},
      {id:0,label:'Serverklasse 0'}
    ];
  };
  dom.window.confirm=message=>{
    calls.confirms.push(message);
    return options.confirm !== false;
  };
  dom.window.eval(adminScript);
  await tick();
  await tick();
  return {dom,calls,innerHTMLWrites:()=>innerHTMLWrites};
}

function classOptions(document) {
  return [...document.querySelectorAll('#adminStudentClass option')].map(option=>[option.value,option.textContent]);
}

test('admin student page no longer uses currentStudent inline data',async()=>{
  assert.doesNotMatch(html,/currentStudent/);
  assert.doesNotMatch(adminScript,/currentStudent/);
  const {dom}=await setup({
    selectedStudentId:17,
    currentStudent:{id:99,firstName:'Stale',lastName:'Copy',email:'stale@example.test',graduationLevel:0,classId:11}
  });
  try {
    assert.equal(dom.window.document.getElementById('adminStudentFirst').value,'Ada <b>');
  } finally {
    dom.window.close();
  }
});

test('selectedStudentId is read and fetchStudentData is called with the selected id',async()=>{
  const {dom,calls}=await setup({selectedStudentId:17});
  try {
    assert.deepEqual(calls.fetchStudentData,[17]);
  } finally {
    dom.window.close();
  }
});

test('student profile fields are filled from backend data',async()=>{
  const {dom}=await setup({selectedStudentId:17});
  try {
    const document=dom.window.document;
    assert.equal(document.getElementById('adminStudentFirst').value,'Ada <b>');
    assert.equal(document.getElementById('adminStudentLast').value,'Lovelace');
    assert.equal(document.getElementById('adminStudentEmail').value,'ada.login@example.test');
    assert.equal(document.getElementById('adminStudentId').value,'17');
  } finally {
    dom.window.close();
  }
});

test('class select offers unassigned active classes and selects current class',async()=>{
  const {dom}=await setup({selectedStudentId:17});
  try {
    assert.deepEqual(classOptions(dom.window.document),[
      ['0','Nicht zugeordnet'],
      ['11','6a'],
      ['12','7b']
    ]);
    assert.equal(dom.window.document.getElementById('adminStudentClass').value,'12');
  } finally {
    dom.window.close();
  }
});

test('student class id is robust across supported json shapes',async()=>{
  const {dom}=await setup({
    selectedStudentId:17,
    student:{...student,schoolClass:undefined,class:{id:11,label:'6a'},classId:12}
  });
  try {
    assert.equal(dom.window.document.getElementById('adminStudentClass').value,'11');
  } finally {
    dom.window.close();
  }
});

test('graduation level is selected and password stays empty',async()=>{
  const {dom}=await setup({selectedStudentId:17});
  try {
    assert.equal(dom.window.document.getElementById('adminStudentLevel').value,'2');
    assert.equal(dom.window.document.getElementById('adminStudentPassword').value,'');
  } finally {
    dom.window.close();
  }
});

test('password label and form post target stay correct',()=>{
  const dom=new JSDOM(`<!doctype html><html><body>${stripTemplate(html)}</body></html>`);
  try {
    const passwordLabel=dom.window.document.querySelector('label[for="adminStudentPassword"]');
    assert.equal(passwordLabel?.textContent.trim(),'Neues Passwort (optional)');
    assert.equal(dom.window.document.getElementById('adminStudentForm')?.getAttribute('action'),'/edit-student-profile');
    assert.equal(dom.window.document.getElementById('adminStudentForm')?.getAttribute('method'),'post');
  } finally {
    dom.window.close();
  }
});

test('form asks for German confirmation before submit',async()=>{
  const {dom,calls}=await setup({selectedStudentId:17});
  try {
    const event=new dom.window.Event('submit',{bubbles:true,cancelable:true});
    dom.window.document.getElementById('adminStudentForm').dispatchEvent(event);
    assert.equal(calls.confirms[0],'Änderungen an diesem Schüler speichern?');
    assert.equal(event.defaultPrevented,false);
  } finally {
    dom.window.close();
  }
});

test('missing selectedStudentId shows message and does not fetch student data',async()=>{
  const {dom,calls}=await setup();
  try {
    assert.deepEqual(calls.fetchStudentData,[]);
    assert.match(dom.window.document.body.textContent,/Kein Schüler ausgewählt\./);
    assert.equal(dom.window.document.querySelector('a[href="/manage_students"]')?.textContent.trim(),'Zurück zur Schülerverwaltung');
    assert.equal(dom.window.document.getElementById('adminStudentForm').hidden,true);
  } finally {
    dom.window.close();
  }
});

test('invalid selectedStudentId shows message and does not fetch student data',async()=>{
  const {dom,calls}=await setup({selectedStudentId:'abc'});
  try {
    assert.deepEqual(calls.fetchStudentData,[]);
    assert.match(dom.window.document.body.textContent,/Kein Schüler ausgewählt\./);
  } finally {
    dom.window.close();
  }
});

test('failed student lookup shows German load error',async()=>{
  const {dom}=await setup({selectedStudentId:17,fetchReject:true});
  try {
    assert.match(dom.window.document.body.textContent,/Der Schüler konnte nicht geladen werden\./);
    assert.equal(dom.window.document.getElementById('adminStudentForm').hidden,true);
  } finally {
    dom.window.close();
  }
});

test('empty student lookup shows German load error',async()=>{
  const {dom}=await setup({selectedStudentId:17,student:null});
  try {
    assert.match(dom.window.document.body.textContent,/Der Schüler konnte nicht geladen werden\./);
  } finally {
    dom.window.close();
  }
});

test('admin student profile renders student data without innerHTML',async()=>{
  const {dom,innerHTMLWrites}=await setup({selectedStudentId:17,trackInnerHTML:true});
  try {
    assert.equal(innerHTMLWrites(),0);
    assert.equal(dom.window.document.querySelectorAll('#admin-student-profile b').length,0);
    assert.equal(dom.window.document.getElementById('adminStudentFirst').value,'Ada <b>');
  } finally {
    dom.window.close();
  }
});

test('admin student detail has no inline handlers or destructive archive actions',()=>{
  const combined=html + '\n' + adminScript;
  assert.doesNotMatch(combined,/onclick=/i);
  assert.doesNotMatch(combined,/onsubmit=/i);
  assert.doesNotMatch(combined,/\/delete-student/);
  const dom=new JSDOM(`<!doctype html><html><body>${stripTemplate(html)}</body></html>`);
  try {
    assert.doesNotMatch(dom.window.document.body.textContent,/Löschen|Archivieren|Wiederherstellen/);
  } finally {
    dom.window.close();
  }
});

test('teacher student view still loads selected student dashboard',async()=>{
  const calls={studentData:[],subjects:[],dashboard:[]};
  const dom=new JSDOM('<!doctype html><html><body><div id="subject-list"></div></body></html>',{
    url:'https://school.example.invalid/student',
    runScripts:'outside-only',
    virtualConsole:new VirtualConsole()
  });
  dom.window.sessionStorage.setItem('selectedStudentId','17');
  dom.window.fetchStudentData=async id=>{
    calls.studentData.push(id);
    return {id,firstName:'Ada'};
  };
  dom.window.fetchStudentSubjects=async id=>{
    calls.subjects.push(id);
    return [{id:5,name:'Mathe'}];
  };
  dom.window.loadStudentDashboard=(studentData,subjects,teacherView)=>{
    calls.dashboard.push({studentData,subjects,teacherView});
  };
  dom.window.eval(teacherScript);
  await tick();
  try {
    assert.deepEqual(calls.studentData,[17]);
    assert.deepEqual(calls.subjects,[17]);
    assert.equal(calls.dashboard[0].teacherView,true);
    assert.equal(calls.dashboard[0].studentData.id,17);
  } finally {
    dom.window.close();
  }
});

test('teacher student view does not fetch without a valid selected student id',async()=>{
  const calls={studentData:[]};
  const dom=new JSDOM('<!doctype html><html><body></body></html>',{
    url:'https://school.example.invalid/student',
    runScripts:'outside-only',
    virtualConsole:new VirtualConsole()
  });
  dom.window.fetchStudentData=async id=>calls.studentData.push(id);
  dom.window.fetchStudentSubjects=async()=>[];
  dom.window.loadStudentDashboard=()=>{};
  dom.window.eval(teacherScript);
  await tick();
  try {
    assert.deepEqual(calls.studentData,[]);
  } finally {
    dom.window.close();
  }
});
