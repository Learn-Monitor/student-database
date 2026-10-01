const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM}=require('jsdom');

const resources=path.join(__dirname,'../../main/resources');
const dashboard=fs.readFileSync(path.join(resources,'html/admin/dashboard.html'),'utf8');
const nav=fs.readFileSync(path.join(resources,'templates/html/admin_main_menu.html'),'utf8');
const subjects=fs.readFileSync(path.join(resources,'html/admin/manage_subjects.html'),'utf8');
const subjectManagement=fs.readFileSync(path.join(resources,'js/admin/admin-subject-management.js'),'utf8');
const sectionNavigation=fs.readFileSync(path.join(resources,'js/admin/admin-section-navigation.js'),'utf8');
const css=fs.readFileSync(path.join(resources,'css/site/style.css'),'utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,25));

function dashboardHtml() {
  return dashboard
    .replace('%[site;title=Dashboard;content=!FOLLOWS]','')
    .replace('%[admin_nav]',nav);
}

function subjectsHtml() {
  return subjects.replace('%[site;title=Fächer verwalten;content=!FOLLOWS]','').replace('%[admin_nav]',nav);
}

test('adminDashboardShowsConfiguredSections',()=>{
  const dom=new JSDOM(`<!doctype html><body>${dashboardHtml()}</body>`);
  try {
    assert.deepEqual([...dom.window.document.querySelectorAll('.admin-function-page')].map(node=>node.id),[
      'uebersicht','schuldaten','schuljahr','curriculum-admin','anwesenheit','rechte','system'
    ]);
    assert.equal(dom.window.document.querySelectorAll('.admin-action-card').length,4);
    assert.match(dom.window.document.querySelector('#schuldaten').textContent,/Fächer/);
  } finally { dom.window.close(); }
});

test('adminNavigationActivatesRequestedSection',()=>{
  const dom=new JSDOM(`<!doctype html><body>${dashboardHtml()}</body>`,{url:'https://school.invalid/dashboard#schuldaten',runScripts:'outside-only'});
  try {
    dom.window.eval(sectionNavigation);
    assert.equal(dom.window.document.querySelector('#schuldaten').hidden,false);
    assert.equal(dom.window.document.querySelector('#uebersicht').hidden,true);
  } finally { dom.window.close(); }
});

test('adminNavigationMarksCurrentSection',()=>{
  const dom=new JSDOM(`<!doctype html><body>${dashboardHtml()}</body>`,{url:'https://school.invalid/dashboard#curriculum',runScripts:'outside-only'});
  try {
    dom.window.eval(sectionNavigation);
    const current=dom.window.document.querySelector('.admin-main-menu a[aria-current="page"]');
    assert.equal(current?.textContent.trim(),'Zentrales Curriculum');
  } finally { dom.window.close(); }
});

test('adminDashboardNavigationHasNoInlineImplementation',()=>{
  assert.match(dashboard,/admin-section-navigation\.js/);
  assert.doesNotMatch(dashboard,/<script>\s*\(\(\)\s*=>/);
});

test('adminDashboardDoesNotExposeDuplicatePrimaryNavigation',()=>{
  const dom=new JSDOM(`<!doctype html><body>${dashboardHtml()}</body>`);
  try {
    const links=[...dom.window.document.querySelectorAll('.admin-main-menu-sections a')];
    assert.deepEqual(links.map(link=>link.textContent.trim()),[
      'Übersicht','Schuldaten','Schuljahr & Zuordnungen','Zentrales Curriculum','Anwesenheit','Rechte','System'
    ]);
    assert.equal(new Set(links.map(link=>link.getAttribute('href'))).size,links.length);
    assert.equal(dom.window.document.querySelectorAll('.admin-main-menu-logout').length,1);
  } finally { dom.window.close(); }
});

test('adminShellUsesScopedSandstonePaletteAndResponsiveStructure',()=>{
  assert.match(css,/\.admin-shell\s*\{[\s\S]*--admin-sandstone-950/);
  assert.match(css,/\.admin-shell \.admin-main-menu/);
  assert.match(css,/@media \(max-width:700px\)[\s\S]*\.admin-shell/);
  assert.doesNotMatch(css,/\.teacher-dashboard\s*\{[\s\S]*--admin-sandstone/);
});

async function setupSubjects(data) {
  const dom=new JSDOM(`<!doctype html><body>${subjectsHtml()}</body>`,{
    url:'https://school.invalid/manage_subjects',runScripts:'outside-only'
  });
  const requests=[];
  dom.window.fetch=async (url,options={})=>{
    requests.push({url,options,data:options.body ? JSON.parse(options.body) : null});
    return {ok:true,status:200,json:async()=>data};
  };
  dom.window.eval(subjectManagement);
  dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
  await tick();
  return {dom,requests};
}

test('subjectManagementRendersEachSubjectOnce',async()=>{
  const {dom}=await setupSubjects({subjects:[
    {id:1,name:'Mathematik',mode:'REGULAR',assignmentGroup:null},
    {id:2,name:'WPF Technik',mode:'INDIVIDUAL',assignmentGroup:'WPF'},
    {id:3,name:'Ethik',mode:'INDIVIDUAL',assignmentGroup:'RELIGION_ETHIK'}
  ]});
  try {
    assert.equal(dom.window.document.querySelectorAll('#subject-list-body tr').length,3);
    assert.equal(dom.window.document.querySelectorAll('.admin-subject-edit-link').length,3);
    assert.equal(dom.window.document.querySelector('#subjectTypes'),null);
    assert.equal(dom.window.document.querySelectorAll('#subjectList').length,1);
  } finally { dom.window.close(); }
});

test('subjectManagementShowsRegularWpfAndReligionEthikTypes',async()=>{
  const {dom}=await setupSubjects({subjects:[
    {id:1,name:'Mathematik',mode:'REGULAR'},
    {id:2,name:'WPF Technik',mode:'INDIVIDUAL',assignmentGroup:'WPF'},
    {id:3,name:'Ethik',mode:'INDIVIDUAL',assignmentGroup:'RELIGION_ETHIK'}
  ]});
  try {
    const selects=[...dom.window.document.querySelectorAll('#subject-list-body select')];
    assert.deepEqual(selects.map(select=>select.value),['REGULAR:','INDIVIDUAL:WPF','INDIVIDUAL:RELIGION_ETHIK']);
    assert.match(dom.window.document.body.textContent,/Religion\/Ethik/);
  } finally { dom.window.close(); }
});

test('subjectRowsUseAccessibleEditControls',async()=>{
  const {dom}=await setupSubjects({subjects:[{id:9,name:'Deutsch',mode:'REGULAR'}]});
  try {
    const edit=dom.window.document.querySelector('.admin-subject-edit-link');
    assert.equal(edit.tagName,'A');
    assert.equal(edit.getAttribute('href'),'/subject?subjectId=9');
    assert.equal(dom.window.document.querySelector('#subject-list-body li'),null);
    assert.ok(dom.window.document.querySelector('#subject-list-body select[aria-label]'));
  } finally { dom.window.close(); }
});

test('subjectTypeUpdateUsesExistingCanonicalEndpoint',async()=>{
  const {dom,requests}=await setupSubjects({subjects:[{id:9,name:'Deutsch',mode:'REGULAR'}]});
  try {
    const select=dom.window.document.querySelector('#subject-list-body select');
    select.value='INDIVIDUAL:WPF';
    dom.window.document.querySelector('#subject-list-body button').click();
    await tick();
    const request=requests.find(item=>item.url==='/set-curriculum-subject-type');
    assert.deepEqual(request.data,{subjectId:9,mode:'INDIVIDUAL',assignmentGroup:'WPF'});
    assert.match(dom.window.document.body.textContent,/Gespeichert/);
  } finally { dom.window.close(); }
});

test('subjectCreateUsesAtomicNameAndTypeEndpoint',async()=>{
  const {dom,requests}=await setupSubjects({subjects:[]});
  try {
    dom.window.document.querySelector('#subject-name').value='WPF Technik';
    dom.window.document.querySelector('#create-subject-type').value='INDIVIDUAL:WPF';
    dom.window.document.querySelector('#manage-subjects-form').dispatchEvent(new dom.window.Event('submit',{bubbles:true,cancelable:true}));
    await tick();
    const request=requests.find(item=>item.url==='/add-subject-with-type');
    assert.deepEqual(request.data,{name:'WPF Technik',mode:'INDIVIDUAL',assignmentGroup:'WPF'});
    assert.match(dom.window.document.body.textContent,/Fach wurde angelegt/);
  } finally { dom.window.close(); }
});
