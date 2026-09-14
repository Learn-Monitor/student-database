(() => {
  const text = (tag, value, className) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    node.textContent = value;
    return node;
  };
  const asList = value => Array.isArray(value) ? value
    : Array.isArray(value?.students) ? value.students
    : Array.isArray(value?.teachers) ? value.teachers
    : Array.isArray(value?.classes) ? value.classes
    : Array.isArray(value?.subjects) ? value.subjects
    : [];
  const numericId = value => {
    if (value == null || value === '') return null;
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  };
  const objectId = value => numericId(value?.id ?? value?.classId ?? value?.schoolClassId ?? value);
  const studentClassId = student => objectId(student?.classId ?? student?.class ?? student?.schoolClass ?? student?.schoolClassId);
  const classId = schoolClass => objectId(schoolClass?.id ?? schoolClass?.classId ?? schoolClass);
  const activeClass = schoolClass => classId(schoolClass) !== 0 && schoolClass?.active !== false && schoolClass?.active !== 0;
  async function getJson(path, options) {
    const response = await fetch(path, options);
    if (!response.ok) throw Error(path);
    return response.json();
  }
  async function loadSources() {
    const entries = await Promise.allSettled([
      getJson('/students'),
      getJson('/teachers'),
      getJson('/classes'),
      getJson('/subjects'),
      getJson('/curriculum-enrollment-catalog', {method:'POST', headers:{'Content-Type':'application/json'}, body:'{}'})
    ]);
    const [students, teachers, classes, subjects, catalog] = entries.map(entry => entry.status === 'fulfilled' ? entry.value : null);
    return {students, teachers, classes, subjects, catalog, hasError: entries.some(entry => entry.status === 'rejected')};
  }
  function card(title, value, detail, warning) {
    const node = document.createElement('article');
    node.className = 'admin-status-card';
    if (warning) node.classList.add('admin-status-warning');
    node.append(text('h3', title), text('div', value, 'admin-status-value'));
    if (detail) node.append(text('div', detail, 'admin-status-detail'));
    return node;
  }
  function setupItem(label, done) {
    const item = document.createElement('li');
    const marker = text('span', done ? '✓ erledigt' : '⚠ offen', done ? 'admin-status-ok' : 'admin-status-warning');
    item.append(marker, document.createTextNode(` ${label}`));
    return item;
  }
  function semesterDisplay(catalog) {
    if (!catalog) return {value:'Nicht verfügbar', detail:'', warning:true, exists:false, active:false};
    const semesters = asList(catalog.semesters ?? catalog);
    if (semesters.length === 0) return {value:'Noch kein Halbjahr angelegt', detail:'', warning:true, exists:false, active:false};
    const active = semesters.find(semester => semester?.active === true || semester?.active === 1);
    if (!active) return {value:'Nicht festgelegt', detail:'', warning:true, exists:true, active:false};
    const year = active.schoolYearLabel ?? active.yearLabel ?? active.schoolYear ?? '';
    const label = active.label ?? active.name ?? String(active.id ?? '');
    return {value:[year, label].filter(Boolean).join(' '), detail:'', warning:false, exists:true, active:true};
  }
  function render(root, data) {
    const students = data.students ? asList(data.students) : null;
    const teachers = data.teachers ? asList(data.teachers) : null;
    const classes = data.classes ? asList(data.classes).filter(activeClass) : null;
    const subjects = data.subjects ? asList(data.subjects) : null;
    const semester = semesterDisplay(data.catalog);
    const unassigned = students ? students.filter(student => studentClassId(student) === 0).length : 0;
    const grid = document.createElement('div');
    grid.className = 'admin-status-grid';
    grid.append(
      card('Aktives Halbjahr', semester.value, semester.detail, semester.warning),
      card('Schüler', students ? String(students.length) : 'Nicht verfügbar', students ? (unassigned === 0 ? 'Alle zugeordnet' : `${unassigned} nicht zugeordnet`) : '', false),
      card('Lehrkräfte', teachers ? String(teachers.length) : 'Nicht verfügbar'),
      card('Klassen', classes ? String(classes.length) : 'Nicht verfügbar'),
      card('Fächer', subjects ? String(subjects.length) : 'Nicht verfügbar')
    );
    const setup = document.createElement('section');
    setup.append(text('h3', 'Einrichtungsstand'));
    const list = document.createElement('ul');
    list.className = 'admin-setup-status';
    list.append(
      setupItem('Halbjahr angelegt', semester.exists),
      setupItem('Aktives Halbjahr festgelegt', semester.active),
      setupItem('Klassen vorhanden', classes ? classes.length > 0 : false),
      setupItem('Fächer vorhanden', subjects ? subjects.length > 0 : false)
    );
    setup.append(list);
    const links = document.createElement('nav');
    links.className = 'admin-quick-links';
    links.setAttribute('aria-label', 'Schnellaktionen');
    [
      ['Schuldaten verwalten', '/dashboard#schuldaten'],
      ['Schuljahr & Zuordnungen', '/dashboard#schuljahr'],
      ['Zentrales Curriculum', '/dashboard#curriculum']
    ].forEach(([label, href]) => {
      const link = document.createElement('a');
      link.href = href;
      link.textContent = label;
      links.append(link);
    });
    const status = text('p', data.hasError ? 'Einige Verwaltungsdaten konnten nicht geladen werden.' : '', 'admin-status-detail');
    root.replaceChildren(text('h2', 'Übersicht'), grid, setup, links, status);
  }
  async function init() {
    const root = document.querySelector('#uebersicht');
    if (!root || root.dataset.loaded === 'true') return;
    root.dataset.loaded = 'true';
    root.replaceChildren(text('h2', 'Übersicht'), text('p', 'Verwaltungsdaten werden geladen.', 'admin-status-detail'));
    render(root, await loadSources());
  }
  document.addEventListener('DOMContentLoaded', () => { init().catch(() => {}); });
})();
