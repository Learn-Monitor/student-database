const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM}=require('jsdom');

const root=path.join(__dirname,'../../main/resources');
const dashboard=fs.readFileSync(path.join(root,'html/admin/dashboard.html'),'utf8');
const script=fs.readFileSync(path.join(root,'js/admin/tutor-assignments.js'),'utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,30));

function domWithFetch(fetchImpl) {
  const dom=new JSDOM(`<!doctype html><body>${dashboard}</body>`,{url:'https://school.example.invalid/dashboard#schuljahr',runScripts:'outside-only'});
  dom.window.fetch=fetchImpl;
  dom.window.console.error=()=>{};
  dom.window.eval(script);
  return dom;
}

const catalog={
  semesters:[{id:11,label:'2026_27_HJ1'}],
  classes:[{id:0,label:'Klasse 0',grade:0,active:true},{id:1,label:'6a',grade:6,active:true},{id:2,label:'6b',grade:6,active:true}],
  teachers:[{id:21,first_name:'Ada',last_name:'Admin'},{id:22,first_name:'Tina',last_name:'Tutor'}]
};

test('tutor block precedes enrollment and is wired into the admin dashboard',()=>{
  assert.ok(dashboard.indexOf('id="admin-tutors"')<dashboard.indexOf('id="admin-enrollment"'));
  assert.match(dashboard,/src="\/tutor-assignments\.js"/);
});

test('renders normal classes, existing assignments, and sends tutor assignment payload',async()=>{
  const requests=[];
  const dom=domWithFetch(async(path,options={})=>{
    requests.push({path,options});
    if(path==='/curriculum-enrollment-catalog') return {ok:true,json:async()=>catalog};
    if(path==='/curriculum-tutor-assignments') return {ok:true,json:async()=>[{classId:1,tutorSlot:1,teacherId:21}]};
    if(path==='/assign-class-tutors') return {ok:true,json:async()=>({ok:true})};
    throw Error(path);
  });
  await tick();
  const block=dom.window.document.querySelector('#admin-tutors');
  assert.match(block.textContent,/Tutor:innen je Klasse/);
  assert.equal(block.querySelectorAll('tbody tr').length,2);
  assert.equal(block.textContent.includes('Klasse 0'),false);
  const selects=block.querySelectorAll('tbody tr:first-child select');
  assert.equal(selects.length,2);
  assert.equal(selects[0].value,'21');
  assert.equal(selects[1].value,'');
  block.querySelector('tbody tr:first-child button').click();
  await tick();
  const save=requests.find(request=>request.path==='/assign-class-tutors');
  assert.ok(save);
  assert.deepEqual(JSON.parse(save.options.body),{semesterId:11,classId:1,tutor1Id:21,tutor2Id:null});
  dom.window.close();
});

test('shows a visible error instead of silently leaving an empty block',async()=>{
  const dom=domWithFetch(async()=>({ok:false,status:500,json:async()=>({})}));
  await tick();
  const block=dom.window.document.querySelector('#admin-tutors');
  assert.match(block.textContent,/Tutorzuordnungen konnten nicht geladen werden/);
  dom.window.close();
});
