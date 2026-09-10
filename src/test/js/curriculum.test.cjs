const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const{JSDOM}=require('jsdom');
const script=fs.readFileSync(path.join(__dirname,'../../main/resources/js/site/curriculum.js'),'utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,20));
async function setup(admin){
 const dom=new JSDOM('<section id="curriculum"></section>',{url:'https://school.example.invalid/',runScripts:'outside-only'});
 const requests=[];
 let centralTokens=70;
 let centralName='Central';
 let topicName='Topic';
 const byClass=new Map([[10,[{id:100,name:'Own task',tokens:30}]],[11,[]]]);
 const catalog={admin,teacherId:7,subjects:[{id:1,name:'Math'}],classes:[{id:10,label:'5a',grade:5},{id:11,label:'5b',grade:5}],semesters:[{id:20,label:'H1'},{id:21,label:'H2'}],teachers:[{id:7,first_name:'Test',last_name:'Teacher'}]};
 dom.window.fetch=async(url,options)=>{
  const data=JSON.parse(options.body);requests.push({url,data});let body;let ok=true;
  const tasks=byClass.get(data.classId)||[];
  if(url==='/curriculum-catalog')body=catalog;
  else if(url==='/curriculum-structure')body={centralTokens,topics:[{id:2,name:topicName,number:1}],tasks:[{id:3,topic:2,name:centralName,tokens:centralTokens,niveau:1}]};
  else if(url==='/curriculum-budget'){const f=tasks.reduce((n,t)=>n+t.tokens,0);body={grade:5,centralTokens,flexibleTokens:f,totalTokens:centralTokens+f,remainingRegular:100-centralTokens-f,remainingHard:105-centralTokens-f};}
  else if(url==='/flexible-tasks')body=tasks;
  else if(url==='/rename-topic'){topicName=data.name;body={ok:true};}
  else if(url==='/edit-task'){
   if(data.tokens>70){ok=false;body={message:'Budget exceeded',affectedContexts:[{teacherId:7,classId:10,semesterId:20,centralTokens:data.tokens,flexibleTokens:35,totalTokens:data.tokens+35}]};}
   else{centralName=data.name;centralTokens=data.tokens;body={ok:true};}
  }
  else if(url==='/add-flexible-task'){tasks.push({id:101,name:data.name,tokens:data.tokens});byClass.set(data.classId,tasks);body=tasks.at(-1);}
  else if(url==='/edit-flexible-task'){const task=[...byClass.values()].flat().find(t=>t.id===data.taskId);Object.assign(task,{name:data.name,tokens:data.tokens});body=task;}
  else throw Error('Unexpected endpoint '+url);
  return {ok,json:async()=>body};
 };
 dom.window.eval(script);dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));await tick();
 return {dom,requests,root:dom.window.document.querySelector('#curriculum')};
}
function formWith(root,button){return [...root.querySelectorAll('form')].find(f=>f.querySelector('button')?.textContent===button);}
async function submit(dom,form){form.dispatchEvent(new dom.window.Event('submit',{bubbles:true,cancelable:true}));await tick();}
test('admin can rename topics and tasks safely and sees server budget conflicts',async()=>{
 const{dom,requests,root}=await setup(true);
 try{
  let form=formWith(root,'Thema umbenennen');form.querySelector('input').value='<img src=x onerror=alert(1)>';await submit(dom,form);
  assert.equal(requests.find(r=>r.url==='/rename-topic').data.topicId,2);assert.equal(root.querySelectorAll('img').length,0);
  form=root.querySelector('details form:nth-of-type(2)');const inputs=form.querySelectorAll('input');inputs[0].value='Revised';inputs[1].value=71;await submit(dom,form);
  assert.match(root.textContent,/Budget exceeded/);assert.match(root.textContent,/106/);
 }finally{dom.window.close();}
});
test('teacher gets own context, can edit and create, and UI blocks totals above 105',async()=>{
 const{dom,requests,root}=await setup(false);
 try{
  assert.equal(formWith(root,'Thema umbenennen'),undefined);
  let form=formWith(root,'Speichern');form.querySelector('input[type=number]').value=35;await submit(dom,form);
  assert.match(root.textContent,/Rest regulär: -5/);
  form=formWith(root,'Flexible Etappe anlegen');form.querySelector('input[type=text]').value='Too much';form.querySelector('input[type=number]').value=1;await submit(dom,form);
  assert.equal(requests.filter(r=>r.url==='/add-flexible-task').length,0);
  const select=root.querySelector('[name=classId]');select.value=11;select.dispatchEvent(new dom.window.Event('change'));await tick();
  form=formWith(root,'Flexible Etappe anlegen');form.querySelector('input[type=text]').value='Own 5b';form.querySelector('input[type=number]').value=30;await submit(dom,form);
  const create=requests.find(r=>r.url==='/add-flexible-task');assert.equal(create.data.teacherId,7);assert.equal(create.data.classId,11);assert.equal(create.data.semesterId,20);
 }finally{dom.window.close();}
});
