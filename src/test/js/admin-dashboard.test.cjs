const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM}=require('jsdom');

const dashboard=fs.readFileSync(path.join(__dirname,'../../main/resources/html/admin/dashboard.html'),'utf8');
const nav=fs.readFileSync(path.join(__dirname,'../../main/resources/templates/html/admin_main_menu.html'),'utf8');
const overviewScript=fs.readFileSync(path.join(__dirname,'../../main/resources/js/admin/admin-dashboard.js'),'utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,20));

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

async function setupOverview(options={}) {
  const dom=setup('#uebersicht');
  const defaults = {
    '/students': [
      {id:1, firstName:'Ada', classId:5},
      {id:2, firstName:'Ben', class:{id:0}},
      {id:3, firstName:'Cem', schoolClass:{id:6}}
    ],
    '/teachers': [{id:1}, {id:2}],
    '/classes': [{id:0, active:true}, {id:5, active:true}, {id:6, active:true}, {id:7, active:false}],
    '/subjects': [{id:1}, {id:2}, {id:3}],
    '/curriculum-enrollment-catalog': {semesters:[{id:20, label:'HJ1', yearLabel:'2026/27', active:true}]}
  };
  const data = {...defaults, ...(options.data || {})};
  const failures = new Set(options.failures || []);
  let innerHTMLWrites = 0;
  if (options.trackInnerHTML) {
    const descriptor = Object.getOwnPropertyDescriptor(dom.window.Element.prototype, 'innerHTML');
    Object.defineProperty(dom.window.Element.prototype, 'innerHTML', {
      get: descriptor.get,
      set(value) { innerHTMLWrites += 1; return descriptor.set.call(this, value); }
    });
  }
  dom.window.fetch = async (url, requestOptions={}) => {
    if (failures.has(url)) return {ok:false, status:500, json:async()=>({error:'failed'})};
    if (!(url in data)) throw Error(`Unexpected endpoint ${url}`);
    return {ok:true, status:200, json:async()=>data[url], requestOptions};
  };
  dom.window.eval(overviewScript);
  dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
  await tick();
  return {dom, innerHTMLWrites:()=>innerHTMLWrites};
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

for (const [hash,section,label] of [
  ['', 'uebersicht', 'Übersicht'],
  ['#schuldaten', 'schuldaten', 'Schuldaten'],
  ['#schuljahr', 'schuljahr', 'Schuljahr & Zuordnungen'],
  ['#curriculum', 'curriculum-admin', 'Zentrales Curriculum'],
  ['#anwesenheit', 'anwesenheit', 'Anwesenheit'],
  ['#rechte', 'rechte', 'Rechte'],
  ['#system', 'system', 'System']
]) test(`admin dashboard hash ${hash || 'default'} opens ${section}`,()=>{
  const dom=setup(hash);
  try {
    assert.deepEqual(visibleIds(dom.window.document),[section]);
    assert.equal(currentText(dom.window.document),label);
  } finally {
    dom.window.close();
  }
});

test('admin dashboard uses distinct enrollment and central curriculum mountpoints',()=>{
  const dom=setup('#curriculum');
  try {
    assert.ok(dom.window.document.querySelector('#admin-enrollment'));
    assert.ok(dom.window.document.querySelector('#admin-central-curriculum'));
    assert.notEqual(dom.window.document.querySelector('#admin-enrollment'),dom.window.document.querySelector('#admin-central-curriculum'));
    assert.equal(dom.window.document.querySelectorAll('#curriculum').length,0);
  } finally {
    dom.window.close();
  }
});

test('admin dashboard switches schuljahr and curriculum hashes without reload',()=>{
  const dom=setup('#schuljahr');
  try {
    assert.deepEqual(visibleIds(dom.window.document),['schuljahr']);
    dom.window.location.hash='#curriculum';
    dom.window.dispatchEvent(new dom.window.Event('hashchange'));
    assert.deepEqual(visibleIds(dom.window.document),['curriculum-admin']);
    assert.equal(currentText(dom.window.document),'Zentrales Curriculum');
    dom.window.location.hash='#schuljahr';
    dom.window.dispatchEvent(new dom.window.Event('hashchange'));
    assert.deepEqual(visibleIds(dom.window.document),['schuljahr']);
    assert.equal(currentText(dom.window.document),'Schuljahr & Zuordnungen');
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

test('overview creates five status cards with active semester',async()=>{
  const {dom}=await setupOverview();
  try {
    const cards=[...dom.window.document.querySelectorAll('.admin-status-card')];
    assert.equal(cards.length,5);
    assert.match(cards[0].textContent,/Aktives Halbjahr/);
    assert.match(cards[0].textContent,/2026\/27 HJ1/);
  } finally {
    dom.window.close();
  }
});

test('overview warns when no active semester is configured',async()=>{
  const {dom}=await setupOverview({data:{'/curriculum-enrollment-catalog':{semesters:[{id:20,label:'HJ1',active:false}]}}});
  try {
    const card=dom.window.document.querySelector('.admin-status-card');
    assert.match(card.textContent,/Nicht festgelegt/);
    assert.equal(card.classList.contains('admin-status-warning'),true);
  } finally {
    dom.window.close();
  }
});

test('overview counts students and class zero assignments',async()=>{
  const {dom}=await setupOverview();
  try {
    const studentCard=[...dom.window.document.querySelectorAll('.admin-status-card')].find(card=>card.textContent.includes('Schüler'));
    assert.match(studentCard.textContent,/3/);
    assert.match(studentCard.textContent,/1 nicht zugeordnet/);
  } finally {
    dom.window.close();
  }
});

test('overview shows all students assigned when none are in class zero',async()=>{
  const {dom}=await setupOverview({data:{'/students':[{id:1,classId:5},{id:2,schoolClass:{id:6}}]}});
  try {
    const studentCard=[...dom.window.document.querySelectorAll('.admin-status-card')].find(card=>card.textContent.includes('Schüler'));
    assert.match(studentCard.textContent,/2/);
    assert.match(studentCard.textContent,/Alle zugeordnet/);
  } finally {
    dom.window.close();
  }
});

test('overview counts teachers, active classes without class zero and subjects',async()=>{
  const {dom}=await setupOverview();
  try {
    const cards=[...dom.window.document.querySelectorAll('.admin-status-card')];
    assert.match(cards.find(card=>card.textContent.includes('Lehrkräfte')).textContent,/2/);
    assert.match(cards.find(card=>card.textContent.includes('Klassen')).textContent,/2/);
    assert.match(cards.find(card=>card.textContent.includes('Fächer')).textContent,/3/);
  } finally {
    dom.window.close();
  }
});

test('overview renders setup status from loaded data',async()=>{
  const {dom}=await setupOverview();
  try {
    const items=[...dom.window.document.querySelectorAll('.admin-setup-status li')].map(item=>item.textContent.trim());
    assert.deepEqual(items,[
      '✓ erledigt Halbjahr angelegt',
      '✓ erledigt Aktives Halbjahr festgelegt',
      '✓ erledigt Klassen vorhanden',
      '✓ erledigt Fächer vorhanden'
    ]);
  } finally {
    dom.window.close();
  }
});

test('overview quick links point to existing dashboard hashes',async()=>{
  const {dom}=await setupOverview();
  try {
    const links=[...dom.window.document.querySelectorAll('.admin-quick-links a')].map(link=>[link.textContent, link.getAttribute('href')]);
    assert.deepEqual(links,[
      ['Schuldaten verwalten','/dashboard#schuldaten'],
      ['Schuljahr & Zuordnungen','/dashboard#schuljahr'],
      ['Zentrales Curriculum','/dashboard#curriculum']
    ]);
  } finally {
    dom.window.close();
  }
});

test('overview keeps remaining cards when a single api fails',async()=>{
  const {dom}=await setupOverview({failures:['/teachers']});
  try {
    const text=dom.window.document.querySelector('#uebersicht').textContent;
    assert.match(text,/Nicht verfügbar/);
    assert.match(text,/Einige Verwaltungsdaten konnten nicht geladen werden/);
    assert.match(text,/Schüler/);
    assert.match(text,/Fächer/);
  } finally {
    dom.window.close();
  }
});

test('overview does not render server data through innerHTML',async()=>{
  const {dom,innerHTMLWrites}=await setupOverview({
    trackInnerHTML:true,
    data:{'/curriculum-enrollment-catalog':{semesters:[{id:20,label:'<img src=x>',yearLabel:'2026/27',active:true}]}}
  });
  try {
    const root=dom.window.document.querySelector('#uebersicht');
    assert.equal(root.querySelectorAll('img').length,0);
    assert.equal(innerHTMLWrites(),0);
    assert.match(root.textContent,/<img src=x>/);
  } finally {
    dom.window.close();
  }
});
