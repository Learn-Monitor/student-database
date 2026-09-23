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
  : (options.overview?'<section id="overview"></section>':'')+'<section id="curriculum"></section>'+(options.progress?'<section id="student-progress"></section>':'');
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
 let centralTopics=options.centralTopics||[{id:2,name:topicName,number:1}];
 let centralTasks=options.centralTasks||[{id:3,topic:2,name:centralName,tokens:centralTokens,niveau:1,stageNumber:1}];
 let releaseTopics=options.releaseTopics||[{id:2,name:'Topic',active:false}];
 let releaseTasks=options.releaseTasks||[{id:3,topicId:2,name:'Task',active:false}];
 const defaultFlexibleTasks=[{id:100,name:'Own task',tokens:30,topicId:null}];
 const defaultFlexibleTopics=[];
 const plannedFlexibleTasks=options.plannedFlexibleTasks||defaultFlexibleTasks;
 const plannedFlexibleTopics=options.plannedFlexibleTopics||defaultFlexibleTopics;
 let releaseFlexibleTopics=options.releaseFlexibleTopics||plannedFlexibleTopics.map(topic=>({id:topic.id,name:topic.name,active:false}));
 let releaseFlexibleTasks=options.releaseFlexibleTasks||plannedFlexibleTasks.map(task=>({id:task.id,name:task.name,topicId:task.topicId??null,active:false}));
 const byClass=new Map([[10,plannedFlexibleTasks],[11,[]]]);
 const topicsByClass=new Map([[10,plannedFlexibleTopics],[11,[]]]);
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
 const defaultRoster=[
  {id:50,name:'Ada Alpha',firstName:'Ada',lastName:'Alpha',activeStage:{type:'CENTRAL',taskId:3,name:'Central'},signals:{help:true,partner:true,experiment:false,exam:false}},
  {id:51,name:'Ben Beta',firstName:'Ben',lastName:'Beta',activeStage:{type:'FLEXIBLE',taskId:100,name:'Own task'},signals:{help:false,partner:false,experiment:true,exam:true}},
  {id:52,name:'Cara Gamma',firstName:'Cara',lastName:'Gamma',activeStage:null,signals:{help:false,partner:false,experiment:false,exam:false}}
 ];
 const defaultProgressDetail={studentId:50,studentName:'Ada Alpha',subjectId:1,semesterId:20,stages:[
  {type:'CENTRAL',stageId:3,topicId:2,topicName:'Topic',name:'Passed central',tokens:5,status:'PASSED',earned:true,inProgress:true},
  {type:'CENTRAL',stageId:4,topicId:2,topicName:'Topic',name:'Failed once',tokens:6,status:'FAILED_ONCE',earned:false,inProgress:false},
  {type:'CENTRAL',stageId:5,topicId:2,topicName:'Topic',name:'Failed twice',tokens:7,status:'FAILED_TWICE',earned:false,inProgress:false},
  {type:'FLEXIBLE',stageId:100,topicId:null,topicName:null,name:'Locked flexible',tokens:8,status:'LOCKED',earned:true,inProgress:false},
  {type:'FLEXIBLE',stageId:101,topicId:null,topicName:null,name:'Unassessed flexible',tokens:9,status:null,earned:false,inProgress:false}
 ]};
 let progressDetail=options.detail ?? defaultProgressDetail;
 dom.window.fetch=async(url,requestOptions)=>{
  const data=JSON.parse(requestOptions.body);requests.push({url,data});let body;let ok=true;
  if(options.response?.url===url)return {ok:false,status:options.response.status,text:async()=>options.response.body};
  const tasks=byClass.get(data.classId)||[];
  if(options.enrollment && url==='/curriculum-enrollment-catalog')body={...catalog,subjects:[{id:1,name:'Math',wpf:0,mode:'REGULAR',assignmentGroup:null},{id:4,name:'WPF Kunst',wpf:1,mode:'INDIVIDUAL',assignmentGroup:'WPF'},{id:5,name:'WPF Technik',wpf:1,mode:'INDIVIDUAL',assignmentGroup:'WPF'},{id:6,name:'Evangelische Religion',wpf:0,mode:'INDIVIDUAL',assignmentGroup:'RELIGION_ETHIK'},{id:7,name:'Ethik',wpf:0,mode:'INDIVIDUAL',assignmentGroup:'RELIGION_ETHIK'}],teaching:options.teaching ?? [{classId:10,subjectId:1,teacherId:7,semesterId:20},{classId:11,subjectId:1,teacherId:7,semesterId:20},{grade:5,subjectId:4,teacherId:7},{grade:5,subjectId:5,teacherId:7},{grade:5,subjectId:6,teacherId:7},{grade:5,subjectId:7,teacherId:7}]};
  else if(options.enrollment && url==='/assign-grade-curriculum')body={students:2,assignments:2};
  else if(options.enrollment && url==='/curriculum-wpf-roster')body=[{id:50,first_name:'Test',last_name:'Kind',subjectId:null,teacherId:null}];
  else if(options.enrollment && url==='/assign-curriculum-wpf')body={ok:true};
  else if(url==='/curriculum-releases')body={topics:releaseTopics,tasks:releaseTasks,flexibleTopics:releaseFlexibleTopics,flexibleTasks:releaseFlexibleTasks};
  else if(url==='/set-curriculum-release'){
   if(data.topicId!=null)releaseTopics=releaseTopics.map(t=>t.id===data.topicId?{...t,active:data.active}:t);
   if(data.taskId!=null)releaseTasks=releaseTasks.map(t=>t.id===data.taskId?{...t,active:data.active}:t);
   if(data.flexibleTopicId!=null) {
    releaseFlexibleTopics=releaseFlexibleTopics.map(t=>t.id===data.flexibleTopicId?{...t,active:data.active}:t);
    releaseFlexibleTasks=releaseFlexibleTasks.map(t=>(t.topicId??null)===data.flexibleTopicId?{...t,active:data.active}:t);
   }
   if(data.flexibleTaskId!=null)releaseFlexibleTasks=releaseFlexibleTasks.map(t=>t.id===data.flexibleTaskId?{...t,active:data.active}:t);
   body={ok:true};
  }
  else if(url==='/curriculum-catalog')body=catalog;
  else if(url==='/curriculum-teacher-roster')body=options.rosterHandler?await options.rosterHandler(data):(options.roster ?? defaultRoster);
  else if(url==='/curriculum-student-progress-detail')body=options.detailHandler?await options.detailHandler(data):{...progressDetail,studentId:data.studentId};
  else if(url==='/set-curriculum-stage-assessment'){
   if(options.assessmentHandler)body=await options.assessmentHandler(data);
   else {
    progressDetail={...progressDetail,stages:progressDetail.stages.map(stage=>stage.type===data.stageType&&stage.stageId===data.stageId?{...stage,status:data.status,earned:data.status==='PASSED'?true:stage.earned,inProgress:data.status==='PASSED'?false:stage.inProgress}:stage)};
    if(data.status==='PASSED'){const student=defaultRoster.find(row=>row.id===data.studentId);if(student?.activeStage?.type===data.stageType&&student.activeStage.taskId===data.stageId)student.activeStage=null;}
    const changed=progressDetail.stages.find(stage=>stage.type===data.stageType&&stage.stageId===data.stageId);body={status:changed.status,earned:changed.earned};
   }
  }
  else if(url==='/curriculum-structure')body={centralTokens,topics:centralTopics,tasks:centralTasks};
  else if(url==='/central-curriculum-overview')body={subjects:[{subjectId:1,subjectName:'Math',centralTokens,remainingRegular:100-centralTokens,remainingHard:105-centralTokens,warning:centralTokens>100}],rows:[{subjectId:1,subjectName:'Math',topicNumber:1,topicName,stageNumber:1,stageName:centralName,tokens:centralTokens}]};
  else if(url==='/preview-central-curriculum-import'){
   const bad=data.csv.includes('BAD'), warn=data.csv.includes('104');
   body={rows:[{sourceLine:2,subjectId:1,subjectName:'Math',topicNumber:1,topicName:'<img src=x>',stageNumber:1,stageName:bad?'BAD':'Imported',tokens:warn?104:5,action:bad?'ERROR':'CREATE',message:bad?'Zeile 2: Fehler':'Neue Etappe.'}],creates:bad?0:1,updates:0,unchanged:0,errors:bad?['Zeile 2: Fehler']:[],warnings:warn?[{subjectId:1,subjectName:'Math',centralTokens:104,remainingRegular:-4,remainingHard:1,warning:true}]:[],subjects:[{subjectId:1,subjectName:'Math',centralTokens:warn?104:5,remainingRegular:warn?-4:95,remainingHard:warn?1:100,warning:warn}],canImport:!bad};
  }
  else if(url==='/import-central-curriculum'){centralName='Imported';centralTokens=5;body={createdTopics:1,updatedTopics:0,createdStages:1,updatedStages:0,unchangedStages:0,subjects:[{subjectId:1,subjectName:'Math',centralTokens:5,remainingRegular:95,remainingHard:100,warning:false}]};}
  else if(url==='/curriculum-budget'){const f=tasks.reduce((n,t)=>n+t.tokens,0);body={grade:data.classId===11?6:5,centralTokens,flexibleTokens:f,totalTokens:centralTokens+f,remainingRegular:100-centralTokens-f,remainingHard:105-centralTokens-f};}
  else if(url==='/flexible-tasks')body=tasks;
  else if(url==='/flexible-curriculum-structure')body={topics:topicsByClass.get(data.classId)||[],tasks};
  else if(url==='/add-flexible-topic'){body={id:500,name:data.name};topicsByClass.get(data.classId).push(body);releaseFlexibleTopics.push({...body,active:false});}
  else if(url==='/rename-flexible-topic'){const topic=[...topicsByClass.values()].flat().find(t=>t.id===data.topicId);topic.name=data.name;body={ok:true};}
  else if(url==='/curriculum-students')body=[{id:50,first_name:'Sample',last_name:'Student',teacherId:8,classId:10}];
  else if(url==='/assign-curriculum-context' || url==='/transfer-curriculum-context')body={ok:true};
  else if(url==='/curriculum-transfer-preview')body={source:{teacherId:8,classId:10},completions:[{id:200,name:'Completed elsewhere',tokens:30}],targets:[{id:100,name:'Own task',tokens:30}]};
  else if(url==='/rename-topic'){topicName=data.name;const topic=centralTopics.find(item=>item.id===data.topicId);if(topic)topic.name=data.name;body={ok:true};}
  else if(url==='/edit-task'){
   if(data.tokens>70){ok=false;body={error:'budget_exceeded',message:'Budget exceeded',affectedContexts:[{teacherId:7,classId:10,semesterId:20,centralTokens:data.tokens,flexibleTokens:35,totalTokens:data.tokens+35}]};}
   else{centralName=data.name;centralTokens=data.tokens;const task=centralTasks.find(item=>item.id===data.taskId);if(task){task.name=data.name;task.tokens=data.tokens;}body={ok:true};}
  }
  else if(url==='/add-flexible-task'){tasks.push({id:101,name:data.name,tokens:data.tokens,topicId:data.topicId});byClass.set(data.classId,tasks);body=tasks.at(-1);releaseFlexibleTasks.push({id:body.id,name:body.name,topicId:body.topicId??null,active:false});}
  else if(url==='/add-curriculum-task'){const task={id:301,topic:data.topicId,name:data.name,tokens:data.tokens,stageNumber:data.stageNumber};centralTasks.push(task);centralName=data.name;centralTokens+=data.tokens;body={id:task.id};}
  else if(url==='/add-curriculum-topic'){const topic={id:9,name:data.name,number:data.number};centralTopics.push(topic);body={id:topic.id};}
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
function detailWith(root,text){return [...root.querySelectorAll('details')].find(d=>d.querySelector('summary')?.textContent.includes(text));}
async function clickInDetail(dom,root,detailText,buttonText){
 const detail=detailWith(root,detailText);assert.ok(detail,detailText);
 const button=[...detail.querySelectorAll('button')].find(b=>b.textContent===buttonText);assert.ok(button,`${detailText} ${buttonText}`);
 button.click();await tick();
}
test('admin can rename topics and tasks safely and sees server budget conflicts',async()=>{
 const{dom,requests,root}=await setup(true);
 try{
  let form=formWith(root,'Thema umbenennen');form.querySelector('input').value='<img src=x onerror=alert(1)>';await submit(dom,form);
  assert.equal(requests.find(r=>r.url==='/rename-topic').data.topicId,2);assert.equal(root.querySelectorAll('img').length,0);
  form=root.querySelector('.curriculum-stage-card form');const inputs=form.querySelectorAll('input');inputs[0].value='Revised';inputs[1].value=71;await submit(dom,form);
  assert.match(root.textContent,/105 Münzen/);assert.match(root.textContent,/106/);
 }finally{dom.window.close();}
});
test('admin curriculum topics stay open across edits and new content is revealed',async()=>{
 const{dom,root}=await setup(true);
 try{
  let topic=detailWith(root,'Topic');topic.open=true;
  let form=formWith(root,'Thema umbenennen');form.querySelector('input').value='Renamed';await submit(dom,form);
  topic=detailWith(root,'Renamed');assert.equal(topic.open,true);
  form=formWith(root,'Speichern');form.querySelector('input').value='Revised';await submit(dom,form);
  topic=detailWith(root,'Renamed');assert.equal(topic.open,true);
  form=formWith(root,'Etappe anlegen');const textInputs=form.querySelectorAll('input[type=text]');textInputs[textInputs.length-1].value='New stage';await submit(dom,form);
  topic=detailWith(root,'Renamed');assert.equal(topic.open,true);assert.match(topic.textContent,/New stage/);
  form=formWith(root,'Thema anlegen');form.querySelectorAll('input')[0].value='New topic';await submit(dom,form);
  const newTopic=detailWith(root,'New topic');assert.ok(newTopic);assert.equal(newTopic.open,true);
 }finally{dom.window.close();}
});
test('admin central curriculum renders topic and stage hierarchy with totals',async()=>{
 const{dom,root}=await setup(true,{centralTopics:[
  {id:2,name:'Bruchrechnung',number:1},{id:4,name:'Dezimalzahlen',number:2}
 ],centralTasks:[
  {id:3,topic:2,name:'Brüche verstehen',tokens:5,stageNumber:1},
  {id:6,topic:2,name:'Brüche addieren',tokens:7,stageNumber:2},
  {id:7,topic:4,name:'Dezimalzahlen lesen',tokens:8,stageNumber:1}
 ]});
 try{
  const topics=[...root.querySelectorAll('details.curriculum-topic-card')];assert.equal(topics.length,2);
  assert.equal(topics.every(topic=>topic.classList.contains('curriculum-topic-card')),true);
  assert.match(topics[0].querySelector('summary').textContent,/2 Etappen · 12 Münzen/);
  assert.match(topics[1].querySelector('summary').textContent,/1 Etappe · 8 Münzen/);
  const stages=topics.map(topic=>[...topic.querySelectorAll('.curriculum-stage-card')]);
  assert.deepEqual(stages.map(group=>group.length),[2,1]);
  assert.equal(stages[0].every(stage=>stage.parentElement===topics[0]),true);
  assert.equal(stages[1].every(stage=>stage.parentElement===topics[1]),true);
  assert.equal(root.querySelectorAll('.curriculum-add-stage').length,2);
  assert.ok(root.querySelector('.curriculum-add-topic'));
 }finally{dom.window.close();}
});
test('teacher gets own context, can edit and create, and UI blocks totals above 105',async()=>{
 const{dom,requests,root}=await setup(false);
 try{
  assert.equal(formWith(root,'Thema umbenennen'),undefined);
  let form=formWith(root,'Speichern');form.querySelector('input[type=number]').value=35;await submit(dom,form);
  assert.match(root.textContent,/Geplant: zentral 70 \+ flexibel 35 = 105 Münzen/);
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
test('teacher overview is a safe cockpit built only from active canonical contexts',async()=>{
 const{dom,requests}=await setup(false,{overview:true});
 try{
  const overview=dom.window.document.querySelector('#overview');
  const cards=[...overview.querySelectorAll('.teacher-status-card')].map(card=>card.textContent);
  assert.equal(cards.length,4);
  assert.match(cards[0],/Aktives HalbjahrH1/);
  assert.match(cards[1],/Lerngruppen \/ Klassen2/);
  assert.match(cards[2],/Fächer2/);
  assert.match(cards[3],/Unterrichtskontexte3/);
  assert.match(overview.textContent,/5a · Math/);
  assert.match(overview.textContent,/5b · Science/);
  assert.doesNotMatch(overview.textContent,/Legacy class|Legacy only|History/);
  assert.deepEqual([...overview.querySelectorAll('.teacher-quick-links a')].map(link=>link.getAttribute('href')),[
   '/dashboard#curriculum','/dashboard#student-progress','/attendance'
  ]);
  assert.equal(requests.some(request=>['/mydata','/teacher_classes','/teacher_subjects'].includes(request.url)),false);
  assert.equal(overview.querySelectorAll('img').length,0);
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
  assert.match(root.textContent,/Geplant: zentral 70 \+ flexibel 0 = 70 Münzen/);
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
test('student progress filters use active canonical contexts only',async()=>{
 const{dom,requests}=await setup(false,{progress:true});
 try{
  const progress=dom.window.document.querySelector('#student-progress');
  assert.ok(progress.querySelector('button').textContent.includes('Aktualisieren'));
  assert.deepEqual(optionsOf(progress.querySelector('[name=classId]')).map(option=>option.text),['5a','5b']);
  assert.deepEqual(optionsOf(progress.querySelector('[name=subjectId]')).map(option=>option.text),['Math']);
  assert.equal(progress.querySelector('[name=semesterId]'),null);
  assert.equal(progress.querySelector('[name=teacherId]'),null);
  assert.equal(progress.querySelector('[name=grade]'),null);
  assert.doesNotMatch(progress.textContent,/Legacy class|Legacy only|History/);
  assert.deepEqual(requests.filter(r=>r.url==='/curriculum-teacher-roster').at(0).data,{subjectId:1,semesterId:20,classId:10,teacherId:7});
  const classSelect=progress.querySelector('[name=classId]'),subjectSelect=progress.querySelector('[name=subjectId]');
  classSelect.value='11';classSelect.dispatchEvent(new dom.window.Event('change'));await tick();
  assert.deepEqual(optionsOf(subjectSelect),[{value:'1',text:'Math'},{value:'3',text:'Science'}]);
  assert.deepEqual(requests.filter(r=>r.url==='/curriculum-teacher-roster').at(-1).data,{subjectId:1,semesterId:20,classId:11,teacherId:7});
  subjectSelect.value='3';subjectSelect.dispatchEvent(new dom.window.Event('change'));await tick();
  assert.deepEqual(requests.filter(r=>r.url==='/curriculum-teacher-roster').at(-1).data,{subjectId:3,semesterId:20,classId:11,teacherId:7});
  const before=requests.filter(r=>r.url==='/curriculum-teacher-roster').length;
  progress.querySelector('button').click();await tick();
  assert.equal(requests.filter(r=>r.url==='/curriculum-teacher-roster').length,before+1);
 }finally{dom.window.close();}
});
test('student progress skips roster requests without active contexts or in admin mode',async()=>{
 const contexts=[{semesterId:21,semesterLabel:'H2',activeSemester:false,classId:10,classLabel:'5a',grade:5,subjectId:1,subjectName:'Math'}];
 let env=await setup(false,{progress:true,contexts});
 try{
  assert.match(env.dom.window.document.querySelector('#student-progress').textContent,/Für das aktive Halbjahr sind keine Lerngruppen oder Fächer zugewiesen/);
  assert.equal(env.requests.some(r=>r.url==='/curriculum-teacher-roster'),false);
 }finally{env.dom.window.close();}
 env=await setup(true,{progress:true});
 try{
  assert.equal(env.requests.some(r=>r.url==='/curriculum-teacher-roster'),false);
  assert.equal(env.dom.window.document.querySelector('#student-progress').querySelector('select'),null);
 }finally{env.dom.window.close();}
});
test('student progress renders active stages and signals read only',async()=>{
 const{dom,requests}=await setup(false,{progress:true});
 try{
  const progress=dom.window.document.querySelector('#student-progress');
  assert.match(progress.textContent,/Central · Zentral/);
  assert.match(progress.textContent,/Own task · Flexibel/);
  assert.match(progress.textContent,/Keine Etappe in Bearbeitung/);
  const rows=[...progress.querySelectorAll('table tr')].slice(1).map(row=>[...row.cells].map(cell=>cell.textContent));
  assert.deepEqual(rows[0],['Ada Alpha','Central · Zentral','Ja','Ja','—','—','Details']);
  assert.deepEqual(rows[1],['Ben Beta','Own task · Flexibel','—','—','Ja','Ja','Details']);
  assert.deepEqual(rows[2],['Cara Gamma','Keine Etappe in Bearbeitung','—','—','—','—','Details']);
  assert.equal(progress.querySelectorAll('form').length,0);
  assert.equal(progress.querySelectorAll('input[type=checkbox]').length,0);
  assert.equal([...progress.querySelectorAll('button')].filter(button=>button.textContent==='Details').length,3);
  assert.equal(requests.some(r=>['/subject-request','/complete-task','/lock-task','/reopen-task','/complete-flexible-task'].includes(r.url)),false);
 }finally{dom.window.close();}
});
test('student progress handles empty roster and safe text rendering',async()=>{
 let env=await setup(false,{progress:true,roster:[]});
 try{assert.match(env.dom.window.document.querySelector('#student-progress').textContent,/Für diesen Unterrichtskontext sind keine Schüler zugeordnet/);}
 finally{env.dom.window.close();}
 env=await setup(false,{progress:true,roster:[{id:1,name:'<img src=x onerror=alert(1)>',activeStage:{type:'CENTRAL',taskId:3,name:'<img src=y onerror=alert(2)>'},signals:{help:true,partner:false,experiment:false,exam:false}}]});
 try{
  const progress=env.dom.window.document.querySelector('#student-progress');
  assert.equal(progress.querySelectorAll('img').length,0);
  assert.match(progress.textContent,/<img src=x onerror=alert\(1\)>/);
  assert.match(progress.textContent,/<img src=y onerror=alert\(2\)> · Zentral/);
 }finally{env.dom.window.close();}
});
test('student progress blocks stale roster responses',async()=>{
 const pending=new Map();
 const rosterHandler=data=>new Promise(resolve=>pending.set(data.classId,resolve));
 const{dom}=await setup(false,{progress:true,rosterHandler});
 try{
  const progress=dom.window.document.querySelector('#student-progress'),classSelect=progress.querySelector('[name=classId]');
  classSelect.value='11';classSelect.dispatchEvent(new dom.window.Event('change'));await tick();
  pending.get(11)([{id:11,name:'Fresh Class',activeStage:null,signals:{help:false,partner:false,experiment:false,exam:false}}]);await tick();
  assert.match(progress.textContent,/Fresh Class/);
  pending.get(10)([{id:10,name:'Stale Class',activeStage:null,signals:{help:false,partner:false,experiment:false,exam:false}}]);await tick();
  assert.match(progress.textContent,/Fresh Class/);
  assert.doesNotMatch(progress.textContent,/Stale Class/);
 }finally{dom.window.close();}
});
test('student progress detail uses exact scope and renders all canonical states safely',async()=>{
 const detail={studentId:50,studentName:'<img src=x onerror=alert(1)>',subjectId:1,semesterId:20,stages:[
  {type:'CENTRAL',stageId:3,topicId:2,topicName:'<script>alert(1)</script>',name:'Passed',tokens:5,status:'PASSED',earned:true,inProgress:true},
  {type:'CENTRAL',stageId:4,topicId:2,topicName:'Topic',name:'Once',tokens:6,status:'FAILED_ONCE',earned:false,inProgress:false},
  {type:'CENTRAL',stageId:5,topicId:2,topicName:'Topic',name:'Twice',tokens:7,status:'FAILED_TWICE',earned:false,inProgress:false},
  {type:'FLEXIBLE',stageId:100,topicId:null,topicName:null,name:'Locked',tokens:8,status:'LOCKED',earned:true,inProgress:false},
  {type:'FLEXIBLE',stageId:101,topicId:null,topicName:null,name:'Open',tokens:9,status:null,earned:false,inProgress:false}
 ]};
 const{dom,requests}=await setup(false,{progress:true,detail});
 try{
  const progress=dom.window.document.querySelector('#student-progress');
  progress.querySelector('tbody button, table tr:nth-child(2) button').click();await tick();
  const request=requests.find(r=>r.url==='/curriculum-student-progress-detail');
  assert.deepEqual(request.data,{studentId:50,subjectId:1,classId:10,semesterId:20});
  assert.equal(Object.hasOwn(request.data,'teacherId'),false);
  assert.match(progress.textContent,/Zentral/);assert.match(progress.textContent,/Flexibel/);
  assert.match(progress.textContent,/5/);assert.match(progress.textContent,/Bestanden/);
  assert.match(progress.textContent,/1× nicht bestanden/);assert.match(progress.textContent,/2× nicht bestanden/);
  assert.match(progress.textContent,/Gesperrt/);assert.match(progress.textContent,/Noch nicht bewertet/);
  assert.match(progress.textContent,/Ohne Thema/);assert.match(progress.textContent,/In Bearbeitung/);
  assert.equal(progress.querySelectorAll('img,script').length,0);
  assert.match(progress.textContent,/<img src=x onerror=alert\(1\)>/);
  assert.match(progress.textContent,/<script>alert\(1\)<\/script>/);
 }finally{dom.window.close();}
});
test('student progress detail ignores stale responses after context changes',async()=>{
 let resolveDetail;const detailHandler=()=>new Promise(resolve=>{resolveDetail=resolve;});
 const{dom}=await setup(false,{progress:true,detailHandler});
 try{
  const progress=dom.window.document.querySelector('#student-progress');
  [...progress.querySelectorAll('button')].find(button=>button.textContent==='Details').click();await tick();
  const classSelect=progress.querySelector('[name=classId]');classSelect.value='11';classSelect.dispatchEvent(new dom.window.Event('change'));await tick();
  resolveDetail({studentId:50,studentName:'Stale Detail',subjectId:1,semesterId:20,stages:[]});await tick();
  assert.doesNotMatch(progress.textContent,/Stale Detail/);
 }finally{dom.window.close();}
});
test('assessment controls are permission gated and revocation blocks mutation',async()=>{
 let pm={...grants(),curriculum_assess_students:false};let env=await setup(false,{progress:true,pm});
 try{
  const progress=env.dom.window.document.querySelector('#student-progress');[...progress.querySelectorAll('button')].find(b=>b.textContent==='Details').click();await tick();
  assert.equal(progress.querySelectorAll('.assessment-control').length,0);
 }finally{env.dom.window.close();}
 pm={...grants(),curriculum_assess_students:true};env=await setup(false,{progress:true,pm});
 try{
  const progress=env.dom.window.document.querySelector('#student-progress');[...progress.querySelectorAll('button')].find(b=>b.textContent==='Details').click();await tick();
  const control=progress.querySelector('.assessment-control'),save=progress.querySelector('.assessment-save');assert.ok(control);control.value='PASSED';pm.curriculum_assess_students=false;save.click();await tick();
  assert.equal(env.requests.some(r=>r.url==='/set-curriculum-stage-assessment'),false);assert.match(progress.textContent,/Berechtigung/);
 }finally{env.dom.window.close();}
});
test('assessment controls send exact payloads for all four explicit states',async()=>{
 for(const status of ['PASSED','FAILED_ONCE','FAILED_TWICE','LOCKED']){
  const detail={studentId:50,studentName:'Ada Alpha',subjectId:1,semesterId:20,stages:[{type:status==='LOCKED'?'FLEXIBLE':'CENTRAL',stageId:77,topicId:null,topicName:null,name:'Stage',tokens:5,status:null,earned:false,inProgress:true}]};
  const env=await setup(false,{progress:true,detail,pm:{...grants(),curriculum_assess_students:true}});
  try{
   const progress=env.dom.window.document.querySelector('#student-progress');[...progress.querySelectorAll('button')].find(b=>b.textContent==='Details').click();await tick();
   const control=progress.querySelector('.assessment-control');control.value=status;progress.querySelector('.assessment-save').click();await tick();await tick();
   const request=env.requests.find(r=>r.url==='/set-curriculum-stage-assessment');
   assert.deepEqual(request.data,{studentId:50,subjectId:1,classId:10,semesterId:20,stageType:status==='LOCKED'?'FLEXIBLE':'CENTRAL',stageId:77,status});
   assert.equal(Object.hasOwn(request.data,'teacherId'),false);assert.equal(Object.hasOwn(request.data,'earned'),false);assert.equal(Object.hasOwn(request.data,'tokens'),false);
  }finally{env.dom.window.close();}
 }
});
test('successful assessment reloads roster and selected detail while preserving earned separately',async()=>{
 const pm={...grants(),curriculum_assess_students:true};const{dom,requests}=await setup(false,{progress:true,pm});
 try{
  const progress=dom.window.document.querySelector('#student-progress');[...progress.querySelectorAll('button')].find(b=>b.textContent==='Details').click();await tick();
  const detailBefore=requests.filter(r=>r.url==='/curriculum-student-progress-detail').length,rosterBefore=requests.filter(r=>r.url==='/curriculum-teacher-roster').length;
  const control=[...progress.querySelectorAll('.assessment-control')].find(node=>node.dataset.stageType==='CENTRAL'&&node.dataset.stageId==='3');control.value='PASSED';control.parentElement.querySelector('.assessment-save').click();await tick();await tick();
  assert.equal(requests.filter(r=>r.url==='/curriculum-teacher-roster').length,rosterBefore+1);
  assert.equal(requests.filter(r=>r.url==='/curriculum-student-progress-detail').length,detailBefore+1);
  assert.doesNotMatch([...progress.querySelectorAll('table')][0].textContent,/Central · Zentral/);
  assert.match(progress.textContent,/Bestanden/);assert.match(progress.textContent,/Ja/);
  const locked=[...progress.querySelectorAll('tr')].find(row=>row.textContent.includes('Locked flexible'));assert.match(locked.textContent,/Gesperrt/);assert.match(locked.textContent,/Ja/);
 }finally{dom.window.close();}
});
test('assessment double click sends once and unsafe server errors stay text only',async()=>{
 let resolveAssessment;const assessmentHandler=()=>new Promise(resolve=>{resolveAssessment=resolve;});
 let env=await setup(false,{progress:true,pm:{...grants(),curriculum_assess_students:true},assessmentHandler});
 try{
  const progress=env.dom.window.document.querySelector('#student-progress');[...progress.querySelectorAll('button')].find(b=>b.textContent==='Details').click();await tick();
  const control=progress.querySelector('.assessment-control'),save=progress.querySelector('.assessment-save');control.value='PASSED';save.click();save.click();await tick();
  assert.equal(env.requests.filter(r=>r.url==='/set-curriculum-stage-assessment').length,1);assert.equal(save.disabled,true);
  resolveAssessment({status:'PASSED',earned:true});await tick();await tick();
 }finally{env.dom.window.close();}
 env=await setup(false,{progress:true,pm:{...grants(),curriculum_assess_students:true},response:{url:'/set-curriculum-stage-assessment',status:500,body:'<script>alert(1)</script> stacktrace'}});
 try{
  const progress=env.dom.window.document.querySelector('#student-progress');[...progress.querySelectorAll('button')].find(b=>b.textContent==='Details').click();await tick();
  const control=progress.querySelector('.assessment-control');control.value='LOCKED';progress.querySelector('.assessment-save').click();await tick();
  assert.equal(progress.querySelectorAll('script').length,0);assert.doesNotMatch(progress.textContent,/stacktrace|alert\(1\)/);assert.match(progress.textContent,/Anfrage fehlgeschlagen/);
 }finally{env.dom.window.close();}
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
test('teacher sees one combined topics and stages area with central and flexible markers',async()=>{
 const{dom,root}=await setup(false,{pm:{...grants(),curriculum_publish:true}});
 try{
  assert.match(root.textContent,/Themen & Etappen/);
  assert.doesNotMatch(root.textContent,/Zentrale Themen und Etappen|Flexible Lehrer-Etappen|Themen und Etappen für die Klasse freischalten/);
  assert.match(root.textContent,/Geplant: zentral 70 \+ flexibel 30 = 100 Münzen · regulärer Rahmen 100 · absolute Grenze 105/);
  const central=[...root.querySelectorAll('details')].find(d=>d.querySelector('summary')?.textContent.includes('Zentral'));
  const flexible=[...root.querySelectorAll('details')].find(d=>d.querySelector('summary')?.textContent.includes('Flexibel'));
  assert.ok(central);assert.ok(flexible);
  assert.match(central.textContent,/Central: 70 Münzen/);
  assert.equal(central.querySelectorAll('input').length,0);
  assert.ok(formWith(flexible,'Speichern'));
  assert.match(root.textContent,/Ohne Thema/);
 }finally{dom.window.close();}
});
test('teacher central release buttons send topic and task changes and reload releases',async()=>{
 const centralTasks=[
  {id:3,topic:2,name:'Central A',tokens:35,niveau:1,stageNumber:1},
  {id:4,topic:2,name:'Central B',tokens:35,niveau:1,stageNumber:2}
 ];
 const releaseTasks=[{id:3,topicId:2,name:'Central A',active:true},{id:4,topicId:2,name:'Central B',active:false}];
  const{dom,root,requests}=await setup(false,{pm:{...grants(),curriculum_publish:true},centralTasks,releaseTasks});
 try{
  assert.match(root.textContent,/teilweise freigegeben/);
  const before=requests.filter(r=>r.url==='/curriculum-releases').length;
  await clickInDetail(dom,root,'Zentral','Alle freigeben');
  let sent=requests.filter(r=>r.url==='/set-curriculum-release').at(-1);
  assert.equal(sent.data.topicId,2);assert.equal(sent.data.active,true);
  assert.ok(requests.filter(r=>r.url==='/curriculum-releases').length>before);
  await clickInDetail(dom,root,'Zentral','Alle sperren');
  sent=requests.filter(r=>r.url==='/set-curriculum-release').at(-1);
  assert.equal(sent.data.topicId,2);assert.equal(sent.data.active,false);
  await clickInDetail(dom,root,'Zentral','Sperren');
  sent=requests.filter(r=>r.url==='/set-curriculum-release').at(-1);
  assert.equal(sent.data.taskId,3);assert.equal(sent.data.active,false);
 }finally{dom.window.close();}
});
test('teacher central topic status distinguishes all released all locked and mixed stages',async()=>{
 const centralTasks=[
  {id:3,topic:2,name:'Central A',tokens:35,niveau:1,stageNumber:1},
  {id:4,topic:2,name:'Central B',tokens:35,niveau:1,stageNumber:2}
 ];
 let env=await setup(false,{pm:{...grants(),curriculum_publish:true},centralTasks,releaseTasks:[{id:3,topicId:2,active:true},{id:4,topicId:2,active:true}]});
 try{assert.match(env.root.textContent,/freigegeben/);assert.doesNotMatch(env.root.textContent,/teilweise freigegeben/);}finally{env.dom.window.close();}
 env=await setup(false,{pm:{...grants(),curriculum_publish:true},centralTasks,releaseTasks:[{id:3,topicId:2,active:false},{id:4,topicId:2,active:false}]});
 try{assert.match(env.root.textContent,/gesperrt/);assert.doesNotMatch(env.root.textContent,/teilweise freigegeben/);}finally{env.dom.window.close();}
 env=await setup(false,{pm:{...grants(),curriculum_publish:true},centralTasks,releaseTasks:[{id:3,topicId:2,active:true},{id:4,topicId:2,active:false}]});
 try{assert.match(env.root.textContent,/teilweise freigegeben/);}finally{env.dom.window.close();}
});
test('teacher without publish sees central content but no release controls or writes',async()=>{
 const{dom,root,requests}=await setup(false,{pm:grants()});
 try{
  assert.match(root.textContent,/Central: 70 Münzen/);
  assert.doesNotMatch(root.textContent,/Alle freigeben|Alle sperren|Freigeben|Sperren/);
  assert.equal(requests.some(r=>r.url==='/set-curriculum-release'),false);
 }finally{dom.window.close();}
});
test('teacher flexible topics show effective release status and controls',async()=>{
 const plannedFlexibleTopics=[{id:400,name:'Practice'}];
 const plannedFlexibleTasks=[
  {id:100,name:'Flex A',tokens:10,topicId:400},
  {id:101,name:'Flex B',tokens:20,topicId:400}
 ];
 let env=await setup(false,{pm:{...grants(),curriculum_publish:true},plannedFlexibleTopics,plannedFlexibleTasks,releaseFlexibleTasks:[{id:100,topicId:400,active:true},{id:101,topicId:400,active:true}]});
 try{assert.match(detailWith(env.root,'Practice')?.querySelector('summary')?.textContent||'',/Flexibel · freigegeben/);}finally{env.dom.window.close();}
 env=await setup(false,{pm:{...grants(),curriculum_publish:true},plannedFlexibleTopics,plannedFlexibleTasks,releaseFlexibleTasks:[{id:100,topicId:400,active:false},{id:101,topicId:400,active:false}]});
 try{assert.match(detailWith(env.root,'Practice')?.querySelector('summary')?.textContent||'',/Flexibel · gesperrt/);}finally{env.dom.window.close();}
 env=await setup(false,{pm:{...grants(),curriculum_publish:true},plannedFlexibleTopics,plannedFlexibleTasks,releaseFlexibleTasks:[{id:100,topicId:400,active:true},{id:101,topicId:400,active:false}]});
 try{
  const detail=detailWith(env.root,'Practice');assert.match(detail.querySelector('summary').textContent,/Flexibel · teilweise freigegeben/);
  assert.ok([...detail.querySelectorAll('button')].some(b=>b.textContent==='Alle freigeben'));
  assert.ok([...detail.querySelectorAll('button')].some(b=>b.textContent==='Alle sperren'));
  assert.match(detail.textContent,/Flex A: 10 Münzen · freigegeben/);
  assert.match(detail.textContent,/Flex B: 20 Münzen · gesperrt/);
 }finally{env.dom.window.close();}
 env=await setup(false,{pm:{...grants(),curriculum_publish:true},plannedFlexibleTopics,plannedFlexibleTasks:[],releaseFlexibleTopics:[{id:400,name:'Practice',active:true}],releaseFlexibleTasks:[]});
 try{assert.match(detailWith(env.root,'Practice')?.querySelector('summary')?.textContent||'',/Flexibel · freigegeben/);}finally{env.dom.window.close();}
});
test('teacher flexible release actions send flexible ids and reload releases',async()=>{
 const plannedFlexibleTopics=[{id:400,name:'Practice'}];
 const plannedFlexibleTasks=[
  {id:100,name:'Flex A',tokens:10,topicId:400},
  {id:101,name:'Flex B',tokens:20,topicId:400},
  {id:102,name:'Loose',tokens:5,topicId:null}
 ];
 const releaseFlexibleTasks=[{id:100,topicId:400,active:false},{id:101,topicId:400,active:false},{id:102,topicId:null,active:false}];
 const{dom,root,requests}=await setup(false,{pm:{...grants(),curriculum_publish:true},plannedFlexibleTopics,plannedFlexibleTasks,releaseFlexibleTasks});
 try{
  let before=requests.filter(r=>r.url==='/curriculum-releases').length;
  await clickInDetail(dom,root,'Practice','Alle freigeben');
  let sent=requests.filter(r=>r.url==='/set-curriculum-release').at(-1);
  assert.equal(sent.data.flexibleTopicId,400);assert.equal(sent.data.active,true);
  assert.ok(requests.filter(r=>r.url==='/curriculum-releases').length>before);
  assert.match(detailWith(root,'Practice').querySelector('summary').textContent,/freigegeben/);
  before=requests.filter(r=>r.url==='/curriculum-releases').length;
  await clickInDetail(dom,root,'Practice','Sperren');
  sent=requests.filter(r=>r.url==='/set-curriculum-release').at(-1);
  assert.equal(sent.data.flexibleTaskId,100);assert.equal(sent.data.active,false);
  assert.ok(requests.filter(r=>r.url==='/curriculum-releases').length>before);
  assert.match(detailWith(root,'Practice').querySelector('summary').textContent,/teilweise freigegeben/);
  await clickInDetail(dom,root,'Practice','Alle freigeben');
  assert.match(detailWith(root,'Practice').querySelector('summary').textContent,/freigegeben/);
  await clickInDetail(dom,root,'Practice','Alle sperren');
  sent=requests.filter(r=>r.url==='/set-curriculum-release').at(-1);
  assert.equal(sent.data.flexibleTopicId,400);assert.equal(sent.data.active,false);
  await clickInDetail(dom,root,'Ohne Thema','Freigeben');
  sent=requests.filter(r=>r.url==='/set-curriculum-release').at(-1);
  assert.equal(sent.data.flexibleTaskId,102);assert.equal(sent.data.active,true);
  const unassigned=detailWith(root,'Ohne Thema');assert.ok(unassigned);
  assert.equal([...unassigned.querySelectorAll('button')].some(b=>b.textContent==='Alle freigeben'||b.textContent==='Alle sperren'),false);
  assert.ok([...unassigned.querySelectorAll('button')].some(b=>b.textContent==='Sperren'));
 }finally{dom.window.close();}
});
test('teacher publish and flexible management permissions stay separated',async()=>{
 const plannedFlexibleTopics=[{id:400,name:'Practice'}];
 const plannedFlexibleTasks=[{id:100,name:'Flex A',tokens:10,topicId:400}];
 let env=await setup(false,{pm:{...grants(false),curriculum_publish:true},plannedFlexibleTopics,plannedFlexibleTasks});
 try{
  const detail=detailWith(env.root,'Practice');assert.ok(detail);
  assert.equal(detail.querySelectorAll('form').length,0);
  assert.ok([...detail.querySelectorAll('button')].some(b=>b.textContent==='Alle freigeben'));
  assert.ok([...detail.querySelectorAll('button')].some(b=>b.textContent==='Freigeben'));
 }finally{env.dom.window.close();}
 env=await setup(false,{pm:{...grants(true),curriculum_publish:false},plannedFlexibleTopics,plannedFlexibleTasks});
 try{
  const detail=detailWith(env.root,'Practice');assert.ok(detail);
  assert.ok(formWith(detail,'Flexibles Thema umbenennen'));
  assert.ok(formWith(detail,'Speichern'));
  assert.equal([...detail.querySelectorAll('button')].some(b=>['Alle freigeben','Alle sperren','Freigeben','Sperren'].includes(b.textContent)),false);
 }finally{env.dom.window.close();}
});
test('new flexible elements remain locked after refresh',async()=>{
 const{dom,root}=await setup(false,{pm:{...grants(),curriculum_publish:true}});
 try{
  let form=formWith(root,'Flexibles Thema anlegen');form.querySelector('input').value='Practice';await submit(dom,form);
  let detail=detailWith(root,'Practice');assert.match(detail.querySelector('summary').textContent,/Flexibel · gesperrt/);
  form=formWith(root,'Flexible Etappe anlegen');form.querySelector('input[type=text]').value='Practice step';form.querySelector('input[type=number]').value=5;form.querySelector('[name=topicId]').value='500';await submit(dom,form);
  detail=detailWith(root,'Practice');assert.match(detail.textContent,/Practice step: 5 Münzen · gesperrt/);
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
  if(admin) {
   assert.match(sections[0].textContent+[...sections[0].querySelectorAll('input')].map(n=>n.value).join(' '),/Central/);assert.match(sections[1].textContent+[...sections[1].querySelectorAll('input')].map(n=>n.value).join(' '),/Own task/);
  } else {
   assert.match(root.textContent+[...root.querySelectorAll('input')].map(n=>n.value).join(' '),/Central/);assert.match(root.textContent+[...root.querySelectorAll('input')].map(n=>n.value).join(' '),/Own task/);
  }
  assert.equal(!!formWith(root,'Flexible Etappe anlegen'),flexible);
  assert.equal((admin?sections[1]:root).querySelectorAll('input').length>0,flexible);
  assert.equal(admin && sections[0].querySelectorAll('input').length>0,central);
  for(const label of ['Thema umbenennen','Etappe anlegen','Thema anlegen'])assert.equal(!!formWith(root,label),central);
  if(!flexible){assert.match(root.textContent,/Own task: 30 Münzen/);assert.match(root.textContent,/Flexible Inhalte sind nur lesbar|Nur lesbar/);assert.equal(root.querySelectorAll('form').length,0);}
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
  const form=formWith(admin?root.querySelectorAll('section')[section]:root,label);
  assert.ok(form);pm[section===1?'curriculum_manage_flexible':'curriculum_manage_central']=false;
  await submit(dom,form);assert.equal(requests.filter(r=>r.url===path).length,0);
  assert.equal((admin?root.querySelectorAll('section')[section]:root).querySelectorAll('form').length,0);
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
  let group=[...root.querySelectorAll('details')].find(d=>d.querySelector('summary').textContent.includes('<b>Practice</b> · Flexibel'));
  form=formWith(group,'Speichern');assert.equal(form.querySelector('[name=topicId]').value,'500');
  assert.doesNotMatch(form.textContent,/gelten auch für bereits abgeschlossene Etappen/);
  form.querySelector('[name=topicId]').value='';await submit(dom,form);
  assert.equal(requests.find(r=>r.url==='/edit-flexible-task').data.topicId,null);
  group=[...root.querySelectorAll('details')].find(d=>d.querySelector('summary').textContent==='Ohne Thema · Flexibel');
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
  await clickNamed(dom,panel,'Ausgewählte Lehrkräfte-Zuordnungen speichern');
  const request=requests.find(r=>r.url==='/assign-grade-curriculum');assert.deepEqual(request.data.subjectIds,[1]);assert.equal(request.data.teaching.length,2);assert.equal(request.data.semesterId,20);
  assert.match(panel.textContent,/2 Kinder, 2 Fachzuordnungen/);
 }finally{dom.window.close();}
});
test('admin grade batch submits only selected teacher rows',async()=>{
 const {dom,root,requests}=await setup(true,{enrollment:true,teaching:[]});try{
  const panel=root.querySelector('.curriculum-enrollment');await clickNamed(dom,panel,'Jetzt verwalten');await clickNamed(dom,panel,'Auswahl bestätigen');
  const selects=panel.querySelectorAll('.semester-create-panel .curriculum-wpf-table select');selects[0].value='7';
  await clickNamed(dom,panel,'Ausgewählte Lehrkräfte-Zuordnungen speichern');
  const writes=requests.filter(r=>r.url==='/assign-grade-curriculum');assert.equal(writes.length,1);assert.deepEqual(writes[0].data.teaching,[{classId:10,subjectId:1,teacherId:7}]);
 }finally{dom.window.close();}
});
test('admin grade batch does not request when no teacher is selected',async()=>{
 const {dom,root,requests}=await setup(true,{enrollment:true,teaching:[]});try{
  const panel=root.querySelector('.curriculum-enrollment');await clickNamed(dom,panel,'Jetzt verwalten');await clickNamed(dom,panel,'Auswahl bestätigen');
  await clickNamed(dom,panel,'Ausgewählte Lehrkräfte-Zuordnungen speichern');
  assert.equal(requests.filter(r=>r.url==='/assign-grade-curriculum').length,0);assert.match(panel.textContent,/Bitte mindestens eine Lehrkraft auswählen/);
 }finally{dom.window.close();}
});
test('admin grade batch preselects assignments only for the selected semester',async()=>{
 const {dom,root}=await setup(true,{enrollment:true,teaching:[{classId:10,subjectId:1,teacherId:7,semesterId:20},{classId:11,subjectId:1,teacherId:7,semesterId:21}]});try{
  const panel=root.querySelector('.curriculum-enrollment');await clickNamed(dom,panel,'Jetzt verwalten');await clickNamed(dom,panel,'Auswahl bestätigen');
  let selects=panel.querySelectorAll('.semester-create-panel .curriculum-wpf-table select');assert.equal(selects[0].value,'7');assert.equal(selects[1].value,'');
  const semester=[...panel.querySelectorAll('.semester-manage-panel select')].find(select=>[...select.options].some(option=>option.value==='21'));assert.ok(semester);semester.value='21';semester.dispatchEvent(new dom.window.Event('change'));await clickNamed(dom,panel,'Lehrkräfte-Tabelle aufbauen');
  selects=panel.querySelectorAll('.semester-create-panel .curriculum-wpf-table select');assert.equal(selects[0].value,'');assert.equal(selects[1].value,'7');
 }finally{dom.window.close();}
});
test('individual subject groups save independently through the guarded assignment endpoint',async()=>{
 const teaching=[{grade:5,subjectId:4,teacherId:7,semesterId:20},{grade:5,subjectId:5,teacherId:7,semesterId:20},{grade:5,subjectId:6,teacherId:7,semesterId:20},{grade:5,subjectId:7,teacherId:7,semesterId:20}];
 const {dom,root,requests}=await setup(true,{enrollment:true,teaching});try{
  const panel=root.querySelector('.curriculum-enrollment');await clickNamed(dom,panel,'Jetzt verwalten');await clickNamed(dom,panel,'Auswahl bestätigen');
  const group=[...panel.querySelectorAll('select')].find(select=>[...select.options].some(option=>option.value==='RELIGION_ETHIK'));assert.ok(group);assert.equal(group.value,'RELIGION_ETHIK');
  await clickNamed(dom,panel,'Zuordnungen laden');
  let row=panel.querySelector('.curriculum-wpf-board table tr:nth-child(2)');let selects=row.querySelectorAll('select');assert.equal(selects.length,1);selects[0].value='6';selects[0].dispatchEvent(new dom.window.Event('change'));assert.match(row.textContent,/Test Teacher/);await clickNamed(dom,panel,'Zuordnungen speichern');
  let request=requests.filter(r=>r.url==='/assign-curriculum-wpf').at(-1);assert.deepEqual(request.data,{studentId:50,subjectId:6,classId:10,semesterId:20,assignmentGroup:'RELIGION_ETHIK',expectedSubjectId:null});
  group.value='WPF';group.dispatchEvent(new dom.window.Event('change'));await clickNamed(dom,panel,'Zuordnungen laden');
  row=panel.querySelector('.curriculum-wpf-board table tr:nth-child(2)');selects=row.querySelectorAll('select');assert.equal(selects.length,1);selects[0].value='5';selects[0].dispatchEvent(new dom.window.Event('change'));assert.match(row.textContent,/Test Teacher/);await clickNamed(dom,panel,'Zuordnungen speichern');
  request=requests.filter(r=>r.url==='/assign-curriculum-wpf').at(-1);assert.deepEqual(request.data,{studentId:50,subjectId:5,classId:10,semesterId:20,assignmentGroup:'WPF',expectedSubjectId:null});
 }finally{dom.window.close();}
});
test('teacher publishes a whole topic or individual task but has no enrollment UI',async()=>{
 const {dom,root,requests}=await setup(false,{enrollment:true,pm:{...grants(),curriculum_publish:true}});try{
  assert.doesNotMatch(root.querySelector('.curriculum-enrollment').textContent,/WPF-Zuteilung/);
  assert.equal(root.textContent.includes('Themen und Etappen für die Klasse freischalten'),false);
  await clickInDetail(dom,root,'Zentral','Alle freigeben');await clickInDetail(dom,root,'Zentral','Freigeben');
  const writes=requests.filter(r=>r.url==='/set-curriculum-release');assert.equal(writes[0].data.topicId,2);assert.equal(writes[1].data.taskId,3);assert.equal(writes[0].data.teacherId,7);
 }finally{dom.window.close();}
});
test('old publication controls cannot write after scope change or permission revocation',async()=>{
 const pm={...grants(),curriculum_publish:true};const {dom,root,requests}=await setup(false,{enrollment:true,pm});try{
  const button=[...detailWith(root,'Zentral').querySelectorAll('button')].find(b=>b.textContent==='Freigeben');root.querySelector('[name=classId]').value='11';root.querySelector('[name=classId]').dispatchEvent(new dom.window.Event('change'));button.click();await tick();assert.equal(requests.filter(r=>r.url==='/set-curriculum-release').length,0);
  root.querySelector('[name=classId]').value='10';root.querySelector('[name=classId]').dispatchEvent(new dom.window.Event('change'));await tick();const fresh=[...detailWith(root,'Zentral').querySelectorAll('button')].find(b=>b.textContent==='Freigeben');pm.curriculum_publish=false;fresh.click();await tick();assert.equal(requests.filter(r=>r.url==='/set-curriculum-release').length,0);
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
