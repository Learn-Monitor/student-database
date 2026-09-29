const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM,VirtualConsole}=require('jsdom');

const script=fs.readFileSync(path.join(__dirname,'../../main/resources/js/site/student-database.js'),'utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,25));

function setup(options={}) {
  const virtualConsole=new VirtualConsole();
  const consoleErrors=[];
  virtualConsole.on('jsdomError',error=>consoleErrors.push(error));
  const dom=new JSDOM('<!doctype html><html><body><div id="subject-list"></div><span id="student-name"></span><span id="student-class"></span><span id="student-email"></span><span id="student-graduation"></span></body></html>',{
    url:'https://school.example.invalid/student',runScripts:'dangerously',virtualConsole
  });
  const requests=[];
  let catalog=options.catalog || {centralTasks:[],flexibleTasks:[],centralTopics:[],flexibleTopics:[],activeStage:null};
  dom.window.studentData={currentRequests:{}};
  dom.window.fetch=async(url,requestOptions={})=>{
    const data=requestOptions.body ? JSON.parse(requestOptions.body) : null;
    requests.push({url,data});
    if(url==='/my-curriculum-catalog') {
      if(options.catalogError) return {ok:false,status:options.catalogError.status,json:async()=>options.catalogError.body};
      return {ok:true,status:200,json:async()=>catalog};
    }
    if(url==='/current-topic') {
      if(options.legacyTopicError) return {ok:false,status:400,json:async()=>({message:'No current topic'})};
      return {ok:true,status:200,json:async()=>options.legacyTopic || {name:'Legacy topic',id:4,tasks:[]}};
    }
    if(url==='/tasks') return {ok:true,status:200,json:async()=>[]};
    if(url==='/begin-task' || url==='/cancel-task' || url==='/begin-flexible-task' || url==='/cancel-flexible-task')
      return {ok:true,status:200,json:async()=>({ok:true})};
    if(url==='/subject-request') return {ok:true,status:200,json:async()=>({ok:true})};
    throw new Error(`Unexpected endpoint ${url}`);
  };
  dom.window.eval(script);
  return {dom,requests,consoleErrors,subject:{id:7,name:'Mathematik'}};
}

async function openPanel(dom,panel) {
  panel.querySelector('h3').click();
  await tick();
}

test('managed subject with no releases renders a clear empty state and disables requests',async()=>{
  const {dom,requests,subject,consoleErrors}=setup();
  try {
    const panel=dom.window.createSubjectPanel(subject,dom.window.studentData,false,new Set([subject.id]));
    dom.window.document.body.appendChild(panel);
    await openPanel(dom,panel);
    assert.match(panel.textContent,/Noch keine aktive Etappe gewählt/);
    assert.match(panel.textContent,/Für dieses Fach sind noch keine Etappen freigeschaltet/);
    assert.equal(panel.querySelectorAll('button').length,4);
    assert.equal([...panel.querySelectorAll('button')].every(button=>button.disabled),true);
    assert.equal(requests.some(request=>request.url==='/current-topic'),false);
    assert.equal(consoleErrors.length,0);
  } finally { dom.window.close(); }
});

test('managed central stages show levels, active stage and completed history without reactivation',async()=>{
  const {dom,requests,subject}=setup({catalog:{
    activeStage:{type:'CENTRAL',taskId:2,name:'Brüche addieren',niveau:2},
    centralTasks:[
      {id:1,name:'Brüche verstehen',topicName:'Brüche',stageNumber:1,niveau:1,tokens:5,active:true,completed:false,inProgress:false},
      {id:2,name:'Brüche addieren',topicName:'Brüche',stageNumber:2,niveau:2,tokens:7,active:true,completed:false,inProgress:true},
      {id:3,name:'Brüche anwenden',topicName:'Brüche',stageNumber:3,niveau:3,tokens:8,active:false,completed:true,inProgress:false}
    ],flexibleTasks:[]
  }});
  try {
    const panel=dom.window.createSubjectPanel(subject,dom.window.studentData,false,new Set([subject.id]));
    dom.window.document.body.appendChild(panel);
    await openPanel(dom,panel);
    assert.match(panel.textContent,/Brüche addieren/);
    assert.match(panel.textContent,/Bergsteiger/);
    assert.match(panel.textContent,/Wanderer/);
    assert.match(panel.textContent,/Gipfelstürmer/);
    const headings=[...panel.querySelectorAll('h4')];
    const completedHeading=headings.find(heading=>heading.textContent==='Abgeschlossene Etappen:');
    assert.ok(completedHeading);
    const completedList=completedHeading.nextElementSibling;
    assert.equal(completedList.querySelectorAll('li').length,1);
    assert.equal(completedList.querySelector('li').style.cursor,'');
    const centralHeading=headings.find(heading=>heading.textContent==='Freigegebene zentrale Etappen:');
    const centralItems=centralHeading.nextElementSibling.querySelectorAll('li');
    centralItems[0].click();
    await tick();
    assert.equal(requests.filter(request=>request.url==='/begin-task').length,1);
    centralItems[1].click();
    await tick();
    assert.equal(requests.filter(request=>request.url==='/cancel-task').length,1);
  } finally { dom.window.close(); }
});

test('managed flexible stages use their activation and cancellation endpoints',async()=>{
  const {dom,requests,subject}=setup({catalog:{
    activeStage:{type:'FLEXIBLE',taskId:9,name:'Projektaufgabe'},centralTasks:[],
    flexibleTasks:[{id:9,name:'Projektaufgabe',tokens:10,active:true,completed:false,inProgress:true},
      {id:10,name:'Neue Projektaufgabe',tokens:12,active:true,completed:false,inProgress:false}]
  }});
  try {
    const panel=dom.window.createSubjectPanel(subject,dom.window.studentData,false,new Set([subject.id]));
    dom.window.document.body.appendChild(panel);
    await openPanel(dom,panel);
    const heading=[...panel.querySelectorAll('h4')].find(node=>node.textContent==='Freigegebene flexible Etappen:');
    const items=heading.nextElementSibling.querySelectorAll('li');
    items[0].click();
    await tick();
    items[1].click();
    await tick();
    assert.equal(requests.filter(request=>request.url==='/cancel-flexible-task').length,1);
    assert.equal(requests.filter(request=>request.url==='/begin-flexible-task').length,1);
  } finally { dom.window.close(); }
});

test('managed catalog errors are shown without falling into the legacy renderer',async()=>{
  const {dom,requests,subject,consoleErrors}=setup({catalogError:{status:500,body:{error:'internal',message:'Katalog nicht verfügbar'}}});
  try {
    const panel=dom.window.createSubjectPanel(subject,dom.window.studentData,false,new Set([subject.id]));
    dom.window.document.body.appendChild(panel);
    await openPanel(dom,panel);
    assert.match(panel.textContent,/Katalog nicht verfügbar/);
    assert.equal(requests.some(request=>request.url==='/current-topic'),false);
    assert.equal(consoleErrors.length,0);
  } finally { dom.window.close(); }
});

test('legacy subjects show an empty-topic message instead of a TypeError',async()=>{
  const {dom,requests,subject,consoleErrors}=setup({legacyTopicError:true});
  try {
    const panel=dom.window.createSubjectPanel(subject,dom.window.studentData,false,new Set());
    dom.window.document.body.appendChild(panel);
    await openPanel(dom,panel);
    assert.match(panel.textContent,/Für dieses Fach ist aktuell kein Thema ausgewählt/);
    assert.equal(requests.filter(request=>request.url==='/current-topic').length,1);
    assert.equal(consoleErrors.length,0);
  } finally { dom.window.close(); }
});

test('managed request buttons are enabled only with an active stage and preserve confirmed state on API failure',async()=>{
  const {dom,subject}=setup({catalog:{activeStage:null,centralTasks:[],flexibleTasks:[]}});
  try {
    dom.window.studentData.currentRequests={};
    const panel=dom.window.createSubjectPanel(subject,dom.window.studentData,false,new Set([subject.id]));
    dom.window.document.body.appendChild(panel);
    await openPanel(dom,panel);
    assert.equal([...panel.querySelectorAll('button')].every(button=>button.disabled),true);
  } finally { dom.window.close(); }
});
