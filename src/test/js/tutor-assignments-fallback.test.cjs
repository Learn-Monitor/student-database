const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM}=require('jsdom');

const root=path.join(__dirname,'../../main/resources');
const dashboard=fs.readFileSync(path.join(root,'html/admin/dashboard.html'),'utf8');
const script=fs.readFileSync(path.join(root,'js/admin/tutor-assignments.js'),'utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,30));

test('raw admin dashboard visibly advertises tutor assignments before enrollment',()=>{
  assert.match(dashboard,/id="admin-tutors"[^>]*>[\s\S]*Tutor:innen je Klasse/);
  assert.match(dashboard,/Tutorzuordnungen werden geladen/);
  assert.ok(dashboard.indexOf('id="admin-tutors"')<dashboard.indexOf('id="admin-enrollment"'));
});

test('javascript keeps fallback markup and fills the content area',async()=>{
  const dom=new JSDOM(`<!doctype html><body>${dashboard}</body>`,{url:'https://example.invalid/dashboard#schuljahr',runScripts:'outside-only'});
  dom.window.fetch=async path=>path==='/curriculum-enrollment-catalog'
    ? {ok:true,json:async()=>({semesters:[{id:1,label:'HJ1'}],classes:[{id:1,label:'6a',grade:6,active:true}],teachers:[{id:2,first_name:'Tina',last_name:'Tutor'}]})}
    : {ok:true,json:async()=>[]};
  dom.window.eval(script); await tick();
  const block=dom.window.document.querySelector('#admin-tutors');
  assert.match(block.querySelector('h3').textContent,/Tutor:innen je Klasse/);
  assert.equal(block.querySelector('[data-tutor-status]').textContent,'');
  assert.equal(block.querySelectorAll('tbody tr').length,1);
  assert.equal(block.querySelectorAll('tbody select').length,2);
  dom.window.close();
});

test('javascript leaves the visible block and reports load errors',async()=>{
  const dom=new JSDOM(`<!doctype html><body>${dashboard}</body>`,{url:'https://example.invalid/dashboard#schuljahr',runScripts:'outside-only'});
  dom.window.fetch=async()=>({ok:false,status:403,json:async()=>({})});
  dom.window.console.error=()=>{};
  dom.window.eval(script); await tick();
  const block=dom.window.document.querySelector('#admin-tutors');
  assert.match(block.querySelector('h3').textContent,/Tutor:innen je Klasse/);
  assert.match(block.textContent,/Tutorzuordnungen konnten nicht geladen werden/);
  dom.window.close();
});
