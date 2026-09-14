const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM}=require('jsdom');

const dashboard=fs.readFileSync(path.join(__dirname,'../../main/resources/html/admin/dashboard.html'),'utf8');
const nav=fs.readFileSync(path.join(__dirname,'../../main/resources/templates/html/admin_main_menu.html'),'utf8');

function html() {
  const body=dashboard
    .replace('%[site;title=Dashboard;content=!FOLLOWS]','')
    .replace('%[admin_nav]',nav)
    .replace('<script src="/curriculum.js" defer></script>','');
  return `<!doctype html><html><body>${body}</body></html>`;
}

function setup(hash='') {
  const dom=new JSDOM(html(),{url:`https://school.example.invalid/dashboard${hash}`,runScripts:'dangerously'});
  return dom;
}

function visibleIds(document) {
  return [...document.querySelectorAll('.admin-function-page')]
    .filter(section=>!section.hidden)
    .map(section=>section.id);
}

function currentText(document) {
  const current=document.querySelector('.admin-main-menu a[aria-current="true"]');
  return current ? current.textContent.trim() : '';
}

for (const [hash,section,label,curriculumVisible] of [
  ['', 'uebersicht', 'Übersicht', false],
  ['#schuldaten', 'schuldaten', 'Schuldaten', false],
  ['#schuljahr', 'schuljahr', 'Schuljahr & Zuordnungen', true],
  ['#curriculum', 'curriculum-admin', 'Zentrales Curriculum', true],
  ['#anwesenheit', 'anwesenheit', 'Anwesenheit', false],
  ['#rechte', 'rechte', 'Rechte', false],
  ['#system', 'system', 'System', false]
]) test(`admin dashboard hash ${hash || 'default'} opens ${section}`,()=>{
  const dom=setup(hash);
  try {
    assert.deepEqual(visibleIds(dom.window.document),[section]);
    assert.equal(currentText(dom.window.document),label);
    assert.equal(dom.window.document.querySelector('#curriculum').hidden,!curriculumVisible);
  } finally {
    dom.window.close();
  }
});

test('legacy halbjahre hash aliases to schuljahr',()=>{
  const dom=setup('#halbjahre');
  try {
    assert.deepEqual(visibleIds(dom.window.document),['schuljahr']);
    assert.equal(currentText(dom.window.document),'Schuljahr & Zuordnungen');
    assert.equal(dom.window.location.hash,'#schuljahr');
  } finally {
    dom.window.close();
  }
});

test('logout is visually separated and not a normal workspace',()=>{
  const dom=setup();
  try {
    const logout=dom.window.document.querySelector('.admin-main-menu-logout');
    assert.equal(logout?.getAttribute('href'),'/logout');
    assert.equal(logout?.hasAttribute('aria-current'),false);
    assert.equal([...dom.window.document.querySelectorAll('.admin-main-menu-sections a')].some(a=>a.getAttribute('href')==='/logout'),false);
  } finally {
    dom.window.close();
  }
});
