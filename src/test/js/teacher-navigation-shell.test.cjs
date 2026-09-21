const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM}=require('jsdom');

const resources=path.join(__dirname,'../../main/resources');
const dashboardHtml=fs.readFileSync(path.join(resources,'html/teacher/dashboard.html'),'utf8');
const studentHtml=fs.readFileSync(path.join(resources,'html/teacher/student.html'),'utf8');
const siteTemplate=fs.readFileSync(path.join(resources,'templates/html/site.html'),'utf8');
const style=fs.readFileSync(path.join(resources,'css/site/style.css'),'utf8');
const navigationScript=fs.readFileSync(path.join(resources,'js/site/teacher-navigation.js'),'utf8');
const templates=JSON.parse(fs.readFileSync(path.join(resources,'meta/templates/templates.json'),'utf8'));
const paths=JSON.parse(fs.readFileSync(path.join(resources,'meta/paths/get_paths.json'),'utf8'));

const navigation='<ul><li><a href="/dashboard#overview">Übersicht</a></li><li><a href="/dashboard#curriculum">Themen &amp; Etappen</a></li><li><a href="/dashboard#student-progress">Schülerfortschritt</a></li><li><a href="/attendance">Anwesenheit</a></li></ul>';

function shell(url,markup) {
  const dom=new JSDOM(`<!doctype html><html><body>${markup}</body></html>`,{
    url,
    runScripts:'outside-only'
  });
  dom.window.eval(navigationScript);
  dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
  return dom;
}

test('dashboard and teacher student view each declare one shared teacher navigation',()=>{
  for (const html of [dashboardHtml,studentHtml]) {
    assert.equal((html.match(/class="teacher-main-menu"/g)||[]).length,1);
    assert.equal((html.match(/%\[teacher_dashboard_nav\]/g)||[]).length,1);
  }
  assert.ok(studentHtml.indexOf('teacher-main-menu') < studentHtml.indexOf('id="subjects"'));
  assert.match(studentHtml,/src="build_student\.js"/);
});

test('attendance compatibility template resolves to the canonical teacher navigation list',()=>{
  assert.deepEqual(templates.teacher_other_nav,{
    type:'HTMLNavigationTemplate',
    navigation_type:'TEACHER_DASHBOARD',
    appearance:'LIST_APPEARANCE'
  });
  assert.equal((siteTemplate.match(/teacher-navigation\.js/g)||[]).length,1);
  assert.deepEqual(paths['/teacher-navigation.js'],{
    type:'GET',
    handler_type:'FileRequestHandler',
    namespaces:['site'],
    context:'js',
    access_level:'public'
  });
});

test('teacher navigation uses the existing sticky shell styles for wrapped and direct lists',()=>{
  assert.match(style,/\.teacher-main-menu\s*\{[^}]*position:sticky;[^}]*top:0;[^}]*z-index:1000;/);
  assert.match(style,/\.teacher-main-menu-sections > ul, \.teacher-main-menu > ul/);
  assert.match(style,/\.teacher-main-menu a\[aria-current="page"\]/);
});

for (const [url,label] of [
  ['https://school.example.invalid/dashboard','Übersicht'],
  ['https://school.example.invalid/dashboard#overview','Übersicht'],
  ['https://school.example.invalid/dashboard#curriculum','Themen & Etappen'],
  ['https://school.example.invalid/dashboard#student-progress','Schülerfortschritt'],
  ['https://school.example.invalid/student','Schülerfortschritt']
]) test(`${new URL(url).pathname}${new URL(url).hash || ' default'} marks ${label} active`,()=>{
  const dom=shell(url,`<nav class="teacher-main-menu">${navigation}</nav>`);
  try {
    const current=dom.window.document.querySelectorAll('.teacher-main-menu a[aria-current="page"]');
    assert.equal(current.length,1);
    assert.equal(current[0].textContent,label);
  } finally { dom.window.close(); }
});

test('attendance view receives one sticky shell with attendance active',()=>{
  const dom=shell('https://school.example.invalid/attendance',`<main class="attendance"><nav>${navigation}</nav><h1>Attendance</h1></main>`);
  try {
    const menus=dom.window.document.querySelectorAll('.teacher-main-menu');
    assert.equal(menus.length,1);
    assert.equal(menus[0].getAttribute('aria-label'),'Lehrkraftbereiche');
    const current=menus[0].querySelectorAll('a[aria-current="page"]');
    assert.equal(current.length,1);
    assert.equal(current[0].getAttribute('href'),'/attendance');
  } finally { dom.window.close(); }
});

test('hash changes update the active dashboard item without duplicating navigation',()=>{
  const dom=shell('https://school.example.invalid/dashboard',`<nav class="teacher-main-menu">${navigation}</nav>`);
  try {
    dom.window.location.hash='#curriculum';
    dom.window.dispatchEvent(new dom.window.Event('hashchange'));
    assert.equal(dom.window.document.querySelectorAll('.teacher-main-menu').length,1);
    assert.equal(dom.window.document.querySelector('.teacher-main-menu a[aria-current="page"]').textContent,'Themen & Etappen');
  } finally { dom.window.close(); }
});

test('shared navigation script does not alter unrelated pages',()=>{
  const dom=shell('https://school.example.invalid/login','<main class="attendance"><nav><a href="/dashboard">Zurück</a></nav></main>');
  try {
    assert.equal(dom.window.document.querySelectorAll('.teacher-main-menu').length,0);
    assert.equal(dom.window.document.querySelectorAll('[aria-current]').length,0);
  } finally { dom.window.close(); }
});

test('teacher navigation uses safe static DOM updates only',()=>{
  assert.doesNotMatch(navigationScript,/innerHTML|outerHTML|insertAdjacentHTML|onclick/i);
});
