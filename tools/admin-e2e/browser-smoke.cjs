const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH);

const base = process.env.A4B_BASE_URL;
const fixture = JSON.parse(fs.readFileSync(process.env.A4B_CREDENTIALS, 'utf8'));
const screenshots = process.env.A4B_SCREENSHOTS;
const reportPath = process.env.A4B_REPORT;
const failures = [];
const checks = [];
const write = (name, ok, detail = '') => {
  checks.push({ name, ok, detail });
  if (!ok) failures.push(`${name}: ${detail}`);
  console.log(`[A4B] ${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ` — ${detail}` : ''}`);
};
function protectedDatabaseState() {
  const result = spawnSync('python3', ['-c', String.raw`
import hashlib, json, sqlite3, sys, urllib.parse
uri='file:'+urllib.parse.quote(sys.argv[1], safe='/')+'?mode=ro'
db=sqlite3.connect(uri, uri=True)
tables={r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
def digest(query):
 rows=db.execute(query).fetchall()
 values=sorted(json.dumps(row, sort_keys=True, default=str) for row in rows)
 return hashlib.sha256(json.dumps(values).encode()).hexdigest()
history=['topics','tasks','taskstats','flexible_topics','flexible_tasks','completed_flexible_tasks','student_curriculum_stage_assessments','student_subject_requests','student_active_curriculum_stages','curriculum_individual_assignments']
state={}
for table in history:
 if table in tables: state[table]=digest('SELECT * FROM "'+table+'"')
for table in sorted(t for t in tables if 'release' in t.lower()): state[table]=digest('SELECT * FROM "'+table+'"')
state['course_group_ids']=digest('SELECT id,subject,grade,semester FROM course_groups') if 'course_groups' in tables else None
state['course_group_members']=digest('SELECT * FROM course_group_members') if 'course_group_members' in tables else None
state['context_identity']=digest('SELECT student,subject,semester,"class",grade,course_group FROM student_curriculum_contexts') if 'student_curriculum_contexts' in tables else None
print(json.dumps(state, sort_keys=True))
`, process.env.A4B_DB_PATH], { encoding: 'utf8' });
  if (result.status !== 0) throw new Error(`Read-only DB snapshot failed: ${result.stderr}`);
  return JSON.parse(result.stdout);
}

