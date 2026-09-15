const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM,VirtualConsole}=require('jsdom');

const manageClassesHtml=fs.readFileSync(path.join(__dirname,'../../main/resources/html/admin/manage_classes.html'),'utf8');
const classHtml=fs.readFileSync(path.join(__dirname,'../../main/resources/html/admin/class.html'),'utf8');
const classScript=fs.readFileSync(path.join(__dirname,'../../main/resources/js/admin/build_class.js'),'utf8');
const tick=()=>new Promise(resolve=>setTimeout(resolve,20));

function stripTemplate(html) {
  return html
    .replace('%[site;title=Klassen verwalten;content=!FOLLOWS]','')
    .replace('%[site;title=Klasse bearbeiten;content=!FOLLOWS]','')
    .replace('%[admin_nav]','')
    .replace('%[admin_class_nav]','')
    .replace('<script src="build_class.js"></script>','');
}

function setupClassList(options={}) {
  const calls={viewed:[],posts:[],alerts:[],confirms:[]};
  let innerHTMLWrites=0;
  const virtualConsole=new VirtualConsole();
  virtualConsole.on('jsdomError',()=>{});
  const dom=new JSDOM(`<!doctype html><html><body>${stripTemplate(manageClassesHtml)}</body></html>`,{
    url:'https://school.example.invalid/manage_classes',
    runScripts:'dangerously',
    virtualConsole,
    beforeParse(window) {
      if (options.trackInnerHTML) {
        const descriptor=Object.getOwnPropertyDescriptor(window.Element.prototype,'innerHTML');
        Object.defineProperty(window.Element.prototype,'innerHTML',{
          get: descriptor.get,
          set(value) {
            innerHTMLWrites += 1;
            return descriptor.set.call(this,value);
          }
        });
      }
      window.fetchJson=async url=>{
        assert.equal(url,'/classes');
        return options.classes || [
          {id:12,label:'7a <strong>',grade:7},
          {id:13,label:'8b',grade:8}
        ];
      };
      window.viewClass=cls=>calls.viewed.push(cls);
      window.post=async (url,data)=>{
        calls.posts.push({url,data});
        return {ok:options.postOk!==false};
      };
      window.alert=message=>calls.alerts.push(message);
      window.confirm=message=>{
        calls.confirms.push(message);
        return options.confirm!==false;
      };
    }
  });
  return tick().then(()=>({dom,calls,innerHTMLWrites:()=>innerHTMLWrites}));
}

function setupClassDetail(options={}) {
  const dom=new JSDOM(`<!doctype html><html><body>${stripTemplate(classHtml)}</body></html>`,{
    url:'https://school.example.invalid/class',
    runScripts:'outside-only',
    virtualConsole:new VirtualConsole()
  });
  const calls={students:[],viewedStudents:[],deleted:[],alerts:[],confirms:[]};
  let innerHTMLWrites=0;
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
  dom.window.sessionStorage.setItem('currentClass',JSON.stringify(options.cls || {id:42,label:'7a',grade:7}));
  dom.window.graduationLevels=['Neustarter','Starter','Durchstarter','Lernprofi'];
  dom.window.populateStudentTable=async (classId, tableId, rowBuilder)=>{
    calls.students.push({classId,tableId});
    const tableBody=dom.window.document.getElementById(tableId).getElementsByTagName('tbody')[0];
    tableBody.replaceChildren();
    for (const student of options.students || [{id:5,name:'Ada <b>',graduationLevel:2,room:'A1'}]) {
      const row=dom.window.document.createElement('tr');
      rowBuilder(row,student);
      tableBody.appendChild(row);
    }
  };
  dom.window.viewStudent=id=>calls.viewedStudents.push(id);
  dom.window.deleteClass=async id=>{
    calls.deleted.push(id);
    return {ok:options.deleteOk!==false};
  };
  dom.window.alert=message=>calls.alerts.push(message);
  dom.window.confirm=message=>{
    calls.confirms.push(message);
    return options.confirm!==false;
  };
  dom.window.eval(classScript);
  dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
  return tick().then(()=>({dom,calls,innerHTMLWrites:()=>innerHTMLWrites}));
}

test('class list shows name grade and actions columns',async()=>{
  const {dom}=await setupClassList();
  try {
    const headers=[...dom.window.document.querySelectorAll('#classTable th')].map(th=>th.textContent.trim());
    assert.deepEqual(headers,['Name','Stufe','Aktionen']);
  } finally {
    dom.window.close();
  }
});

test('class list no longer shows id as a visible table column',async()=>{
  const {dom}=await setupClassList();
  try {
    assert.equal(dom.window.document.querySelector('#classTable th')?.textContent.trim(),'Name');
    assert.equal(dom.window.document.querySelector('#classTable tbody tr')?.children.length,3);
    assert.equal(dom.window.document.querySelector('#classTable tbody tr')?.textContent.includes('12'),false);
  } finally {
    dom.window.close();
  }
});

test('class list does not render server data through innerHTML',async()=>{
  const {dom,innerHTMLWrites}=await setupClassList({trackInnerHTML:true});
  try {
    const table=dom.window.document.querySelector('#classTable');
    assert.equal(innerHTMLWrites(),0);
    assert.equal(table.querySelectorAll('strong').length,0);
    assert.match(table.textContent,/7a <strong>/);
  } finally {
    dom.window.close();
  }
});

test('class list edit button calls viewClass with the class object',async()=>{
  const {dom,calls}=await setupClassList();
  try {
    dom.window.document.querySelector('#classTable .edit').click();
    assert.deepEqual(calls.viewed,[{id:12,label:'7a <strong>',grade:7}]);
  } finally {
    dom.window.close();
  }
});

