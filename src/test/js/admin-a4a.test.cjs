const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {JSDOM}=require('jsdom');

const resources=path.join(__dirname,'../../main/resources');
const read=relative=>fs.readFileSync(path.join(resources,relative),'utf8');
const nav=read('templates/html/admin_main_menu.html');
const templates={
  students:read('html/admin/manage_students.html'),
  student:read('html/admin/student.html'),
  teachers:read('html/admin/manage_teachers.html'),
  teacher:read('html/admin/teacher.html'),
  classes:read('html/admin/manage_classes.html'),
  schoolClass:read('html/admin/class.html'),
  subjects:read('html/admin/manage_subjects.html'),
  subject:read('html/admin/subject.html'),
  plugins:read('html/admin/plugins.html'),
  editor:read('html/admin/editor.html'),
  dashboard:read('html/admin/dashboard.html')
};

function render(name) {
  return templates[name]
    .replace(/^%\[site;[^\]]+\]\s*/,'')
    .replaceAll('%[admin_nav]',nav)
    .replaceAll('%[admin_student_nav]','')
    .replaceAll('%[admin_class_nav]','')
    .replaceAll('%[admin_teacher_nav]','')
    .replaceAll('%[admin_subject_nav]','');
}

test('legacy admin pages share the sandstone shell and one primary navigation',()=>{
  for (const name of ['students','student','teachers','teacher','classes','schoolClass','subjects','subject','plugins','editor']) {
    const dom=new JSDOM(`<body>${render(name)}</body>`);
    try {
      const shell=dom.window.document.querySelector('.admin-shell');
      assert.ok(shell,`${name} has admin shell`);
      assert.equal(shell.querySelectorAll('.admin-main-menu').length,['students','teachers','classes','subjects'].includes(name)?1:0,`${name} navigation count`);
      assert.equal(dom.window.document.querySelectorAll('h1').length,1,`${name} has one page heading`);
    } finally { dom.window.close(); }
  }
});

test('the single admin navigation marks Schuldaten current on all canonical school-data routes',()=>{
  const navigationScript=read('js/admin/admin-section-navigation.js');
  for (const [name,pathName] of [['students','/manage_students'],['teachers','/manage_teachers'],['classes','/manage_classes'],['subjects','/manage_subjects']]) {
    const dom=new JSDOM(`<body>${render(name)}</body>`,{url:`https://school.invalid${pathName}`,runScripts:'outside-only'});
    try {
      dom.window.eval(navigationScript);
      assert.equal(dom.window.document.querySelector('.admin-main-menu a[aria-current="page"]')?.textContent.trim(),'Schuldaten',name);
      assert.equal(dom.window.document.querySelectorAll('.admin-main-menu a[aria-current="page"]').length,1,name);
    } finally { dom.window.close(); }
  }
});

test('admin detail pages provide one clear canonical return path',()=>{
  for (const [name,href] of [['student','/manage_students'],['teacher','/manage_teachers'],['schoolClass','/manage_classes'],['subject','/manage_subjects']]) {
    const dom=new JSDOM(`<body>${render(name)}</body>`);
    try {
      assert.ok(dom.window.document.querySelector(`.admin-detail-nav a[href="${href}"]`),name);
      assert.equal(dom.window.document.querySelectorAll('.admin-detail-nav').length,1,name);
    } finally { dom.window.close(); }
  }
});

test('admin-managed tables declare semantic column headers and local scroll containers',()=>{
  for (const name of ['students','teachers','classes','schoolClass','subjects']) {
    const dom=new JSDOM(`<body>${render(name)}</body>`);
    try {
      for (const table of dom.window.document.querySelectorAll('table')) {
        assert.ok(table.closest('.admin-table-scroll'),`${name} table scroll wrapper`);
        assert.ok(table.querySelector('caption'),`${name} table caption`);
        assert.ok([...table.querySelectorAll('thead th')].every(th=>th.getAttribute('scope')==='col'),`${name} column scope`);
      }
    } finally { dom.window.close(); }
  }
});

test('admin semester setup grids and assignment tables stay locally scrollable on mobile',()=>{
  const css=fs.readFileSync(path.join(__dirname,'../../main/resources/css/site/style.css'),'utf8');
  assert.match(css,/\.semester-landing\s*\{[^}]*minmax\(0,\s*1fr\)/);
  assert.match(css,/\.admin-table-scroll\s*\{[^}]*overflow-x:\s*auto/);
  assert.match(css,/\.admin-shell \.semester-landing > \*/);
});

test('admin status regions are announced and forms have real associated labels',()=>{
  for (const name of ['students','teachers','classes','schoolClass','student','teacher','subject']) {
    const dom=new JSDOM(`<body>${render(name)}</body>`);
    try {
      for (const status of dom.window.document.querySelectorAll('[role="status"]')) assert.equal(status.getAttribute('aria-live'),'polite',name);
      for (const label of dom.window.document.querySelectorAll('label[for]')) assert.ok(dom.window.document.getElementById(label.htmlFor),`${name} label ${label.htmlFor}`);
      for (const button of dom.window.document.querySelectorAll('button')) assert.ok(button.type,name);
    } finally { dom.window.close(); }
  }
});

test('admin templates have no inline script blocks or inline event handlers',()=>{
  for (const [name,html] of Object.entries(templates)) {
    assert.doesNotMatch(html,/<script\s*>/i,name);
    assert.doesNotMatch(html,/\son(click|change|submit)\s*=/i,name);
  }
});

test('admin static script routes are explicit admin-only file resources',()=>{
  const paths=JSON.parse(read('meta/paths/get_paths.json'));
  for (const route of ['/admin-section-navigation.js','/admin-plugins.js','/manage-teachers.js','/manage-classes.js']) {
    assert.deepEqual(paths[route],{
      type:'GET',handler_type:'FileRequestHandler',namespaces:['admin'],context:'js',access_level:'admin'
    });
  }
});

test('legacy admin dashboard entries are removed while the PM-owned navigation type remains supported',()=>{
  const elements=JSON.parse(read('meta/navigation/navigation_elements.json'));
  const types=JSON.parse(read('meta/navigation/navigation_types.json'));
  const templatesManifest=JSON.parse(read('meta/templates/templates.json'));
  assert.equal(elements.some(item=>item.type==='ADMIN_DASHBOARD'),false);
  assert.equal(elements.some(item=>item.type.startsWith('ADMIN_')),false);
  assert.equal(types.includes('ADMIN_DASHBOARD'),true);
  assert.deepEqual(Object.keys(templatesManifest).filter(key=>key.startsWith('admin_') && key.endsWith('_nav')),['admin_nav']);
  assert.equal(elements.filter(item=>item.type==='TEACHER_DASHBOARD').length,3);
});

test('admin-only shell classes do not leak into student and teacher shell selectors',()=>{
  const css=read('css/site/style.css');
  assert.match(css,/\.admin-shell \.admin-table-scroll/);
  assert.match(css,/\.teacher-dashboard\s*\{[^}]*color:/s);
  assert.doesNotMatch(css,/\.teacher-dashboard\s*,\s*\.admin-shell/);
});
