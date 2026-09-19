const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const{JSDOM}=require('jsdom');
const script=fs.readFileSync(path.join(__dirname,'../../main/resources/js/site/curriculum.js'),'utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,20));
async function setup(admin, options={}){
 const markup=options.adminDashboard
  ? '<section id="schuljahr"><div id="admin-enrollment"></div></section><section id="curriculum-admin"><div id="admin-central-curriculum"></div></section>'
  : '<section id="curriculum"></section>';
 const dom=new JSDOM(markup,{url:'https://school.example.invalid/',runScripts:'outside-only'});
 await new Promise(resolve=>dom.window.document.addEventListener('DOMContentLoaded',resolve,{once:true}));
 if(options.pm) {
  dom.window.hasPermission=async name=>{if(options.checkFails)throw Error('internal failure');return options.pm[name]===true;};
  if(options.bootstrap)options.bootstrap(dom.window);
 }
 const requests=[];
 let centralTokens=70;
 let centralName='Central';
 let topicName='Topic';
 const byClass=new Map([[10,[{id:100,name:'Own task',tokens:30}]],[11,[]]]);
 const topicsByClass=new Map([[10,[]],[11,[]]]);
 const defaultContexts=[
  {semesterId:20,semesterLabel:'H1',activeSemester:true,classId:10,classLabel:'5a',grade:5,subjectId:1,subjectName:'Math'},
  {semesterId:20,semesterLabel:'H1',activeSemester:true,classId:11,classLabel:'5b',grade:6,subjectId:1,subjectName:'Math'},
  {semesterId:20,semesterLabel:'H1',activeSemester:true,classId:11,classLabel:'5b',grade:6,subjectId:3,subjectName:'Science'},
  {semesterId:21,semesterLabel:'H2',activeSemester:false,classId:13,classLabel:'7a',grade:7,subjectId:4,subjectName:'History'}
 ];
 const catalog={admin,enrollmentEnabled:options.enrollment===true,teacherId:7,
  subjects:admin?[{id:1,name:'Math'}]:[{id:1,name:'Math'},{id:2,name:'Legacy only'}],
  classes:admin?[{id:10,label:'5a',grade:5},{id:11,label:'5b',grade:5}]:[{id:10,label:'5a',grade:5},{id:11,label:'5b',grade:5},{id:12,label:'Legacy class',grade:8}],
  semesters:[{id:20,label:'H1'},{id:21,label:'H2'}],teachers:[{id:7,first_name:'Test',last_name:'Teacher'}],contexts:options.contexts ?? defaultContexts};
 dom.window.fetch=async(url,requestOptions)=>{
  const data=JSON.parse(requestOptions.body);requests.push({url,data});let body;let ok=true;
  if(options.response?.url===url)return {ok:false,status:options.response.status,text:async()=>options.response.body};
  const tasks=byClass.get(data.classId)||[];
  if(options.enrollment && url==='/curriculum-enrollment-catalog')body={...catalog,subjects:[{id:1,name:'Math',wpf:0,mode:'REGULAR',assignmentGroup:null},{id:4,name:'WPF Kunst',wpf:1,mode:'INDIVIDUAL',assignmentGroup:'WPF'},{id:5,name:'WPF Technik',wpf:1,mode:'INDIVIDUAL',assignmentGroup:'WPF'},{id:6,name:'Evangelische Religion',wpf:0,mode:'INDIVIDUAL',assignmentGroup:'RELIGION_ETHIK'},{id:7,name:'Ethik',wpf:0,mode:'INDIVIDUAL',assignmentGroup:'RELIGION_ETHIK'}],teaching:[{classId:10,subjectId:1,teacherId:7},{classId:11,subjectId:1,teacherId:7},{grade:5,subjectId:4,teacherId:7},{grade:5,subjectId:5,teacherId:7},{grade:5,subjectId:6,teacherId:7},{grade:5,subjectId:7,teacherId:7}]};
  else if(options.enrollment && url==='/assign-grade-curriculum')body={students:2,assignments:2};
  else if(options.enrollment && url==='/curriculum-wpf-roster')body=[{id:50,first_name:'Test',last_name:'Kind',subjectId:null,teacherId:null}];
  else if(options.enrollment && url==='/assign-curriculum-wpf')body={ok:true};
  else if(options.enrollment && url==='/curriculum-releases')body={topics:[{id:2,name:'Topic',active:false}],tasks:[{id:3,topicId:2,name:'Task',active:false}]};
  else if(options.enrollment && url==='/set-curriculum-release')body={ok:true};
  else if(url==='/curriculum-catalog')body=catalog;
  else if(url==='/curriculum-structure')body={centralTokens,topics:[{id:2,name:topicName,number:1}],tasks:[{id:3,topic:2,name:centralName,tokens:centralTokens,niveau:1,stageNumber:1}]};
  else if(url==='/central-curriculum-overview')body={subjects:[{subjectId:1,subjectName:'Math',centralTokens,remainingRegular:100-centralTokens,remainingHard:105-centralTokens,warning:centralTokens>100}],rows:[{subjectId:1,subjectName:'Math',topicNumber:1,topicName,stageNumber:1,stageName:centralName,tokens:centralTokens}]};
  else if(url==='/preview-central-curriculum-import'){
   const bad=data.csv.includes('BAD'), warn=data.csv.includes('104');
   body={rows:[{sourceLine:2,subjectId:1,subjectName:'Math',topicNumber:1,topicName:'<img src=x>',stageNumber:1,stageName:bad?'BAD':'Imported',tokens:warn?104:5,action:bad?'ERROR':'CREATE',message:bad?'Zeile 2: Fehler':'Neue Etappe.'}],creates:bad?0:1,updates:0,unchanged:0,errors:bad?['Zeile 2: Fehler']:[],warnings:warn?[{subjectId:1,subjectName:'Math',centralTokens:104,remainingRegular:-4,remainingHard:1,warning:true}]:[],subjects:[{subjectId:1,subjectName:'Math',centralTokens:warn?104:5,remainingRegular:warn?-4:95,remainingHard:warn?1:100,warning:warn}],canImport:!bad};
  }
  else if(url==='/import-central-curriculum'){centralName='Imported';centralTokens=5;body={createdTopics:1,updatedTopics:0,createdStages:1,updatedStages:0,unchangedStages:0,subjects:[{subjectId:1,subjectName:'Math',centralTokens:5,remainingRegular:95,remainingHard:100,warning:false}]};}
  else if(url==='/curriculum-budget'){const f=tasks.reduce((n,t)=>n+t.tokens,0);body={grade:data.classId===11?6:5,centralTokens,flexibleTokens:f,totalTokens:centralTokens+f,remainingRegular:100-centralTokens-f,remainingHard:105-centralTokens-f};}
  else if(url==='/flexible-tasks')body=tasks;
  else if(url==='/flexible-curriculum-structure')body={topics:topicsByClass.get(data.classId)||[],tasks};
  else if(url==='/add-flexible-topic'){body={id:500,name:data.name};topicsByClass.get(data.classId).push(body);}
  else if(url==='/rename-flexible-topic'){const topic=[...topicsByClass.values()].flat().find(t=>t.id===data.topicId);topic.name=data.name;body={ok:true};}
  else if(url==='/curriculum-students')body=[{id:50,first_name:'Sample',last_name:'Student',teacherId:8,classId:10}];
  else if(url==='/assign-curriculum-context' || url==='/transfer-curriculum-context')body={ok:true};
  else if(url==='/curriculum-transfer-preview')body={source:{teacherId:8,classId:10},completions:[{id:200,name:'Completed elsewhere',tokens:30}],targets:[{id:100,name:'Own task',tokens:30}]};
  else if(url==='/rename-topic'){topicName=data.name;body={ok:true};}
  else if(url==='/edit-task'){
   if(data.tokens>70){ok=false;body={error:'budget_exceeded',message:'Budget exceeded',affectedContexts:[{teacherId:7,classId:10,semesterId:20,centralTokens:data.tokens,flexibleTokens:35,totalTokens:data.tokens+35}]};}
   else{centralName=data.name;centralTokens=data.tokens;body={ok:true};}
  }
  else if(url==='/add-flexible-task'){tasks.push({id:101,name:data.name,tokens:data.tokens,topicId:data.topicId});byClass.set(data.classId,tasks);body=tasks.at(-1);}
  else if(url==='/add-curriculum-task'){centralName=data.name;centralTokens+=data.tokens;body={id:301};}
  else if(url==='/edit-flexible-task'){const task=[...byClass.values()].flat().find(t=>t.id===data.taskId);Object.assign(task,{name:data.name,tokens:data.tokens,topicId:data.topicId});body=task;}
  else throw Error('Unexpected endpoint '+url);
  return {ok,status:ok?200:409,text:async()=>JSON.stringify(body)};
 };
 dom.window.eval(script);dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));await tick();
 return {dom,requests,root:dom.window.document.querySelector(options.adminDashboard?'#admin-central-curriculum':'#curriculum')};
}
function formWith(root,button){return [...root.querySelectorAll('form')].find(f=>f.querySelector('button')?.textContent===button);}
async function submit(dom,form){form.dispatchEvent(new dom.window.Event('submit',{bubbles:true,cancelable:true}));await tick();}
const optionsOf=select=>[...select.options].map(option=>({value:option.value,text:option.textContent}));
test('admin can rename topics and tasks safely and sees server budget conflicts',async()=>{
 const{dom,requests,root}=await setup(true);
 try{
  let form=formWith(root,'Thema umbenennen');form.querySelector('input').value='<img src=x onerror=alert(1)>';await submit(dom,form);
  assert.equal(requests.find(r=>r.url==='/rename-topic').data.topicId,2);assert.equal(root.querySelectorAll('img').length,0);
  form=root.querySelector('details form:nth-of-type(2)');const inputs=form.querySelectorAll('input');inputs[0].value='Revised';inputs[1].value=71;await submit(dom,form);
  assert.match(root.textContent,/105 Münzen/);assert.match(root.textContent,/106/);
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
test('teacher filters use only active canonical contexts',async()=>{
 const{dom,root}=await setup(false);
 try{
  const classes=optionsOf(root.querySelector('[name=classId]')).map(option=>option.text);
  assert.deepEqual(classes,['5a','5b']);
  assert.doesNotMatch(classes.join(' '),/Legacy class|7a/);
  const subjects=optionsOf(root.querySelector('[name=subjectId]')).map(option=>option.text);
  assert.deepEqual(subjects,['Math']);
  assert.doesNotMatch(subjects.join(' '),/Legacy only|History/);
  assert.equal(root.querySelector('[name=semesterId]'),null);
  assert.equal(root.querySelector('[name=teacherId]'),null);
  assert.equal(root.querySelector('[name=grade]'),null);
 }finally{dom.window.close();}
});
test('teacher class changes rebuild subjects and requests canonical context scope',async()=>{
 const{dom,root,requests}=await setup(false);
 try{
  const classSelect=root.querySelector('[name=classId]'),subjectSelect=root.querySelector('[name=subjectId]');
  classSelect.value='11';classSelect.dispatchEvent(new dom.window.Event('change'));await tick();
  assert.deepEqual(optionsOf(subjectSelect),[{value:'1',text:'Math'},{value:'3',text:'Science'}]);
  subjectSelect.value='3';subjectSelect.dispatchEvent(new dom.window.Event('change'));await tick();
  const structure=requests.filter(r=>r.url==='/curriculum-structure').at(-1);
  assert.deepEqual(structure.data,{subjectId:3,semesterId:20,classId:11,teacherId:7,grade:6});
  const budget=requests.filter(r=>r.url==='/curriculum-budget').at(-1);
  assert.deepEqual(budget.data,{subjectId:3,semesterId:20,classId:11,teacherId:7});
  const flexible=requests.filter(r=>r.url==='/flexible-curriculum-structure').at(-1);
  assert.deepEqual(flexible.data,{subjectId:3,semesterId:20,classId:11,teacherId:7});
  assert.match(root.textContent,/Zentrale Summe Jahrgang 6/);
 }finally{dom.window.close();}
});
test('teacher without active contexts sees message and sends no curriculum detail requests',async()=>{
 const contexts=[{semesterId:21,semesterLabel:'H2',activeSemester:false,classId:10,classLabel:'5a',grade:5,subjectId:1,subjectName:'Math'}];
 const{dom,root,requests}=await setup(false,{contexts});
 try{
  assert.match(root.textContent,/Für das aktive Halbjahr sind keine Lerngruppen oder Fächer zugewiesen/);
  assert.equal(requests.filter(r=>['/curriculum-structure','/curriculum-budget','/flexible-curriculum-structure'].includes(r.url)).length,0);
 }finally{dom.window.close();}
});
test('admin curriculum filters keep subject semester class teacher and grade selects',async()=>{
 const{dom,root}=await setup(true);
 try{
  assert.ok(root.querySelector('[name=subjectId]'));
  assert.ok(root.querySelector('[name=semesterId]'));
  assert.ok(root.querySelector('[name=classId]'));
  assert.ok(root.querySelector('[name=teacherId]'));
  assert.ok(root.querySelector('[name=grade]'));
 }finally{dom.window.close();}
});

const grants=(flexible=true,central=false)=>({curriculum_view:true,curriculum_manage_flexible:flexible,curriculum_manage_central:central});
for(const [label,admin,pm,flexible,central] of [
 ['core teacher',false,null,true,false],['core admin',true,null,true,true],
 ['PM flexible editor',false,grants(),true,false],
 ['PM flexible reader',false,grants(false),false,false],
 ['PM central editor',true,grants(true,true),true,true],
 ['PM central reader',true,grants(false,false),false,false],
 ['teacher with accidental central grant',false,grants(false,true),false,false],
 ['completion alone does not grant editing',false,{...grants(false),curriculum_complete_flexible:true},false,false]
])test(label,async()=>{
 const {dom,root}=await setup(admin,{pm});
 try{
  const sections=root.querySelectorAll('section');assert.ok(sections.length>=3);
  assert.match(sections[0].textContent+[...sections[0].querySelectorAll('input')].map(n=>n.value).join(' '),/Central/);assert.match(sections[1].textContent+[...sections[1].querySelectorAll('input')].map(n=>n.value).join(' '),/Own task/);
  assert.equal(!!formWith(root,'Flexible Etappe anlegen'),flexible);
  assert.equal(sections[1].querySelectorAll('input').length>0,flexible);
  assert.equal(sections[0].querySelectorAll('input').length>0,central);
  for(const label of ['Thema umbenennen','Etappe anlegen','Thema anlegen'])assert.equal(!!formWith(root,label),central);
  if(!flexible){assert.match(sections[1].textContent,/Own task: 30 Münzen/);assert.match(sections[1].textContent,/Nur lesbar/);assert.equal(sections[1].querySelectorAll('form,button').length,0);}
 }finally{dom.window.close();}
});
for(const [label,options] of [
 ['view denied',{pm:{...grants(),curriculum_view:false}}],
 ['permission check rejects',{pm:grants(),checkFails:true}],
 ['bootstrap rejects',{pm:grants(),bootstrap:w=>{w.permissionsLoaded=Promise.reject(Error('private detail'));}}],
 ['loader rejects without bootstrap promise',{pm:grants(),bootstrap:w=>{w.loadCurrentPermissions=async()=>{throw Error('private detail');};}}],
 ['PM absorbs loading failure as empty permissions',{pm:{}}]
])test(label+' blocks all curriculum requests',async()=>{
 const{dom,root,requests}=await setup(false,options);
 try{assert.equal(requests.length,0);assert.equal(root.querySelectorAll('form,input').length,0);assert.match(root.textContent,/nicht verfügbar/);}
 finally{dom.window.close();}
});
test('waits for existing PM bootstrap without another load',async()=>{
 let release,loads=0;
 const pending=setup(false,{pm:grants(),bootstrap:w=>{
  w.permissionsLoaded=new Promise(resolve=>{release=resolve;});
  w.loadCurrentPermissions=async()=>{loads++;};
 }});
 const{dom,requests,root}=await pending;
 try{assert.equal(requests.length,0);release();await tick();assert.ok(formWith(root,'Flexible Etappe anlegen'));assert.equal(loads,0);assert.equal(requests.filter(r=>r.url==='/curriculum-catalog').length,1);}
 finally{dom.window.close();}
});
test('loads once when only the loader is available',async()=>{
 let loads=0;
 const{dom,root}=await setup(false,{pm:grants(),bootstrap:w=>{w.loadCurrentPermissions=async()=>{loads++;};}});
 try{assert.ok(formWith(root,'Flexible Etappe anlegen'));assert.equal(loads,1);}finally{dom.window.close();}
});
for(const status of [401,403])for(const [kind,body] of [
 ['JSON',JSON.stringify({message:'SQL private stacktrace',error:'forbidden'})],
 ['text','SQL private stacktrace'],['HTML','<html>SQL private stacktrace</html>'],['empty','']
])test(`${kind} ${status} is presented safely`,async()=>{
 const{dom,root}=await setup(false,{response:{url:'/edit-flexible-task',status,body}});
 try{await submit(dom,formWith(root,'Speichern'));assert.match(root.textContent,status===401?/Bitte erneut anmelden/:/Für diese Aktion fehlt die Berechtigung/);assert.doesNotMatch(root.textContent,/SQL|stacktrace|JSON|<html>/);}
 finally{dom.window.close();}
});
for(const [path,admin,label,section] of [
 ['/add-flexible-task',false,'Flexible Etappe anlegen',1],['/edit-flexible-task',false,'Speichern',1],
 ['/rename-topic',true,'Thema umbenennen',0],['/edit-task',true,'Speichern',0],
 ['/add-curriculum-task',true,'Etappe anlegen',0],['/add-curriculum-topic',true,'Thema anlegen',0]
])test(`revoked permission blocks stale ${path} form`,async()=>{
 const pm=grants(true,true),{dom,root,requests}=await setup(admin,{pm});
 try{
  const form=formWith(root.querySelectorAll('section')[section],label);
  assert.ok(form);pm[section===1?'curriculum_manage_flexible':'curriculum_manage_central']=false;
  await submit(dom,form);assert.equal(requests.filter(r=>r.url===path).length,0);
  assert.equal(root.querySelectorAll('section')[section].querySelectorAll('form').length,0);
 }finally{dom.window.close();}
});
test('view revoked after render blocks mutation and subsequent reads',async()=>{
 const pm=grants(),{dom,root,requests}=await setup(false,{pm});
 try{const count=requests.length;pm.curriculum_view=false;await submit(dom,formWith(root,'Speichern'));assert.equal(requests.length,count);assert.equal(root.querySelectorAll('form').length,0);}
 finally{dom.window.close();}
});
for(const status of [409,500])test(`untrusted ${status} details are never displayed`,async()=>{
 const{dom,root}=await setup(false,{response:{url:'/edit-flexible-task',status,body:JSON.stringify({error:'database_error',message:'SQL private stacktrace',affectedContexts:[null,{teacherId:'<html>'}]})}});
 try{await submit(dom,formWith(root,'Speichern'));assert.match(root.textContent,/Anfrage fehlgeschlagen/);assert.doesNotMatch(root.textContent,/SQL|stacktrace|<html>/);}finally{dom.window.close();}
});

test('permission check failure after render fails closed',async()=>{
 const{dom,root,requests}=await setup(false,{pm:grants()});
 try{const count=requests.length;dom.window.hasPermission=async()=>{throw Error('SQL private');};await submit(dom,formWith(root,'Speichern'));assert.equal(requests.length,count);assert.doesNotMatch(root.textContent,/SQL/);}finally{dom.window.close();}
});
test('known 409 conflict retains actionable semester information',async()=>{
 const{dom,root}=await setup(false,{response:{url:'/edit-flexible-task',status:409,body:JSON.stringify({error:'conflict',message:'Class grade changed; existing semester context is historical.'})}});
 try{await submit(dom,formWith(root,'Speichern'));assert.match(root.textContent,/neues Halbjahr/);}finally{dom.window.close();}
});

for(const [label,admin,pm,visible] of [
 ['core admin assignment',true,null,true],['core teacher no assignment',false,null,false],
 ['PM central does not imply assignment',true,grants(true,true),false],
 ['PM assignment grant',true,{...grants(),curriculum_assign_context:true},true],
 ['PM assignment grant cannot elevate teacher',false,{...grants(),curriculum_assign_context:true},false]
])test(label,async()=>{
 const{dom,root,requests}=await setup(admin,{pm});
 try{assert.equal(!!formWith(root,'Unterrichtskontext zuweisen'),visible);assert.equal(requests.some(r=>r.url==='/curriculum-students'),visible);}
 finally{dom.window.close();}
});
test('admin explicitly previews and confirms one-to-one completion transfer',async()=>{
 const{dom,root,requests}=await setup(true);
 try{
  [...root.querySelectorAll('button')].find(b=>b.textContent==='Wechsel mit Leistungsübernahme vorbereiten').click();await tick();
  const form=formWith(root,'Wechsel und Leistungsübernahme bestätigen');assert.ok(form);
  await submit(dom,form);assert.equal(requests.some(r=>r.url==='/transfer-curriculum-context'),false);
  form.querySelector('select').value=100;await submit(dom,form);
  const sent=requests.find(r=>r.url==='/transfer-curriculum-context');assert.deepEqual(sent.data.transfers,[{sourceTaskId:200,targetTaskId:100,tokens:30}]);assert.equal(sent.data.studentId,50);assert.equal(sent.data.sourceTeacherId,8);
 }finally{dom.window.close();}
});
for(const transfer of [false,true])test(`revoked assignment permission blocks ${transfer?'transfer':'assignment'}`,async()=>{
 const pm={...grants(),curriculum_assign_context:true},{dom,root,requests}=await setup(true,{pm});
 try{
  let form=formWith(root,'Unterrichtskontext zuweisen');
  if(transfer){[...root.querySelectorAll('button')].find(b=>b.textContent==='Wechsel mit Leistungsübernahme vorbereiten').click();await tick();form=formWith(root,'Wechsel und Leistungsübernahme bestätigen');form.querySelector('select').value=100;}
  pm.curriculum_assign_context=false;await submit(dom,form);assert.equal(requests.some(r=>r.url==='/assign-curriculum-context'||r.url==='/transfer-curriculum-context'),false);
 }finally{dom.window.close();}
});

test('teacher creates a flexible topic, assigns an open task and can detach it',async()=>{
 const {dom,root,requests}=await setup(false);
 try {
  let form=formWith(root,'Flexibles Thema anlegen');form.querySelector('input').value='<b>Practice</b>';await submit(dom,form);
  assert.equal(root.querySelectorAll('b').length,0);
  form=formWith(root,'Flexible Etappe anlegen');form.querySelector('input[type=text]').value='Practice step';form.querySelector('input[type=number]').value=5;
  form.querySelector('[name=topicId]').value='500';await submit(dom,form);
  const created=requests.find(r=>r.url==='/add-flexible-task');assert.equal(created.data.topicId,500);assert.equal(created.data.tokens,5);
  assert.match(root.textContent,/erst durch bestätigte Abschlüsse/);
  assert.equal(requests.filter(r=>r.url==='/complete-flexible-task').length,0);
  let group=[...root.querySelectorAll('details')].find(d=>d.querySelector('summary').textContent==='<b>Practice</b>');
  form=formWith(group,'Speichern');assert.equal(form.querySelector('[name=topicId]').value,'500');
  assert.doesNotMatch(form.textContent,/gelten auch für bereits abgeschlossene Etappen/);
  form.querySelector('[name=topicId]').value='';await submit(dom,form);
  assert.equal(requests.find(r=>r.url==='/edit-flexible-task').data.topicId,null);
  group=[...root.querySelectorAll('details')].find(d=>d.querySelector('summary').textContent==='Noch keinem Thema zugeordnet');
  assert.ok([...group.querySelectorAll('input')].some(i=>i.value==='Practice step'));
 } finally {dom.window.close();}
});
test('revoked flexible permission blocks topic creation and rename',async()=>{
 const pm=grants(),{dom,root,requests}=await setup(false,{pm});
 try {
  let form=formWith(root,'Flexibles Thema anlegen');form.querySelector('input').value='Practice';await submit(dom,form);
  form=formWith(root,'Flexibles Thema umbenennen');pm.curriculum_manage_flexible=false;await submit(dom,form);
  assert.equal(requests.filter(r=>r.url==='/rename-flexible-topic').length,0);
  assert.equal(formWith(root,'Flexibles Thema anlegen'),undefined);
 } finally {dom.window.close();}
});
test('context change invalidates an old topic form',async()=>{
 const {dom,root,requests}=await setup(false);
 try {
  const stale=formWith(root,'Flexibles Thema anlegen');stale.querySelector('input').value='Old context';
  const choice=root.querySelector('[name=classId]');choice.value='11';choice.dispatchEvent(new dom.window.Event('change'));await tick();
  await submit(dom,stale);assert.equal(requests.filter(r=>r.url==='/add-flexible-topic').length,0);
  assert.match(root.textContent,/Auswahl wurde geändert/);
 } finally {dom.window.close();}
});

async function clickNamed(dom,root,name){const b=[...root.querySelectorAll('button')].find(b=>b.textContent===name);assert(b,name);b.click();await tick();}
test('admin grade batch excludes individual subjects and submits every regular class teacher assignment',async()=>{
 const {dom,root,requests}=await setup(true,{enrollment:true});try{
  const panel=root.querySelector('.curriculum-enrollment');await clickNamed(dom,panel,'Jetzt verwalten');await clickNamed(dom,panel,'Auswahl bestätigen');
  const rows=[...panel.querySelectorAll('.semester-create-panel .curriculum-wpf-table tr')].slice(1);assert.equal(rows.length,2);
  assert.deepEqual(rows.map(r=>r.cells[1].textContent),['Math','Math']);
  await clickNamed(dom,panel,'Dem gesamten Jahrgang zuordnen');
  const request=requests.find(r=>r.url==='/assign-grade-curriculum');assert.deepEqual(request.data.subjectIds,[1]);assert.equal(request.data.teaching.length,2);assert.equal(request.data.semesterId,20);
  assert.match(panel.textContent,/2 Kinder, 2 Fachzuordnungen/);
 }finally{dom.window.close();}
});
test('individual subject groups save independently through the guarded assignment endpoint',async()=>{
 const {dom,root,requests}=await setup(true,{enrollment:true});try{
  const panel=root.querySelector('.curriculum-enrollment');await clickNamed(dom,panel,'Jetzt verwalten');await clickNamed(dom,panel,'Auswahl bestätigen');
  const group=[...panel.querySelectorAll('select')].find(select=>[...select.options].some(option=>option.value==='RELIGION_ETHIK'));assert.ok(group);assert.equal(group.value,'RELIGION_ETHIK');
  await clickNamed(dom,panel,'Zuordnungen laden');
  let row=panel.querySelector('.curriculum-wpf-board table tr:nth-child(2)');let selects=row.querySelectorAll('select');selects[0].value='6';selects[1].value='7';await clickNamed(dom,panel,'Zuordnungen speichern');
  let request=requests.filter(r=>r.url==='/assign-curriculum-wpf').at(-1);assert.equal(request.data.studentId,50);assert.equal(request.data.subjectId,6);assert.equal(request.data.assignmentGroup,'RELIGION_ETHIK');assert.equal(request.data.expectedSubjectId,null);
  group.value='WPF';group.dispatchEvent(new dom.window.Event('change'));await clickNamed(dom,panel,'Zuordnungen laden');
  row=panel.querySelector('.curriculum-wpf-board table tr:nth-child(2)');selects=row.querySelectorAll('select');selects[0].value='5';selects[1].value='7';await clickNamed(dom,panel,'Zuordnungen speichern');
  request=requests.filter(r=>r.url==='/assign-curriculum-wpf').at(-1);assert.equal(request.data.subjectId,5);assert.equal(request.data.classId,10);assert.equal(request.data.assignmentGroup,'WPF');
 }finally{dom.window.close();}
});
test('teacher publishes a whole topic or individual task but has no enrollment UI',async()=>{
 const {dom,root,requests}=await setup(false,{enrollment:true});try{
  assert.doesNotMatch(root.querySelector('.curriculum-enrollment').textContent,/WPF-Zuteilung/);
  await clickNamed(dom,root,'Freischaltungen laden');await clickNamed(dom,root,'Ganzes Thema freischalten');await clickNamed(dom,root,'Etappe freischalten');
  const writes=requests.filter(r=>r.url==='/set-curriculum-release');assert.equal(writes[0].data.topicId,2);assert.equal(writes[1].data.taskId,3);assert.equal(writes[0].data.teacherId,7);
 }finally{dom.window.close();}
});
test('old publication controls cannot write after scope change or permission revocation',async()=>{
 const pm={...grants(),curriculum_publish:true};const {dom,root,requests}=await setup(false,{enrollment:true,pm});try{
  await clickNamed(dom,root,'Freischaltungen laden');const button=[...root.querySelectorAll('button')].find(b=>b.textContent==='Etappe freischalten');root.querySelector('[name=classId]').value='11';button.click();await tick();assert.equal(requests.filter(r=>r.url==='/set-curriculum-release').length,0);
  root.querySelector('[name=classId]').value='10';pm.curriculum_publish=false;button.click();await tick();assert.equal(requests.filter(r=>r.url==='/set-curriculum-release').length,0);
 }finally{dom.window.close();}
});

test('admin dashboard separates enrollment from central curriculum mounts',async()=>{
 const {dom,root,requests}=await setup(true,{enrollment:true,adminDashboard:true});try{
  const enrollment=dom.window.document.querySelector('#admin-enrollment');
  const central=dom.window.document.querySelector('#admin-central-curriculum');
  assert.ok(enrollment);assert.ok(central);assert.notEqual(enrollment,central);assert.equal(dom.window.document.querySelectorAll('#curriculum').length,0);
  assert.ok(enrollment.querySelector('.curriculum-enrollment'));
  assert.ok(central.querySelector('.central-curriculum-import'));
  assert.match(enrollment.textContent,/Neues Schulhalbjahr anlegen/);
  assert.match(enrollment.textContent,/Als aktives Halbjahr setzen/);
  await clickNamed(dom,enrollment,'Jetzt verwalten');await clickNamed(dom,enrollment,'Auswahl bestätigen');
  assert.match(enrollment.textContent,/Lehrkräfte-Tabelle/);
  assert.match(enrollment.textContent,/Individuelle Fächer/);
  assert.doesNotMatch(enrollment.textContent,/CSV|Vorschau prüfen|Thema anlegen|Etappe anlegen/);
  assert.match(central.textContent,/Zentrale Themen und Etappen/);
  assert.match(central.textContent,/Thema umbenennen/);
  assert.match(central.textContent,/Etappe anlegen/);
  assert.match(central.textContent,/Vorschau prüfen/);
  assert.doesNotMatch(central.textContent,/Individuelle Fächer|Unterrichtskontext|Flexible Lehrer-Etappen|Flexible Etappe/);
  assert.equal(requests.some(r=>r.url==='/flexible-curriculum-structure'),false);
 }finally{dom.window.close();}
});

function attachCsv(input,text){Object.defineProperty(input,'files',{configurable:true,value:[{text:async()=>text}]});}
test('central csv import reads file, renders safe preview and confirms import',async()=>{
 const {dom,root,requests}=await setup(true);try{
  dom.window.confirm=()=>true;
  const panel=root.querySelector('.central-curriculum-import');assert.ok(panel);
  attachCsv(panel.querySelector('input[type=file]'),'Fach;Themennummer;Themenname;Etappennummer;Etappenname;Muenzen\nMath;1;<img src=x>;1;Imported;5\n');
  await clickNamed(dom,panel,'Vorschau prüfen');
  assert.equal(requests.some(r=>r.url==='/preview-central-curriculum-import'&&r.data.csv.includes('Imported')),true);
  assert.equal(panel.querySelectorAll('img').length,0);
  assert.match(panel.textContent,/CREATE/);
  const importButton=[...panel.querySelectorAll('button')].find(b=>b.textContent==='Import bestätigen');assert.equal(importButton.disabled,false);
  importButton.click();await tick();
  assert.equal(requests.some(r=>r.url==='/import-central-curriculum'),true);
  assert.equal(requests.filter(r=>r.url==='/central-curriculum-overview').length>=2,true);
  assert.match(root.textContent,/Import erfolgreich/);
 }finally{dom.window.close();}
});
test('central csv preview disables import on errors and shows warning range',async()=>{
 const {dom,root,requests}=await setup(true);try{
  const panel=root.querySelector('.central-curriculum-import'), importButton=[...panel.querySelectorAll('button')].find(b=>b.textContent==='Import bestätigen');
  attachCsv(panel.querySelector('input[type=file]'),'BAD');
  await clickNamed(dom,panel,'Vorschau prüfen');
  assert.equal(importButton.disabled,true);assert.match(panel.textContent,/Zeile 2: Fehler/);
  attachCsv(panel.querySelector('input[type=file]'),'104');
  await clickNamed(dom,panel,'Vorschau prüfen');
  assert.equal(importButton.disabled,false);assert.match(panel.textContent,/104/);assert.match(panel.textContent,/regulären Rahmen/);
  assert.equal(requests.filter(r=>r.url==='/import-central-curriculum').length,0);
 }finally{dom.window.close();}
});
test('central overview subject and text filters work without unsafe html',async()=>{
 const {dom,root}=await setup(true);try{
  const panel=root.querySelector('.central-curriculum-import');assert.match(panel.textContent,/Math: 70/);
  const text=panel.querySelector('input[placeholder="Themen-/Etappenname filtern"]');text.value='missing';text.dispatchEvent(new dom.window.Event('input'));await tick();
  assert.doesNotMatch(panel.querySelector('table')?.textContent||'',/Central/);
  text.value='central';text.dispatchEvent(new dom.window.Event('input'));await tick();
  assert.match(panel.querySelector('table').textContent,/Central/);
  assert.equal(panel.querySelectorAll('script,img').length,0);
 }finally{dom.window.close();}
});
