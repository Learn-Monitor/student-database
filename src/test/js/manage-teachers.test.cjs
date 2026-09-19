const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM,VirtualConsole}=require('jsdom');

const html=fs.readFileSync(path.join(__dirname,'../../main/resources/html/admin/manage_teachers.html'),'utf8');
const dashboardHtml=fs.readFileSync(path.join(__dirname,'../../main/resources/html/admin/dashboard.html'),'utf8');
const navigation=fs.readFileSync(path.join(__dirname,'../../main/resources/meta/navigation/navigation_elements.json'),'utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,20));

const teachers=[
  {id:3,firstName:'Zoë',lastName:'Ärger',email:'zoe@example.test'},
  {id:1,firstName:'Berta',lastName:'Albrecht',email:'berta@example.test'},
  {id:2,firstName:'Ada',lastName:'Albrecht',email:'ada.login@example.test'}
];

function stripTemplate(value) {
  return value
    .replace('%[site;title=Lehrkräfte verwalten;content=!FOLLOWS]','')
    .replace('%[admin_nav]','');
}

function inlineScript(value) {
  const match=value.match(/<script>([\s\S]*)<\/script>\s*$/);
  assert.ok(match);
  return match[1];
}

async function setup(options={}) {
  const calls={fetchJson:[],downloads:[],viewed:[]};
  let innerHTMLWrites=0;
  const virtualConsole=new VirtualConsole();
  virtualConsole.on('jsdomError',()=>{});
  const dom=new JSDOM(`<!doctype html><html><body>${stripTemplate(html).replace(/<script>[\s\S]*<\/script>\s*$/,'')}</body></html>`,{
    url:'https://school.example.invalid/manage_teachers',
    runScripts:'outside-only',
    virtualConsole
  });
  if (options.trackInnerHTML) {
    const descriptor=Object.getOwnPropertyDescriptor(dom.window.Element.prototype,'innerHTML');
    Object.defineProperty(dom.window.Element.prototype,'innerHTML',{
      get: descriptor.get,
      set(value) {
        innerHTMLWrites += 1;
        return descriptor.set.call(this,value);
      }
    });
  }
  dom.window.fetchJson=async url=>{
    calls.fetchJson.push(url);
    assert.equal(url,'/teachers');
    return options.teachers || teachers;
  };
  dom.window.postDataAndDownload=(url,data,filename)=>calls.downloads.push({url,data,filename});
  dom.window.viewTeacher=teacher=>calls.viewed.push(teacher);
  dom.window.eval(inlineScript(html));
  dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
  await tick();
  return {dom,calls,innerHTMLWrites:()=>innerHTMLWrites};
}

function rowTexts(document) {
  return [...document.querySelectorAll('#teacherTableBody tr')]
    .map(row=>[...row.children].map(cell=>cell.textContent.trim()));
}

test('teacher list uses Lehrkräfte terminology and keeps existing forms',async()=>{
  const {dom,calls}=await setup();
  try {
    const text=dom.window.document.body.textContent;
    assert.match(text,/Lehrkräfte verwalten/);
    assert.match(text,/Lehrkraft hinzufügen/);
    assert.match(text,/Lehrkräfte aus CSV hinzufügen/);
    assert.match(text,/Lehrkräfte-Liste/);
    assert.doesNotMatch(text,/Lehrer verwalten|Lehrer hinzufügen|Lehrer-Liste/);
    assert.equal(dom.window.document.querySelector('#add-teacher-form').getAttribute('action'),'/add-teacher');
    assert.equal(dom.window.document.querySelector('#add-teachers-csv-form').getAttribute('action'),'/add-teachers');
    assert.equal(dom.window.document.querySelector('#download-teachers').closest('a').getAttribute('href'),'/teachers');
    dom.window.document.querySelector('#csv-input').value='csv-data';
    dom.window.document.querySelector('#add-teachers-csv-form').dispatchEvent(new dom.window.Event('submit',{bubbles:true,cancelable:true}));
    assert.deepEqual(calls.downloads[0],{url:'/add-teachers',data:'csv-data',filename:'lehrer.csv'});
  } finally {
    dom.window.close();
  }
});

test('teacher table starts with Nachname before Vorname and sorts by last then first',async()=>{
  const {dom}=await setup();
  try {
    assert.deepEqual(
      [...dom.window.document.querySelectorAll('#teacherTable th')].map(th=>th.textContent.trim()),
      ['Nachname','Vorname','Loginname','Aktionen']
    );
    assert.deepEqual(rowTexts(dom.window.document).map(cells=>cells.slice(0,2)),[
      ['Albrecht','Ada'],
      ['Albrecht','Berta'],
      ['Ärger','Zoë']
    ]);
  } finally {
    dom.window.close();
  }
});

test('teacher rows are rendered safely without innerHTML or inline onclick',async()=>{
  const {dom,innerHTMLWrites}=await setup({trackInnerHTML:true,teachers:[
    {id:9,firstName:'<img src=x onerror=alert(1)>',lastName:'<b>Unsafe</b>',email:'unsafe@example.test'}
  ]});
  try {
    assert.equal(innerHTMLWrites(),0);
    assert.equal(dom.window.document.querySelector('#teacherTableBody tr').innerHTML.includes('<img'),false);
    assert.equal(dom.window.document.querySelector('[onclick]'),null);
    assert.doesNotMatch(html,/onclick=/i);
    assert.doesNotMatch(inlineScript(html),/innerHTML\s*=/);
  } finally {
    dom.window.close();
  }
});

test('edit button calls viewTeacher with the real teacher object',async()=>{
  const {dom,calls}=await setup();
  try {
    dom.window.document.querySelector('#teacherTableBody tr .view-teacher').click();
    assert.equal(calls.viewed.length,1);
    assert.equal(calls.viewed[0],teachers[2]);
  } finally {
    dom.window.close();
  }
});

test('teacher filter uses German case handling for name or login',async()=>{
  const {dom}=await setup();
  try {
    const filter=dom.window.document.getElementById('teacherFilter');
    assert.equal(filter.getAttribute('placeholder'),'Name oder Loginname filtern');
    filter.value='ärger';
    filter.dispatchEvent(new dom.window.Event('input',{bubbles:true}));
    assert.deepEqual(rowTexts(dom.window.document).map(cells=>cells[0]),['Ärger']);
    filter.value='ADA.LOGIN';
    filter.dispatchEvent(new dom.window.Event('input',{bubbles:true}));
    assert.deepEqual(rowTexts(dom.window.document).map(cells=>cells[1]),['Ada']);
  } finally {
    dom.window.close();
  }
});

test('admin dashboard teacher labels use Lehrkräfte only for manage teachers link',()=>{
  assert.match(dashboardHtml,/manage_teachers">Lehrkräfte verwalten/);
  const items=JSON.parse(navigation);
  const dashboardTeacher=items.find(item=>item.type==='ADMIN_DASHBOARD' && item.path==='/manage_teachers');
  assert.equal(dashboardTeacher.label,'Lehrkräfte verwalten');
});