async function main() {
  let browser;
  let context;
  try {
  browser = await chromium.launch({ headless: true });
  context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  page.setDefaultTimeout(15000);
  page.setDefaultNavigationTimeout(20000);
  const loginResponses = [];
  const dialogs = [];
  page.on('dialog', async dialog => {
    dialogs.push(dialog.message());
    await dialog.dismiss();
  });
  page.on('console', msg => { if (msg.type() === 'error') failures.push(`console.error: ${msg.text()}`); });
  page.on('pageerror', error => failures.push(`pageerror: ${error.message}`));
  page.on('requestfailed', request => failures.push(`request failed ${request.method()} ${new URL(request.url()).pathname}`));
  page.on('response', response => {
    const url = new URL(response.url());
    if (url.origin === base && response.status() >= 500) failures.push(`HTTP ${response.status()} ${url.pathname}`);
    if (url.origin === base && response.status() === 403) failures.push(`unexpected admin-session HTTP 403 ${url.pathname}`);
    if (url.origin === base && response.status() === 404 && /\.(js|css)$/.test(url.pathname)) failures.push(`missing core asset ${url.pathname}`);
  });

  await page.goto(`${base}/`, { waitUntil: 'domcontentloaded' });
  write('public root', page.url().startsWith(`${base}/`));
  await page.goto(`${base}/login`, { waitUntil: 'domcontentloaded' });
  write('login form rendered', await page.locator('#loginForm').count() === 1);
  await page.locator('#username').fill(fixture.adminUsername);
  await page.locator('#password').fill(fixture.adminPassword);
  page.on('response', response => {
    if (new URL(response.url()).pathname === '/login' && response.request().method() === 'POST') {
      loginResponses.push({ status: response.status(), url: new URL(response.url()).pathname });
    }
  });
  await page.locator('#loginForm button[type=submit]').click();
  try {
    await page.waitForURL('**/dashboard', { timeout: 15000 });
  } catch {
    throw new Error(`admin login did not redirect; POST /login=${JSON.stringify(loginResponses)}; currentPath=${new URL(page.url()).pathname}; loginDialog=${JSON.stringify(dialogs)}`);
  }
  await page.getByRole('heading', { name: 'Admin-Dashboard' }).waitFor();
  write('real admin login and session redirect', true);

  const pages = [
    ['/dashboard', 'Admin-Dashboard', 'dashboard.png'],
    ['/manage_students', 'Schüler', 'students.png'],
    ['/manage_teachers', 'Lehrkräfte', 'teachers.png'],
    ['/manage_classes', 'Klassen', 'classes.png'],
    ['/manage_subjects', 'Fächer', 'subjects.png'],
    ['/subject', null, null], ['/teacher', null, null], ['/class', null, null], ['/student', null, null],
    ['/plugins', 'Module', null], ['/editor', 'Editor', null], ['/manage_permissions', null, null]
  ];
  for (const [route, heading, screenshot] of pages) {
    const response = await page.goto(`${base}${route}`, { waitUntil: 'domcontentloaded' });
    write(`admin page ${route}`, response && response.status() === 200, `HTTP ${response?.status()}`);
    if (heading) write(`admin page content ${route}`, (await page.locator('body').innerText()).includes(heading));
    if (screenshot) await page.screenshot({ path: path.join(screenshots, screenshot), fullPage: true });
  }
  write('Permission Manager route runtime proof', await page.locator('body').innerText().then(text => /Berechtigungen|Rollen|Permission/i.test(text)));

  await page.goto(`${base}/dashboard#schuldaten`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => document.querySelector('.admin-main-menu a[aria-current="page"]'), null, { timeout: 10000 });
  write('active admin nav updates', await page.locator('.admin-main-menu a[aria-current="page"]').count() === 1);
  await page.screenshot({ path: path.join(screenshots, 'school-data.png'), fullPage: true });
  await page.goto(`${base}/dashboard#schuljahr`, { waitUntil: 'domcontentloaded' });
  await page.locator('[data-global-semester]').waitFor({ state: 'visible' });
  await page.locator('#admin-enrollment .admin-course-group-summary').waitFor({ state: 'attached', timeout: 15000 });
  await page.locator('#admin-enrollment .admin-regular-assignments').waitFor({ state: 'attached', timeout: 15000 });
  write('global semester selector visible', await page.locator('[data-global-semester]').count() === 1);
  await page.getByRole('button', { name: 'Jetzt verwalten' }).click();
  const gradeSelector = page.locator('#admin-enrollment fieldset:has(legend) select').first();
  await gradeSelector.waitFor({ state: 'visible' });
  await gradeSelector.selectOption({ label: '6' });
  await page.getByRole('button', { name: 'Auswahl bestätigen' }).click();
  await page.locator('#admin-enrollment .admin-regular-assignments').waitFor({ state: 'visible' });
  await page.locator('#admin-enrollment .admin-course-group-summary').waitFor({ state: 'visible' });
  await page.screenshot({ path: path.join(screenshots, 'school-year.png'), fullPage: true });
  write('REGULAR assignment area rendered', await page.locator('#admin-enrollment .admin-regular-assignments').count() === 1);
  await page.screenshot({ path: path.join(screenshots, 'regular.png'), fullPage: true });
  write('INDIVIDUAL CourseGroup summary rendered', await page.locator('#admin-enrollment .admin-course-group-summary').count() === 1);
  await page.screenshot({ path: path.join(screenshots, 'individual.png'), fullPage: true });
  await page.goto(`${base}/dashboard#curriculum`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(150);
  write('curriculum section accessible from shell', await page.locator('#curriculum-admin').isVisible());
  await page.screenshot({ path: path.join(screenshots, 'curriculum.png'), fullPage: true });

  const browserDimensions = async (width, height, suffix) => {
    await page.setViewportSize({ width, height });
    await page.goto(`${base}/dashboard#schuldaten`, { waitUntil: 'domcontentloaded' });
    await page.waitForFunction(() => document.querySelector('.admin-main-menu a[aria-current="page"]'), null, { timeout: 10000 });
    const metrics = await page.evaluate(() => ({
      client: document.documentElement.clientWidth,
      scroll: document.documentElement.scrollWidth,
      bodyClient: document.body.clientWidth,
      bodyScroll: document.body.scrollWidth,
      activeNav: document.querySelectorAll('.admin-main-menu a[aria-current="page"]').length,
      overflowing: [...document.querySelectorAll('body *')].map(element => ({
        tag: element.tagName.toLowerCase(), id: element.id, className: typeof element.className === 'string' ? element.className : '',
        right: Math.round(element.getBoundingClientRect().right)
      })).filter(element => element.right > document.documentElement.clientWidth + 1).sort((a, b) => b.right - a.right).slice(0, 5)
    }));
    write(`no whole-page overflow ${width}x${height}`, metrics.scroll <= metrics.client + 1 && metrics.bodyScroll <= metrics.bodyClient + 1, JSON.stringify(metrics));
    await page.screenshot({ path: path.join(screenshots, `dashboard${suffix}.png`), fullPage: true });
  };
  await browserDimensions(1440, 900, '');
  await browserDimensions(1280, 800, '-1280');
  await browserDimensions(768, 1024, '-tablet');
  await browserDimensions(390, 844, '-mobile');
  await page.goto(`${base}/dashboard#schuljahr`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(150);
  await page.screenshot({ path: path.join(screenshots, 'school-year-mobile.png'), fullPage: true });
  await page.goto(`${base}/dashboard#schuljahr`, { waitUntil: 'domcontentloaded' });
  await page.locator('#admin-enrollment .admin-course-group-summary').waitFor({ state: 'attached', timeout: 15000 });
  await page.getByRole('button', { name: 'Jetzt verwalten' }).click();
  const mobileGradeSelectorAgain = page.locator('#admin-enrollment fieldset:has(legend) select').first();
  await mobileGradeSelectorAgain.waitFor({ state: 'visible' });
  await mobileGradeSelectorAgain.selectOption({ label: '6' });
  await page.getByRole('button', { name: 'Auswahl bestätigen' }).click();
  await page.locator('#admin-enrollment .admin-course-group-summary').waitFor({ state: 'visible' });
  await page.screenshot({ path: path.join(screenshots, 'individual-mobile.png'), fullPage: true });

  await page.exposeFunction('snapshotProtectedDBState', protectedDatabaseState);
  const api = await page.evaluate(async data => {
    const out = {};
    const post = async (route, body) => {
      const response = await fetch(route, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal: AbortSignal.timeout(15000) });
      const text = await response.text();
      let json; try { json = JSON.parse(text); } catch { json = { text }; }
      return { status: response.status, json };
    };
    const expect = (condition, message) => { if (!condition) throw new Error(message); };
    const catalog = await post('/curriculum-enrollment-catalog', {});
    expect(catalog.status === 200, `catalog HTTP ${catalog.status}`);
    out.catalog = true;
    const group = await post('/curriculum-course-groups', { semesterId: data.semester1Id, grade: 6, assignmentGroup: 'WPF' });
    expect(group.status === 200 && group.json.length === 1, `cross-class WPF group result ${group.status}`);
    expect(group.json[0].memberCount === 2, 'WPF group does not contain both cross-class students');
    const religion = await post('/curriculum-course-groups', { semesterId: data.semester1Id, grade: 6, assignmentGroup: 'RELIGION_ETHIK' });
    expect(religion.status === 200 && religion.json.length === 1 && religion.json[0].memberCount === 2, 'concrete Religion/Ethik cross-class group missing');
    out.crossClassCourseGroup = true;
    out.religionEthikCrossClass = true;
    const groupIdBeforeTeacherChange = group.json[0].id;
    const blockedHistoricalSwitch = await post('/assign-curriculum-wpf', {
      studentId: data.studentAId, subjectId: data.alternativeWpfSubjectId, classId: data.classAId,
      semesterId: data.semester1Id, assignmentGroup: 'WPF', expectedSubjectId: data.wpfSubjectId
    });
    expect(blockedHistoricalSwitch.status === 409, `completed-work subject switch HTTP ${blockedHistoricalSwitch.status}`);
    out.completedHistorySwitchBlocked = true;
    const createSubject = await post('/add-subject-with-type', { name: `A4b unused ${Date.now()}`, mode: 'REGULAR', assignmentGroup: null });
    expect(createSubject.status === 200 && Number.isInteger(createSubject.json.subjectId), `subject create HTTP ${createSubject.status}`);
    const preflight = await post('/delete-subject', { id: createSubject.json.subjectId, action: 'preflight' });
    expect(preflight.status === 200 && preflight.json.deletable === true, 'unused subject preflight did not allow deletion');
    const deleted = await post('/delete-subject', { id: createSubject.json.subjectId, action: 'delete' });
    expect(deleted.status === 200 && deleted.json.deleted === true, `unused subject delete HTTP ${deleted.status}`);
    out.unusedSubjectDelete = true;
    const blockedSubject = await post('/add-subject-with-type', { name: `A4b assigned ${Date.now()}`, mode: 'REGULAR', assignmentGroup: null });
    expect(blockedSubject.status === 200, `dependent subject create HTTP ${blockedSubject.status}`);
    const assignment = await post('/assign-grade-curriculum', {
      grade: 6, semesterId: data.semester1Id, subjectIds: [blockedSubject.json.subjectId],
      teaching: [
        { classId: data.classAId, subjectId: blockedSubject.json.subjectId, teacherId: data.teacherAId },
        { classId: data.classDId, subjectId: blockedSubject.json.subjectId, teacherId: data.teacherAId }
      ]
    });
    expect(assignment.status === 200, `regular assignment HTTP ${assignment.status}`);
    const blocked = await post('/delete-subject', { id: blockedSubject.json.subjectId, action: 'preflight' });
    expect(blocked.status === 200 && blocked.json.deletable === false, 'assigned subject preflight was not blocked');
    const conflict = await post('/delete-subject', { id: blockedSubject.json.subjectId, action: 'delete' });
    expect(conflict.status === 409, `assigned subject delete status ${conflict.status}`);
    out.blockedSubjectPreserved = true;
    const unusedTeacher = await post('/delete-teacher', { id: data.unusedTeacherId, action: 'preflight' });
    expect(unusedTeacher.status === 200 && unusedTeacher.json.deletable === true, 'unused teacher preflight did not allow deletion');
    const teacherDelete = await post('/delete-teacher', { id: data.unusedTeacherId, action: 'delete' });
    expect(teacherDelete.status === 200 && teacherDelete.json.deleted === true, `unused teacher delete HTTP ${teacherDelete.status}`);
    out.unusedTeacherDelete = true;
    const blockedTeacher = await post('/delete-teacher', { id: data.teacherAId, action: 'preflight' });
    expect(blockedTeacher.status === 200 && blockedTeacher.json.deletable === false, 'assigned teacher preflight was not blocked');
    const teacherConflict = await post('/delete-teacher', { id: data.teacherAId, action: 'delete' });
    expect(teacherConflict.status === 409, `assigned teacher delete status ${teacherConflict.status}`);
    out.blockedTeacherPreserved = true;
    const preview = await post('/curriculum-enrollment-catalog', {});
    expect(preview.json.nextSemesterPreview.frameCopied === true && preview.json.nextSemesterPreview.tutorsCopied === true, 'HJ1 preview copy semantics incorrect');
    const created = await post('/create-curriculum-semester', {});
    expect(created.status === 200 && created.json.frameCopied === true && created.json.tutorsCopied === true, `HJ2 create response ${created.status}`);
    const afterCreateCatalog = await post('/curriculum-enrollment-catalog', {});
    const createdSemester = afterCreateCatalog.json.semesters.find(row => row.id === created.json.id);
    expect(createdSemester && Number(createdSemester.active) === 0, 'creating HJ2 activated it implicitly');
    const copiedGroups = await post('/curriculum-course-groups', { semesterId: created.json.id, grade: 6, assignmentGroup: 'WPF' });
    expect(copiedGroups.status === 200 && copiedGroups.json.length === 1
      && copiedGroups.json[0].id !== groupIdBeforeTeacherChange && copiedGroups.json[0].memberCount === 2,
      'HJ2 did not get a new CourseGroup identity with the copied members');
    const oldTutors = await post('/curriculum-tutor-assignments', { semesterId: data.semester1Id });
    const copiedTutors = await post('/curriculum-tutor-assignments', { semesterId: created.json.id });
    expect(oldTutors.status === 200 && copiedTutors.status === 200 && oldTutors.json.length === 4 && copiedTutors.json.length === 4,
      'HJ1/HJ2 tutor rows were not preserved and copied');
    out.hj2Copy = true;
    out.createDoesNotActivate = true;
    out.newCourseGroupIdentity = true;
    out.tutorContinuity = true;
    const activated = await post('/activate-curriculum-semester', { semesterId: created.json.id });
    expect(activated.status === 200, `HJ2 activate HTTP ${activated.status}`);
    const nextPreview = await post('/curriculum-enrollment-catalog', {});
    expect(nextPreview.json.nextSemesterPreview.newSchoolYear === true && nextPreview.json.nextSemesterPreview.frameCopied === false && nextPreview.json.nextSemesterPreview.tutorsCopied === true, 'new-year preview copy semantics incorrect');
    const nextYear = await post('/create-curriculum-semester', {});
    expect(nextYear.status === 200 && nextYear.json.newSchoolYear === true && nextYear.json.frameCopied === false && nextYear.json.tutorsCopied === true, `new-year create response ${nextYear.status}`);
    const afterNewYear = await post('/curriculum-enrollment-catalog', {});
    const newYearSemester = afterNewYear.json.semesters.find(row => row.id === nextYear.json.id);
    expect(newYearSemester && Number(newYearSemester.active) === 0, 'creating new-year HJ1 activated it implicitly');
    const newYearGroups = await post('/curriculum-course-groups', { semesterId: nextYear.json.id, grade: 6 });
    const newYearTutors = await post('/curriculum-tutor-assignments', { semesterId: nextYear.json.id });
    expect(newYearGroups.status === 200 && newYearGroups.json.length === 0, 'new school year received curriculum CourseGroups');
    expect(newYearTutors.status === 200 && newYearTutors.json.length === 4, 'tutor assignments did not continue across the school-year boundary');
    out.newSchoolYearTutorOnlyCopy = true;
    out.newYearRemainsInactive = true;
    const beforeIndividualTeacherChange = await window.snapshotProtectedDBState();
    const teacherChange = await post('/assign-individual-grade-teacher', {
      grade: 6, semesterId: data.semester1Id, subjectId: data.wpfSubjectId, teacherId: data.teacherBId
    });
    expect(teacherChange.status === 200, `individual teacher change HTTP ${teacherChange.status}`);
    const groupAfterTeacherChange = await post('/curriculum-course-groups', { semesterId: data.semester1Id, grade: 6, assignmentGroup: 'WPF' });
    expect(groupAfterTeacherChange.status === 200 && groupAfterTeacherChange.json[0].id === groupIdBeforeTeacherChange
      && groupAfterTeacherChange.json[0].teacherId === data.teacherBId && groupAfterTeacherChange.json[0].memberCount === 2,
      'teacher update changed group identity or members');
    expect(JSON.stringify(await window.snapshotProtectedDBState()) === JSON.stringify(beforeIndividualTeacherChange),
      'individual teacher change mutated releases, membership, contexts, or learning history');
    out.individualTeacherChange = true;
    out.courseGroupIdentityAndMembersPreserved = true;
    out.individualTeacherChangePreservedHistoryAndReleases = true;
    const beforeRegularTeacherChange = await window.snapshotProtectedDBState();
    const regularTeacherChange = await post('/assign-grade-curriculum', {
      grade: 6, semesterId: data.semester1Id, subjectIds: [data.regularSubjectId],
      teaching: [
        { classId: data.classAId, subjectId: data.regularSubjectId, teacherId: data.teacherBId },
        { classId: data.classDId, subjectId: data.regularSubjectId, teacherId: data.teacherBId }
      ]
    });
    expect(regularTeacherChange.status === 200, `REGULAR teacher change HTTP ${regularTeacherChange.status}`);
    expect(JSON.stringify(await window.snapshotProtectedDBState()) === JSON.stringify(beforeRegularTeacherChange),
      'REGULAR teacher change mutated releases, membership, contexts, or learning history');
    out.regularTeacherChange = true;
    out.regularTeacherChangePreservedHistoryAndReleases = true;
    const concurrencySuccess = await post('/assign-curriculum-wpf', {
      studentId: data.studentBId, subjectId: data.alternativeWpfSubjectId, classId: data.classDId,
      semesterId: data.semester1Id, assignmentGroup: 'WPF', expectedSubjectId: data.wpfSubjectId
    });
    expect(concurrencySuccess.status === 200, `expectedSubjectId valid switch HTTP ${concurrencySuccess.status}`);
    const concurrencyConflict = await post('/assign-curriculum-wpf', {
      studentId: data.studentBId, subjectId: data.wpfSubjectId, classId: data.classDId,
      semesterId: data.semester1Id, assignmentGroup: 'WPF', expectedSubjectId: data.wpfSubjectId
    });
    expect(concurrencyConflict.status === 409, `stale expectedSubjectId HTTP ${concurrencyConflict.status}`);
    out.optimisticConcurrency = true;
    return out;
  }, fixture);
  checks.push(...Object.entries(api).map(([name, ok]) => ({ name: `HTTP E2E ${name}`, ok, detail: '' })));

  const authPaths = [
    '/delete-subject', '/delete-teacher', '/create-curriculum-semester', '/activate-curriculum-semester',
    '/assign-class-tutors', '/assign-grade-curriculum', '/assign-individual-grade-teacher', '/assign-curriculum-wpf'
  ];
  for (const [role, username, password] of [
    ['teacher', fixture.teacherAUsername, fixture.teacherAPassword],
    ['student', fixture.studentAUsername, fixture.studentAPassword]
  ]) {
    const roleContext = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1280, height: 800 } });
    const rolePage = await roleContext.newPage();
    rolePage.setDefaultTimeout(15000);
    rolePage.setDefaultNavigationTimeout(20000);
    rolePage.on('dialog', dialog => dialog.dismiss());
    await rolePage.goto(`${base}/login`, { waitUntil: 'domcontentloaded' });
    await rolePage.locator('#username').fill(username);
    await rolePage.locator('#password').fill(password);
    await rolePage.locator('#loginForm button[type=submit]').click();
    await rolePage.waitForURL('**/dashboard', { timeout: 15000 });
    write(`${role} real login and session`, true);
    if (role === 'student') {
      const studentChecks = await rolePage.evaluate(async data => {
        const post = async body => {
          const response = await fetch('/my-curriculum-catalog', {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
            signal: AbortSignal.timeout(15000)
          });
          let json = {}; try { json = await response.json(); } catch {}
          return { status: response.status, json };
        };
        return {
          own: await post({ subjectId: data.wpfSubjectId, semesterId: data.semester1Id }),
          forged: await post({ subjectId: data.wpfSubjectId, semesterId: data.semester1Id, studentId: data.studentBId }),
          foreign: await post({ subjectId: data.alternativeWpfSubjectId, semesterId: data.semester1Id })
        };
      }, fixture);
      write('student session reads own curriculum catalog', studentChecks.own.status === 200 && studentChecks.own.json.semesterId === fixture.semester1Id, `HTTP ${studentChecks.own.status}`);
      write('student catalog rejects client-selected identity', studentChecks.forged.status === 400, `HTTP ${studentChecks.forged.status}`);
      write('student catalog denies unassigned subject', studentChecks.foreign.status === 403, `HTTP ${studentChecks.foreign.status}`);
    }
    const denied = await rolePage.evaluate(async routes => {
      const results = [];
      for (const route of routes) {
        const response = await fetch(route, {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}', signal: AbortSignal.timeout(15000)
        });
        results.push({ route, status: response.status });
      }
      return results;
    }, authPaths);
    for (const result of denied) write(`${role} blocked from ${result.route}`, result.status === 403, `HTTP ${result.status}`);
    await roleContext.close();
  }
  const deletedLogin = await page.evaluate(async credentials => {
    const response = await fetch('/login', {
      method: 'POST', body: new URLSearchParams(credentials), redirect: 'manual', signal: AbortSignal.timeout(15000)
    });
    return response.status;
  }, { username: fixture.unusedTeacherUsername, password: fixture.unusedTeacherPassword });
  write('deleted teacher login rejected', deletedLogin === 401, `HTTP ${deletedLogin}`);

  const overflow = await page.evaluate(() => ({ client: document.documentElement.clientWidth, scroll: document.documentElement.scrollWidth }));
  write('mobile whole-page overflow final', overflow.scroll <= overflow.client + 1, JSON.stringify(overflow));
  fs.writeFileSync(reportPath, JSON.stringify({ checks, errors: failures }, null, 2));
  if (failures.length) throw new Error(failures.join('\n'));
  } finally {
    await context?.close().catch(() => {});
    await browser?.close().catch(() => {});
  }
}

main().catch(error => {
  console.error(`A4b browser acceptance failed: ${error.message}`);
  process.exitCode = 1;
});
