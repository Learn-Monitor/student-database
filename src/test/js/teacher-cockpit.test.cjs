const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM}=require('jsdom');

const root=path.join(__dirname,'../../main/resources');
const html=fs.readFileSync(path.join(root,'html/teacher/dashboard.html'),'utf8');
const script=fs.readFileSync(path.join(root,'js/teacher/build_dashboard.js'),'utf8');
const navigation=JSON.parse(fs.readFileSync(path.join(root,'meta/navigation/navigation_elements.json'),'utf8'));

test('teacher cockpit contains canonical core sections without legacy teacher data',()=>{
  assert.match(html,/id="overview"/);
  assert.match(html,/id="curriculum"/);
  assert.match(html,/id="student-progress"/);
  assert.doesNotMatch(html,/%\[teacher_infos\]/);
  assert.doesNotMatch(html,/WPF|Zuordnung speichern|assign-curriculum|curriculum-enrollment/);
});

test('teacher dashboard bootstrap keeps plugin lifecycle without legacy fetches',async()=>{
  assert.doesNotMatch(script,/fetchMyClasses|fetchMySubjects|buildTeacherDashboard|\/mydata/);
  const dom=new JSDOM('<main></main>',{runScripts:'outside-only'});let loads=0;
  dom.window.teacherDashboardLoadEvent=()=>dom.window.document.dispatchEvent(new dom.window.Event('teacher-dashboard-load'));
  dom.window.document.addEventListener('teacher-dashboard-load',()=>loads++);
  dom.window.eval(script);await new Promise(resolve=>setTimeout(resolve,20));
  assert.equal(loads,1);dom.window.close();
});

test('teacher cockpit navigation exposes core areas without duplicating attendance',()=>{
  const items=navigation.filter(item=>item.type==='TEACHER_DASHBOARD');
  assert.equal(items.filter(item=>item.path==='/dashboard#overview'&&item.label==='Übersicht').length,1);
  assert.equal(items.filter(item=>item.path==='/dashboard#curriculum'&&item.label==='Themen & Etappen').length,1);
  assert.equal(items.filter(item=>item.path==='/dashboard#student-progress'&&item.label==='Schülerfortschritt').length,1);
  assert.equal(items.some(item=>item.path==='/attendance'||item.label==='Anwesenheit'),false);
  assert.equal(items.some(item=>item.label==='Mein Konto'),false);
});
