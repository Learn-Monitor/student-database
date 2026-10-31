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

function shell(url,markup,classes=null) {
  const dom=new JSDOM(`<!doctype html><html><body>${markup}</body></html>`,{
    url,
    runScripts:'outside-only'
  });
  dom.window.__fetchCalls=[];
  dom.window.fetch=classes === null
    ? (() => new Promise(() => {}))
    : (async(url)=>{dom.window.__fetchCalls.push(url); return url === '/dashboard'
      ? ({ok:true,status:200,text:async()=>markup.includes('admin-main-menu')?'<nav class="admin-main-menu"></nav>':'<nav class="teacher-main-menu"></nav>'})
      : ({ok:true,status:200,json:async()=>classes});
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
  assert.match(studentHtml,/class="teacher-dashboard teacher-student-view"/);
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

test('teacher logout styling is scoped to the teacher navigation',()=>{
  assert.match(style,/\.teacher-main-menu \.teacher-main-menu-logout-item\s*\{[^}]*margin-left:auto;/);
  assert.match(style,/\.teacher-main-menu \.teacher-main-menu-logout-item button\s*\{[^}]*display:inline-flex;/s);
  assert.match(style,/\.teacher-main-menu \.teacher-main-menu-logout-item button:hover,/);
  assert.doesNotMatch(style,/body\s*>\s*header\s*\{\s*display:\s*flex/);
  assert.doesNotMatch(style,/^\.logout-button\s*\{/m);
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

test('attendance view receives one sticky shell with attendance active',async()=>{
  const dom=shell('https://school.example.invalid/attendance',`<main class="attendance"><nav>${navigation}</nav><h1>Attendance</h1></main>`,[]);
  await new Promise(resolve=>setTimeout(resolve,0));
  try {
    const menus=dom.window.document.querySelectorAll('.teacher-main-menu');
    assert.equal(menus.length,1);
    assert.equal(menus[0].getAttribute('aria-label'),'Lehrkraftbereiche');
    const current=menus[0].querySelectorAll('a[aria-current="page"]');
    assert.equal(current.length,1);
    assert.equal(current[0].getAttribute('href'),'/attendance');
  } finally { dom.window.close(); }
});

test('admin attendance view does not query teacher-only tutor classes',async()=>{
  const dom=shell('https://school.example.invalid/attendance',`<main class="attendance"><nav class="admin-main-menu"><ul><li><a href="/attendance">Anwesenheit</a></li></ul></nav><h1>Attendance</h1></main>`,[]);
  await new Promise(resolve=>setTimeout(resolve,0));
  try {
    assert.equal(dom.window.__fetchCalls.filter(path=>path==='/my-tutor-classes').length,0);
    assert.equal(dom.window.document.querySelector('.admin-main-menu').classList.contains('teacher-main-menu'),false);
  } finally { dom.window.close(); }
});

test('admin attendance view replaces the compatibility teacher menu with the server-provided admin menu',async()=>{
  const dom=shell('https://school.example.invalid/attendance',`<main class="attendance teacher-dashboard"><nav class="teacher-main-menu"><ul><li><a href="/attendance">Anwesenheit</a></li></ul></nav><p hidden>class="admin-main-menu"</p><h1>Attendance</h1></main>`,[]);
  await new Promise(resolve=>setTimeout(resolve,0));
  try {
    const menu=dom.window.document.querySelector('main.attendance > nav');
    assert.equal(menu?.classList.contains('admin-main-menu'),true);
    assert.equal(menu?.classList.contains('teacher-main-menu'),false);
    assert.equal(dom.window.document.querySelector('main.attendance')?.classList.contains('teacher-dashboard'),false);
    assert.equal(dom.window.document.querySelector('main.attendance')?.classList.contains('admin-dashboard-page'),true);
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

test('tutor navigation is added only when the canonical tutor-class lookup returns a class',async()=>{
  const dom=shell('https://school.example.invalid/dashboard',`<nav class="teacher-main-menu">${navigation}</nav>`,[{semesterId:4,classId:7}]);
  await new Promise(resolve=>setTimeout(resolve,0));
  try {
    const link=dom.window.document.querySelector('.teacher-main-menu a[href="/dashboard#tutor-area"]');
    assert.ok(link);
    assert.equal(link.textContent,'Tutorenbereich');
    assert.equal(link.closest('li').previousElementSibling.querySelector('a').textContent,'Schülerfortschritt');
    assert.equal(dom.window.tutorAreaAccess,true);
  } finally { dom.window.close(); }
});

test('non-tutors do not receive a tutor navigation item',async()=>{
  const dom=shell('https://school.example.invalid/dashboard',`<nav class="teacher-main-menu">${navigation}</nav>`,[]);
  await new Promise(resolve=>setTimeout(resolve,0));
  try {
    assert.equal(dom.window.document.querySelector('a[href="/dashboard#tutor-area"]'),null);
    assert.equal(dom.window.tutorAreaAccess,false);
  } finally { dom.window.close(); }
});

test('teacher logout is the final menu item and keeps POST semantics',()=>{
  const dom=shell('https://school.example.invalid/dashboard',
    `<header><form action="/logout" method="post"><button type="submit">Ausloggen</button></form></header><nav class="teacher-main-menu">${navigation}</nav>`);
  try {
    assert.equal(dom.window.document.querySelector('body > header'),null);
    const items=dom.window.document.querySelectorAll('.teacher-main-menu-sections > ul > li, .teacher-main-menu > ul > li');
    const logout=items[items.length-1];
    assert.equal(logout.className,'teacher-main-menu-logout-item');
    assert.equal(logout.querySelector('form')?.getAttribute('method'),'post');
    assert.equal(logout.querySelector('button')?.textContent,'Ausloggen');
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

test('freistellung links are inserted as one deterministic block before logout',async()=>{
  const dom=new JSDOM(`<!doctype html><html><body><nav class="teacher-main-menu"><div class="teacher-main-menu-sections"><ul><li><a href="/dashboard#overview">Übersicht</a></li><li><a href="/dashboard#student-progress">Schülerfortschritt</a></li><li class="teacher-main-menu-logout-item"><button>Ausloggen</button></li></ul></div></nav>`,{
    url:'https://school.example.invalid/freistellung-bearbeitung',runScripts:'outside-only'
  });
  dom.window.fetch=async url=>url==='/freistellung-role-info'
    ? {ok:true,status:200,json:async()=>({apply:true,orga:true,sl:true})}
    : {ok:true,status:200,json:async()=>[]};
  dom.window.eval(navigationScript);
  dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
  await new Promise(resolve=>setTimeout(resolve,0));
  try {
    const items=[...dom.window.document.querySelectorAll('[data-freistellung-nav]')].filter(node=>node.matches('li'));
    assert.deepEqual(items.map(item=>item.querySelector('a')?.textContent),[
      'Freistellungen','Freistellungen – Orga-Leitung','Freistellungen – Schulleitung'
    ]);
    assert.equal(items.at(-1)?.nextElementSibling?.className,'teacher-main-menu-logout-item');
    assert.equal(dom.window.document.querySelectorAll('li[data-freistellung-nav]').length,3);
  } finally { dom.window.close(); }
});

test('all asynchronous teacher navigation additions keep one stable order',async()=>{
  const dom=new JSDOM(`<!doctype html><html><body><nav class="teacher-main-menu"><div class="teacher-main-menu-sections"><ul><li><a href="/dashboard#overview">Übersicht</a></li><li><a href="/dashboard#curriculum">Themen &amp; Etappen</a></li><li><a href="/dashboard#student-progress">Schülerfortschritt</a></li><li><a href="/attendance">Anwesenheit</a></li><li class="teacher-main-menu-logout-item"><button>Ausloggen</button></li></ul></div></nav></body></html>`,{
    url:'https://school.example.invalid/dashboard',runScripts:'outside-only'
  });
  dom.window.fetch=async url=>url==='/freistellung-role-info'
    ? {ok:true,status:200,json:async()=>({apply:true,orga:true,sl:true})}
    : {ok:true,status:200,json:async()=>[{semesterId:4,classId:7}]};
  dom.window.eval(navigationScript);
  dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
  await new Promise(resolve=>setTimeout(resolve,10));
  try {
    const links=[...dom.window.document.querySelectorAll('.teacher-main-menu a[href]')].map(link=>link.getAttribute('href'));
    assert.deepEqual(links,[
      '/dashboard#overview','/dashboard#curriculum','/dashboard#student-progress',
      '/dashboard#tutor-area','/dashboard#schoolwide-stage-overview',
      '/freistellung','/freistellung-bearbeitung','/freistellung-bearbeitung','/attendance'
    ]);
    assert.equal(dom.window.document.querySelectorAll('li[data-freistellung-nav]').length,3);
  } finally { dom.window.close(); }
});

test('attendance class multi-select becomes a collapsible checkbox picker kept in sync',async()=>{
  const dom=shell('https://school.example.invalid/attendance',
    `<main class="attendance"><nav>${navigation}</nav><form id="filters"><label>Datum<input id="date" type="date"></label><label>Klassen<select id="classes" multiple size="4"></select></label><label class="search">Schüler suchen<input id="search" type="search"></label><div class="actions"><button type="button" id="reset">Filter zurücksetzen</button></div></form><table><tbody id="rows"></tbody></table></main><p id="outside">x</p>`);
  const {document,Event}=dom.window;
  const tick=async()=>{ await Promise.resolve(); await new Promise(resolve=>dom.window.setTimeout(resolve,0)); };
  try {
    const select=document.getElementById('classes');
    assert.equal(select.hidden,true);
    assert.equal(select.form?.id,'filters');
    const picker=document.querySelector('.attendance-class-picker');
    assert.ok(picker);
    assert.equal(document.querySelector('#attendance-class-picker-value').textContent,'Alle Klassen');

    for (const name of ['5a','5b','5c','5d','6a']) select.append(new dom.window.Option(name,name));
    await tick();
    const boxes=picker.querySelectorAll('input[type="checkbox"]');
    assert.equal(boxes.length,5);

    let changes=0;
    select.addEventListener('change',()=>changes++);
    boxes[1].checked=true;
    boxes[1].dispatchEvent(new Event('change',{bubbles:true}));
    assert.equal(select.options[1].selected,true);
    assert.equal(changes,1);
    assert.equal(document.querySelector('#attendance-class-picker-value').textContent,'5b');

    select.options[1].selected=false;
    select.options[3].selected=true;
    document.getElementById('rows').append(document.createElement('tr'));
    await tick();
    assert.equal(boxes[1].checked,false);
    assert.equal(boxes[3].checked,true);
    assert.equal(document.querySelector('#attendance-class-picker-value').textContent,'5d');

    picker.open=true;
    document.getElementById('outside').dispatchEvent(new dom.window.MouseEvent('click',{bubbles:true}));
    assert.equal(picker.open,false);
  } finally { dom.window.close(); }
});
