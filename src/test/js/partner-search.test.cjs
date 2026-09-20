const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM}=require('jsdom');

const script=fs.readFileSync(path.join(__dirname,'../../main/resources/js/site/student-database.js'),'utf8');
const partnerPageScript=fs.readFileSync(path.join(__dirname,'../../main/resources/js/user/build_partner_search.js'),'utf8');

function setup() {
  const dom=new JSDOM('<!doctype html><html><body><table><tbody id="studentTableBody"></tbody></table></body></html>',{url:'https://school.example.invalid/partner',runScripts:'dangerously'});
  const requests=[];
  dom.window.fetch=async (url,options={})=>{
    requests.push({url,body:options.body ? JSON.parse(options.body) : null});
    return {ok:true,json:async()=>[{id:7,name:'<img src=x onerror=alert(1)> Ada'}]};
  };
  dom.window.eval(script);
  return {dom,requests};
}

test('partner search sends only subject id',async()=>{
  const {dom,requests}=setup();
  try {
    await dom.window.searchPartner(3);
    assert.deepEqual(requests,[{url:'/search-partner',body:{subjectId:3}}]);
    assert.equal('studentId' in requests[0].body,false);
    assert.equal('classId' in requests[0].body,false);
    assert.equal('topicId' in requests[0].body,false);
    assert.equal('semesterId' in requests[0].body,false);
  } finally {
    dom.window.close();
  }
});

test('canonical subject helper posts an empty scope',async()=>{
  const {dom,requests}=setup();
  try {
    await dom.window.fetchMyCurriculumSubjects();
    assert.deepEqual(requests,[{url:'/my-curriculum-subjects',body:{}}]);
  } finally {
    dom.window.close();
  }
});

test('partner list avoids current topic lookup and renders names as text while firing row event',async()=>{
  const {dom,requests}=setup();
  let currentTopicCalls=0;
  let rowEvents=0;
  dom.window.fetchMyCurrentTopic=async ()=>{
    currentTopicCalls++;
    throw new Error('current topic must not be loaded');
  };
  dom.window.document.addEventListener('populate-partner-row',(event)=>{
    rowEvents++;
    assert.equal(event.detail.student.id,7);
    assert.equal(event.detail.row.tagName,'TR');
  });
  try {
    await dom.window.populatePartnerSubjectStudentList(3);
    const tbody=dom.window.document.getElementById('studentTableBody');
    assert.equal(currentTopicCalls,0);
    assert.deepEqual(requests,[{url:'/search-partner',body:{subjectId:3}}]);
    assert.equal(tbody.querySelectorAll('tr').length,1);
    assert.equal(tbody.querySelector('.student-name').textContent,'<img src=x onerror=alert(1)> Ada');
    assert.equal(tbody.querySelector('.student-name').children.length,0);
    assert.equal(rowEvents,1);
  } finally {
    dom.window.close();
  }
});

function setupPartnerPage(subjects) {
  const dom=new JSDOM('<!doctype html><html><body><select id="subjectSelect"></select><table><tbody id="studentTableBody"></tbody></table></body></html>',{url:'https://school.example.invalid/partner',runScripts:'dangerously'});
  const searches=[];
  dom.window.fetchMyCurriculumSubjects=async ()=>subjects;
  dom.window.populateSubjectSelect=(id,values)=>values.forEach(subject=>{
    const option=dom.window.document.createElement('option');
    option.value=subject.id;
    option.textContent=subject.name;
    dom.window.document.getElementById(id).appendChild(option);
  });
  dom.window.populatePartnerSubjectStudentList=async subjectId=>searches.push(subjectId);
  dom.window.eval(partnerPageScript);
  return {dom,searches};
}

test('partner page uses canonical subjects and reloads on numeric subject changes',async()=>{
  const {dom,searches}=setupPartnerPage([{id:3,name:'Mathematik'},{id:7,name:'WPF Kunst'}]);
  try {
    await new Promise(resolve=>setTimeout(resolve,0));
    const select=dom.window.document.getElementById('subjectSelect');
    assert.deepEqual([...select.options].map(option=>({id:Number(option.value),name:option.textContent})),[
      {id:3,name:'Mathematik'},{id:7,name:'WPF Kunst'}
    ]);
    assert.deepEqual(searches,[3]);
    select.value='7';
    select.dispatchEvent(new dom.window.Event('change'));
    await new Promise(resolve=>setTimeout(resolve,0));
    assert.deepEqual(searches,[3,7]);
  } finally {
    dom.window.close();
  }
});

test('partner page does not search when no canonical subjects exist',async()=>{
  const {dom,searches}=setupPartnerPage([]);
  try {
    await new Promise(resolve=>setTimeout(resolve,0));
    assert.deepEqual(searches,[]);
    assert.equal(dom.window.document.getElementById('subjectSelect').options.length,0);
  } finally {
    dom.window.close();
  }
});

test('partner page contains no legacy subject or student data source',()=>{
  assert.equal(partnerPageScript.includes('/mysubjects'),false);
  assert.equal(partnerPageScript.includes('/mydata'),false);
  assert.equal(partnerPageScript.includes('fetchMySubjects'),false);
  assert.equal(partnerPageScript.includes('fetchMyData'),false);
  assert.equal(partnerPageScript.includes('fetchMyCurrentTopic'),false);
  assert.equal(partnerPageScript.includes('studentData'),false);
});
