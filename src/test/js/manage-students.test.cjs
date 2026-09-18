const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM,VirtualConsole}=require('jsdom');

const html=fs.readFileSync(path.join(__dirname,'../../main/resources/html/admin/manage_students.html'),'utf8');
const script=fs.readFileSync(path.join(__dirname,'../../main/resources/js/admin/manage_students.js'),'utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,20));

const students=[
  {id:3,firstName:'Zoë',lastName:'Ärger',email:'zoe@example.test',schoolClass:{id:12,label:'7b'},graduationLevel:1},
  {id:1,firstName:'Berta',lastName:'Albrecht',email:'berta@example.test',schoolClass:{id:0,label:'Nicht zugeordnet'},graduationLevel:2},
  {id:2,firstName:'Ada',lastName:'Albrecht',email:'ada.login@example.test',schoolClass:{id:11,label:'6a'},graduationLevel:0}
];

function stripTemplate(value) {
  return value
    .replace('%[site;title=Schülerverwaltung;content=!FOLLOWS]','')
    .replace('%[admin_nav]','')
    .replace('<script src="/manage_students.js" defer></script>','');
}

async function setup(options={}) {
  const calls={fetchJson:[],viewed:[],changed:[],downloads:[],subjects:[]};
  let innerHTMLWrites=0;
  const virtualConsole=new VirtualConsole();
  virtualConsole.on('jsdomError',()=>{});
  const dom=new JSDOM(`<!doctype html><html><body>${stripTemplate(html)}</body></html>`,{
    url:'https://school.example.invalid/manage_students',
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
  dom.window.fetchJson=async url=>{
    calls.fetchJson.push(url);
    assert.equal(url,'/students');
    return options.students || students;
  };
  dom.window.fetchClasses=async()=>options.classes || [
    {id:11,label:'6a'},
    {id:12,label:'7b'}
  ];
  dom.window.fetchAllSubjects=async()=>[{id:5,name:'Mathe'}];
  dom.window.populateSubjectSelect=(id,subjects)=>{
    calls.subjects.push({id,subjects});
    const select=dom.window.document.getElementById(id);
    select.replaceChildren(...subjects.map(subject=>{
      const option=dom.window.document.createElement('option');
      option.value=String(subject.id);
      option.textContent=subject.name;
      return option;
    }));
  };
  dom.window.viewStudent=id=>calls.viewed.push(id);
  dom.window.changeGraduationLevel=async(id,graduationLevel)=>{
    calls.changed.push({id,graduationLevel});
    return {ok:options.changeOk!==false};
  };
  dom.window.postDataAndDownload=(url,data,filename)=>calls.downloads.push({url,data,filename});
  dom.window.alert=()=>{};
  dom.window.eval(script);
  dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
  await tick();
  return {dom,calls,innerHTMLWrites:()=>innerHTMLWrites};
}

function rows(document) {
  return [...document.querySelectorAll('#studentTableBody tr')];
}

function rowTexts(document) {
  return rows(document).map(row=>[...row.children].map(cell=>cell.textContent.trim()));
}

test('/students is loaded and downloads/import remain present',async()=>{
  const {dom,calls}=await setup();
  try {
    assert.ok(calls.fetchJson.includes('/students'));
    assert.equal(calls.fetchJson.includes('/archived-students'),false);
    assert.equal(dom.window.document.querySelector('#add-students-form')?.getAttribute('action'),'/add-students');
    assert.equal(dom.window.document.querySelector('#download-students')?.closest('a')?.getAttribute('href'),'/students');
    assert.equal(dom.window.document.querySelector('#download-student-results')?.closest('a')?.getAttribute('href'),'/all-student-results');
    dom.window.document.querySelector('#csv-input').value='csv-data';
    dom.window.document.querySelector('#add-students-form').dispatchEvent(new dom.window.Event('submit',{bubbles:true,cancelable:true}));
    assert.deepEqual(calls.downloads[0],{url:'/add-students',data:'csv-data',filename:'schueler.csv'});
  } finally {
    dom.window.close();
  }
});

test('student table has five requested columns',async()=>{
  const {dom}=await setup();
  try {
    const headers=[...dom.window.document.querySelectorAll('#studentTable th')].map(th=>th.textContent.trim());
    assert.deepEqual(headers,['Nachname','Vorname','Klasse','Abschlussstufe','Aktionen']);
    assert.equal(rows(dom.window.document)[0].children.length,5);
  } finally {
    dom.window.close();
  }
});

test('last and first names are separated and sorted by German locale',async()=>{
  const {dom}=await setup();
  try {
    assert.deepEqual(rowTexts(dom.window.document).map(cells=>cells.slice(0,2)),[
      ['Albrecht','Ada'],
      ['Albrecht','Berta'],
      ['Ärger','Zoë']
    ]);
  } finally {
    dom.window.close();
  }
});

test('class filter includes all unassigned and active classes',async()=>{
  const {dom}=await setup();
  try {
    const options=[...dom.window.document.querySelectorAll('#classSelect option')].map(option=>[option.value,option.textContent]);
    assert.deepEqual(options,[['-1','Alle Klassen'],['0','Nicht zugeordnet'],['11','6a'],['12','7b']]);
    dom.window.document.getElementById('classSelect').value='11';
    dom.window.document.getElementById('classSelect').dispatchEvent(new dom.window.Event('change'));
    assert.deepEqual(rowTexts(dom.window.document).map(cells=>cells[1]),['Ada']);
  } finally {
    dom.window.close();
  }
});

for (const [label,query,expected] of [
  ['first name','zoë',['Zoë']],
  ['last name','ärger',['Zoë']],
  ['login','ada.login',['Ada']],
  ['class','7b',['Zoë']]
]) test(`search filters by ${label}`,async()=>{
  const {dom}=await setup();
  try {
    const input=dom.window.document.getElementById('studentFilter');
    input.value=query;
    input.dispatchEvent(new dom.window.Event('input'));
    assert.deepEqual(rowTexts(dom.window.document).map(cells=>cells[1]),expected);
  } finally {
    dom.window.close();
  }
});

test('edit button uses the correct student id',async()=>{
  const {dom,calls}=await setup();
  try {
    dom.window.document.querySelector('#studentTableBody .edit-student').click();
    assert.deepEqual(calls.viewed,[2]);
  } finally {
    dom.window.close();
  }
});

test('graduation level remains changeable',async()=>{
  const {dom,calls}=await setup();
  try {
    const select=dom.window.document.querySelector('#studentTableBody tr select');
    select.value='3';
    select.dispatchEvent(new dom.window.Event('change'));
    await tick();
    assert.deepEqual(calls.changed,[{id:2,graduationLevel:3}]);
  } finally {
    dom.window.close();
  }
});

test('student rows do not use innerHTML',async()=>{
  const {dom,innerHTMLWrites}=await setup({trackInnerHTML:true,students:[
    {id:9,firstName:'Ada <b>',lastName:'Markup',email:'x@example.test',schoolClass:{id:11,label:'6a <i>'},graduationLevel:0}
  ]});
  try {
    assert.equal(innerHTMLWrites(),0);
    assert.equal(dom.window.document.querySelectorAll('#studentTableBody b,#studentTableBody i').length,0);
    assert.match(dom.window.document.querySelector('#studentTableBody').textContent,/Ada <b>/);
    assert.match(dom.window.document.querySelector('#studentTableBody').textContent,/6a <i>/);
  } finally {
    dom.window.close();
  }
});

test('manage_students has no inline onclick delete route or visible delete action',async()=>{
  const combined=html + '\n' + script;
  assert.doesNotMatch(combined,/onclick=/i);
  assert.doesNotMatch(combined,/\/delete-student/);
  const {dom}=await setup();
  try {
    assert.doesNotMatch(dom.window.document.querySelector('#studentTableBody').textContent,/Löschen/);
  } finally {
    dom.window.close();
  }
});

test('grade results download remains wired',async()=>{
  const {dom,calls}=await setup();
  try {
    dom.window.document.getElementById('grade-input').value='6';
    dom.window.document.getElementById('subjectSelect').value='5';
    dom.window.document.getElementById('download-grade-results-form').dispatchEvent(new dom.window.Event('submit',{bubbles:true,cancelable:true}));
    assert.deepEqual(calls.downloads[0],{url:'/grade-results',data:'{"grade":6,"subjectId":5}',filename:'schueler_ergebnisse_6_5.csv'});
  } finally {
    dom.window.close();
  }
});
