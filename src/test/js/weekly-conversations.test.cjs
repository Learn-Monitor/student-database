const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM}=require('jsdom');
const css=fs.readFileSync(path.join(__dirname,'../../main/resources/css/site/style.css'),'utf8');

const root=path.join(__dirname,'../../main/resources');
const script=fs.readFileSync(path.join(root,'js/teacher/weekly-conversations.js'),'utf8');

function dom(response) {
  const instance=new JSDOM('<main id="tutor-area"><h2>Tutorenbereich</h2><div id="weekly-conversations-mount"></div></main>',{url:'https://school.example.invalid/dashboard#tutor-area',runScripts:'outside-only'});
  instance.window.fetch=async path => ({ok:true,status:200,json:async()=>path==='/my-tutor-classes'?response.classes:response.overview});
  instance.window.eval(script);
  instance.window.document.dispatchEvent(new instance.window.Event('DOMContentLoaded'));
  return instance;
}

test('weekly-conversations mount belongs to the tutor area',()=>{
  const instance=new JSDOM('<main id="student-progress"></main><section id="tutor-area"><div id="weekly-conversations-mount"></div></section>');
  try {
    assert.equal(instance.window.document.querySelector('#tutor-area #weekly-conversations-mount') !== null,true);
    assert.equal(instance.window.document.querySelector('#student-progress #weekly-conversations-mount'),null);
  } finally { instance.window.close(); }
});

test('non-tutor receives no visible weekly-conversation control',async()=>{
  const instance=dom({classes:[],overview:{}});
  await new Promise(resolve=>setTimeout(resolve,20));
  assert.equal(instance.window.document.querySelector('.teacher-weekly-conversations'),null);
  instance.window.close();
});

test('tutor view offers only assigned classes and a printable compact table',async()=>{
  const instance=dom({
    classes:[{semesterId:4,classId:7,label:'7a',grade:7},{semesterId:4,classId:8,label:'8b',grade:8}],
    overview:{semesterLabel:'2026/27 HJ1',classLabel:'7a',subjects:[{id:3,name:'Mathe'}],students:[{name:'Test Schüler',subjects:[{subjectId:3,totalTokens:75,note:2,lastGnDate:'2026-10-20',stages:[{niveau:2}]}]}]}
  });
  await new Promise(resolve=>setTimeout(resolve,30));
  assert.equal(instance.window.document.querySelectorAll('.teacher-filter-bar option').length,2);
  assert.ok(instance.window.document.querySelector('.teacher-weekly-conversations').textContent.includes('Test Schüler'));
  assert.ok(instance.window.document.querySelector('.teacher-weekly-conversations').textContent.includes('letzter GN: 20.10.2026'));
  assert.equal(instance.window.document.querySelectorAll('.teacher-filter-bar button').length,3);
  assert.equal(instance.window.document.querySelectorAll('.teacher-weekly-conversations tbody tr:first-child button').length,3);
  assert.doesNotMatch(instance.window.document.querySelector('.teacher-weekly-conversations').textContent,/Note|Etappen:/);
  instance.window.close();
});

test('tutor view renders offered WPF subjects as separate compact columns',async()=>{
  const instance=dom({
    classes:[{semesterId:4,classId:7,label:'7a',grade:7}],
    overview:{semesterLabel:'2026/27 HJ1',classLabel:'7a',subjects:[
      {id:3,name:'Mathe'},
      {id:21,name:'WPF Technik',assignmentGroup:'WPF'},
      {id:22,name:'WPF Soziales',assignmentGroup:'WPF'}
    ],students:[{name:'Belegt',subjects:[{subjectId:21,assigned:true,totalTokens:0,note:6,stages:[]}]} ,
      {name:'Nicht belegt',subjects:[]} ]}
  });
  await new Promise(resolve=>setTimeout(resolve,30));
  const table=instance.window.document.querySelector('.teacher-weekly-table');
  assert.equal(table.querySelector('.teacher-weekly-groups'),null);
  assert.deepEqual([...table.querySelectorAll('.teacher-weekly-header th')].map(th=>th.textContent),['Schüler','Mathe','WPF Technik','WPF Soziales']);
  assert.match(table.querySelector('tbody tr').textContent,/0 Münzen/);
  assert.match(table.querySelector('tbody tr').textContent,/noch kein GN/);
  assert.match(table.querySelectorAll('tbody tr')[1].textContent,/—/);
  instance.window.close();
});

