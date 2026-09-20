const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM}=require('jsdom');

const script=fs.readFileSync(path.join(__dirname,'../../main/resources/js/site/student-database.js'),'utf8');

function setup() {
  const dom=new JSDOM('<!doctype html><html><body></body></html>',{url:'https://school.example.invalid/student',runScripts:'dangerously'});
  const requests=[];
  dom.window.fetch=async (url,options={})=>{
    requests.push({url,body:options.body ? JSON.parse(options.body) : null});
    return {ok:true,json:async()=>({})};
  };
  dom.window.eval(script);
  return {dom,requests};
}

test('student subject request writes do not send a student identity',async()=>{
  const {dom,requests}=setup();
  try {
    assert.equal('teacherPerms' in dom.window,false);
    dom.window.studentData={currentRequests:{}};
    const button=dom.window.createRequestButton({id:3},'hilfe','Hilfe',false);
    assert.equal(button.tagName,'BUTTON');
    button.click();
    await dom.window.addSubjectRequest(3,'hilfe',99);
    await dom.window.removeSubjectRequest(3,'partner',99);
    assert.deepEqual(requests[0],{url:'/subject-request',body:{subjectId:3,subjectRequest:'hilfe'}});
    assert.deepEqual(requests[1],{url:'/subject-request',body:{subjectId:3,subjectRequest:'hilfe'}});
    assert.deepEqual(requests[2],{url:'/subject-request',body:{subjectId:3,subjectRequest:'partner',remove:true}});
  } finally {
    dom.window.close();
  }
});

test('teacher mode does not create writable subject request controls',()=>{
  const {dom,requests}=setup();
  try {
    assert.equal('teacherPerms' in dom.window,false);
    const node=dom.window.createRequestButton({id:3},'hilfe','Hilfe',true);
    assert.equal(node.nodeType,dom.window.Node.TEXT_NODE);
    node.dispatchEvent(new dom.window.Event('click'));
    assert.deepEqual(requests,[]);
  } finally {
    dom.window.close();
  }
});