test('class list archive uses German archive confirmation',async()=>{
  const {dom,calls}=await setupClassList();
  try {
    [...dom.window.document.querySelectorAll('#classTable button')].find(button=>button.textContent==='Archivieren').click();
    await tick();
    assert.equal(calls.confirms[0],'Klasse wirklich archivieren? Die Klasse wird deaktiviert. Die Schülerinnen und Schüler bleiben erhalten und werden der Klasse „Nicht zugeordnet“ zugewiesen.');
    assert.equal(calls.alerts[0],'Klasse wurde archiviert.');
  } finally {
    dom.window.close();
  }
});

test('class list archive still posts to delete-class',async()=>{
  const {dom,calls}=await setupClassList();
  try {
    [...dom.window.document.querySelectorAll('#classTable button')].find(button=>button.textContent==='Archivieren').click();
    await tick();
    assert.equal(calls.posts[0].url,'/delete-class');
    assert.deepEqual({...calls.posts[0].data},{id:12});
  } finally {
    dom.window.close();
  }
});

test('class detail says class archive instead of class delete',()=>{
  const dom=new JSDOM(`<!doctype html><html><body>${stripTemplate(classHtml)}</body></html>`);
  try {
    assert.match(dom.window.document.body.textContent,/Klasse archivieren/);
    assert.doesNotMatch(dom.window.document.body.textContent,/Klasse löschen/);
  } finally {
    dom.window.close();
  }
});

test('class detail no longer contains the old subject list',()=>{
  const dom=new JSDOM(`<!doctype html><html><body>${stripTemplate(classHtml)}</body></html>`);
  try {
    assert.equal(dom.window.document.querySelector('#subjectList'),null);
    assert.doesNotMatch(dom.window.document.body.textContent,/Fächer der Klasse|Fach hinzufügen/);
  } finally {
    dom.window.close();
  }
});

test('class detail no longer contains addSubjectForm',()=>{
  const dom=new JSDOM(`<!doctype html><html><body>${stripTemplate(classHtml)}</body></html>`);
  try {
    assert.equal(dom.window.document.querySelector('#addSubjectForm'),null);
    assert.equal(classHtml.includes('/add-subject-to-class'),false);
    assert.equal(dom.window.document.querySelector('#subjectSelect'),null);
  } finally {
    dom.window.close();
  }
});

test('class detail links to school year assignments',()=>{
  const dom=new JSDOM(`<!doctype html><html><body>${stripTemplate(classHtml)}</body></html>`);
  try {
    const link=dom.window.document.querySelector('a[href="/dashboard#schuljahr"]');
    assert.equal(link?.textContent.trim(),'Schuljahr & Zuordnungen');
  } finally {
    dom.window.close();
  }
});

test('build_class no longer calls populateSubjectList',()=>{
  assert.doesNotMatch(classScript,/populateSubjectList/);
});

test('build_class no longer calls populateSubjectSelect',()=>{
  assert.doesNotMatch(classScript,/populateSubjectSelect/);
});

test('student table has exactly three cells per data row',async()=>{
  const {dom}=await setupClassDetail();
  try {
    const row=dom.window.document.querySelector('#studentTableBody tr');
    assert.equal(row.children.length,3);
  } finally {
    dom.window.close();
  }
});

test('student table columns are name graduation level and actions',async()=>{
  const {dom}=await setupClassDetail();
  try {
    const headers=[...dom.window.document.querySelectorAll('#studentTable th')].map(th=>th.textContent.trim());
    assert.deepEqual(headers,['Name','Abschlussstufe','Aktionen']);
    const cells=[...dom.window.document.querySelectorAll('#studentTableBody tr:first-child td')].map(td=>td.textContent.trim());
    assert.deepEqual(cells,['Ada <b>','Durchstarter','Bearbeiten']);
  } finally {
    dom.window.close();
  }
});

test('student data is rendered safely without innerHTML',async()=>{
  const {dom,innerHTMLWrites}=await setupClassDetail({trackInnerHTML:true});
  try {
    const table=dom.window.document.querySelector('#studentTable');
    assert.equal(innerHTMLWrites(),0);
    assert.equal(table.querySelectorAll('b').length,0);
    assert.match(table.textContent,/Ada <b>/);
  } finally {
    dom.window.close();
  }
});

test('student edit button still opens the selected student',async()=>{
  const {dom,calls}=await setupClassDetail();
  try {
    dom.window.document.querySelector('#studentTable .edit-student').click();
    assert.deepEqual(calls.viewedStudents,[5]);
  } finally {
    dom.window.close();
  }
});

test('system class zero cannot be edited or archived',async()=>{
  const {dom,calls}=await setupClassDetail({cls:{id:0,label:'Nicht zugeordnet',grade:0}});
  try {
    assert.equal(dom.window.document.querySelector('#systemClassNotice').hidden,false);
    assert.equal(dom.window.document.querySelector('#className').disabled,true);
    assert.equal(dom.window.document.querySelector('#classGrade').disabled,true);
    assert.equal(dom.window.document.querySelector('#saveClassButton').disabled,true);
    assert.equal(dom.window.document.querySelector('#deleteClassButton').disabled,true);
    dom.window.document.querySelector('#deleteClassButton').click();
    await tick();
    assert.deepEqual(calls.deleted,[]);
    assert.match(dom.window.document.body.textContent,/„Nicht zugeordnet“ ist eine Systemklasse und kann nicht bearbeitet werden\./);
  } finally {
    dom.window.close();
  }
});