test('tutor view shows earned of released tokens like the subject teacher roster',async()=>{
  const instance=dom({
    classes:[{semesterId:4,classId:7,label:'7a',grade:7}],
    overview:{semesterLabel:'2026/27 HJ1',classLabel:'7a',subjects:[{id:3,name:'Mathe'},{id:4,name:'Deutsch'}],students:[{name:'Test Schüler',subjects:[
      {subjectId:3,assigned:true,totalTokens:12,lastGnDate:'2026-10-20',stages:[],progress:{earnedTokens:12,releasedTokens:30}},
      {subjectId:4,assigned:true,totalTokens:0,lastGnDate:null,stages:[],progress:{earnedTokens:0,releasedTokens:0}}
    ]}]}
  });
  await new Promise(resolve=>setTimeout(resolve,30));
  const cells=instance.window.document.querySelectorAll('.teacher-weekly-table tbody td');
  assert.equal(cells[1].querySelector('.teacher-roster-progress-tokens').textContent,'12 von 30 Münzen');
  const bar=cells[1].querySelector('progress');assert.equal(bar.max,30);assert.equal(bar.value,12);
  assert.match(cells[2].textContent,/Noch keine Etappe freigegeben/);
  assert.doesNotMatch(instance.window.document.querySelector('.teacher-weekly-table').textContent,/🪙/);
  instance.window.close();
});

test('tutor print CSS targets complete A4 landscape width without screen scrolling',()=>{
  assert.match(css,/@page\s*\{\s*size:\s*A4 landscape;/);
  assert.match(css,/\.teacher-weekly-conversations \.teacher-weekly-table \{ width:100%; min-width:0; table-layout:fixed;/);
  assert.match(css,/\.teacher-weekly-conversations \.teacher-weekly-table th:first-child,[\s\S]*?width:16%;/);
  assert.match(css,/\.teacher-table-scroll \{ overflow:visible; max-height:none;/);
  assert.match(css,/thead \{ display:table-header-group; \}/);
  assert.match(css,/\.teacher-weekly-conversations \.teacher-weekly-scroll,[\s\S]*?height:auto !important; max-height:none !important; overflow:visible !important;/);
});

test('print state keeps all 97 already-loaded students in the print DOM',async()=>{
  const students=Array.from({length:97},(_,index)=>({name:`Student ${index+1}`,subjects:[]}));
  const instance=dom({classes:[{semesterId:4,classId:7,label:'7a',grade:7}],overview:{semesterLabel:'2026/27 HJ1',classLabel:'7a',subjects:[{id:3,name:'Mathe'},{id:21,name:'WPF Technik'}],students}});
  await new Promise(resolve=>setTimeout(resolve,30));
  const panel=instance.window.document.querySelector('.teacher-weekly-conversations');
  const table=panel.querySelector('.teacher-weekly-table');
  assert.equal(table.querySelectorAll('tbody tr').length,97);
  assert.match(table.querySelector('tbody tr:first-child').textContent,/Student 1/);
  assert.match(table.querySelectorAll('tbody tr')[48].textContent,/Student 49/);
  assert.match(table.querySelector('tbody tr:last-child').textContent,/Student 97/);
  assert.equal(new Set([...table.querySelectorAll('tbody tr')].map(row=>row.firstElementChild.textContent)).size,97);
  instance.window.dispatchEvent(new instance.window.Event('beforeprint'));
  assert.equal(panel.classList.contains('teacher-weekly-printing'),true);
  assert.equal(table.querySelectorAll('tbody tr').length,97);
  instance.window.dispatchEvent(new instance.window.Event('afterprint'));
  assert.equal(panel.classList.contains('teacher-weekly-printing'),false);
  instance.window.close();
});
