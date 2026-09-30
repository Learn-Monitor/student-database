const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM}=require('jsdom');

const script=fs.readFileSync(path.join(__dirname,'../../main/resources/js/user/build_dashboard.js'),'utf8');

test('student dashboard starts independent base reads together and never loads current-topic during bootstrap',async()=>{
  const dom=new JSDOM('<!doctype html><html><body></body></html>',{
    url:'https://school.example.invalid/dashboard',runScripts:'outside-only'
  });
  const started=[];
  const resolvers={};
  const result={};
  dom.window.fetchMyData=()=>{started.push('/mydata');return new Promise(resolve=>{resolvers.data=resolve;});};
  dom.window.fetchMySubjects=()=>{started.push('/mysubjects');return new Promise(resolve=>{resolvers.subjects=resolve;});};
  dom.window.fetchMyManagedSubjects=()=>{started.push('/my-curriculum-subjects');return new Promise(resolve=>{resolvers.managed=resolve;});};
  dom.window.fetchCurrentTopic=()=>{throw new Error('current-topic must not be part of dashboard bootstrap');};
  dom.window.loadStudentDashboard=(studentData,subjects,teacherView,managedSubjects)=>{
    result.studentData=studentData;result.subjects=subjects;result.teacherView=teacherView;result.managedSubjects=managedSubjects;
  };
  dom.window.eval(script);
  await new Promise(resolve=>setTimeout(resolve,0));
  assert.deepEqual(started,['/mydata','/mysubjects','/my-curriculum-subjects']);
  resolvers.data({id:7});resolvers.subjects([{id:11,name:'Mathematik'}]);resolvers.managed([{id:11}]);
  await new Promise(resolve=>setTimeout(resolve,0));
  assert.deepEqual(result,{studentData:{id:7},subjects:[{id:11,name:'Mathematik'}],teacherView:false,managedSubjects:[{id:11}]});
  dom.window.close();
});
