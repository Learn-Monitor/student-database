const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM}=require('jsdom');

const root=path.join(__dirname,'../../main/resources');
const script=fs.readFileSync(path.join(root,'js/teacher/weekly-conversations.js'),'utf8');

function dom(response) {
  const instance=new JSDOM('<main id="student-progress"><div id="weekly-conversations-mount"></div></main>',{url:'https://school.example.invalid/dashboard',runScripts:'outside-only'});
  instance.window.fetch=async path => ({ok:true,status:200,json:async()=>path==='/my-tutor-classes'?response.classes:response.overview});
  instance.window.eval(script);
  instance.window.document.dispatchEvent(new instance.window.Event('DOMContentLoaded'));
  return instance;
}

test('non-tutor receives no visible weekly-conversation control',async()=>{
  const instance=dom({classes:[],overview:{}});
  await new Promise(resolve=>setTimeout(resolve,20));
  assert.equal(instance.window.document.querySelector('.teacher-weekly-conversations'),null);
  instance.window.close();
});

test('tutor view offers only assigned classes and a printable compact table',async()=>{
  const instance=dom({
    classes:[{semesterId:4,classId:7,label:'7a',grade:7},{semesterId:4,classId:8,label:'8b',grade:8}],
    overview:{semesterLabel:'2026/27 HJ1',classLabel:'7a',subjects:[{id:3,name:'Mathe'}],students:[{name:'Test Schüler',subjects:[{subjectId:3,totalTokens:75,note:2,stages:[{niveau:2}]}]}]}
  });
  await new Promise(resolve=>setTimeout(resolve,30));
  assert.equal(instance.window.document.querySelectorAll('.teacher-weekly-conversations option').length,2);
  assert.ok(instance.window.document.querySelector('.teacher-weekly-conversations').textContent.includes('Test Schüler'));
  assert.ok(instance.window.document.querySelector('.teacher-weekly-conversations').textContent.includes('Note 2'));
  assert.equal(instance.window.document.querySelectorAll('.teacher-weekly-conversations button').length,2);
  instance.window.close();
});
