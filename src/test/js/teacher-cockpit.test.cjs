const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM}=require('jsdom');

const root=path.join(__dirname,'../../main/resources');
const html=fs.readFileSync(path.join(root,'html/teacher/dashboard.html'),'utf8');
const script=fs.readFileSync(path.join(root,'js/teacher/build_dashboard.js'),'utf8');
const navigation=JSON.parse(fs.readFileSync(path.join(root,'meta/navigation/navigation_elements.json'),'utf8'));

function dashboard(hash='') {
  const nav='<ul><li><a href="/dashboard#overview">Übersicht</a></li><li><a href="/dashboard#curriculum">Themen &amp; Etappen</a></li><li><a href="/dashboard#student-progress">Schülerfortschritt</a></li><li><a href="/attendance">Anwesenheit</a></li></ul>';
  const markup=html
    .replace('%[site;title=Lehrer-Dashboard;content=!FOLLOWS]','')
    .replace('%[teacher_dashboard_nav]',nav)
    .replace('<script src="build_dashboard.js"></script>','')
    .replace('<script src="/curriculum.js" defer></script>','');
  return new JSDOM(markup,{url:`https://school.example.invalid/dashboard${hash}`,runScripts:'dangerously'});
}

const visiblePages=document=>[...document.querySelectorAll('.teacher-function-page')].filter(page=>!page.hidden).map(page=>page.id);

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

for (const [hash,page,label] of [
  ['', 'overview', 'Übersicht'],
  ['#overview', 'overview', 'Übersicht'],
  ['#curriculum', 'curriculum', 'Themen & Etappen'],
  ['#student-progress', 'student-progress', 'Schülerfortschritt'],
  ['#ungueltig', 'overview', 'Übersicht']
]) test(`teacher hash ${hash || 'default'} opens only ${page}`,()=>{
  const dom=dashboard(hash);
  try {
    assert.deepEqual(visiblePages(dom.window.document),[page]);
    const current=dom.window.document.querySelector('.teacher-main-menu a[aria-current="page"]');
    assert.equal(current?.textContent,label);
    assert.equal(dom.window.document.querySelectorAll('.teacher-main-menu a[aria-current="page"]').length,1);
  } finally { dom.window.close(); }
});

test('teacher hash navigation changes pages without reload and keeps attendance external',()=>{
  const dom=dashboard('#overview');
  try {
    dom.window.location.hash='#student-progress';
    dom.window.dispatchEvent(new dom.window.Event('hashchange'));
    assert.deepEqual(visiblePages(dom.window.document),['student-progress']);
    assert.equal(dom.window.document.querySelector('a[href="/attendance"]').hasAttribute('aria-current'),false);
    dom.window.location.hash='#curriculum';
    dom.window.dispatchEvent(new dom.window.Event('hashchange'));
    assert.deepEqual(visiblePages(dom.window.document),['curriculum']);
  } finally { dom.window.close(); }
});
