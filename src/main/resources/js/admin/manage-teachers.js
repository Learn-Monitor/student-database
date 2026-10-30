(() => {
  let teachers = [];
  let visibleRoles = new Map();
  let notificationProfiles = new Map();
  let sortColumn = 'lastName';
  let sortDirection = 'ascending';

  const firstName = teacher => teacher.firstName || (teacher.name || '').split(' ').slice(0, -1).join(' ') || teacher.name || '';
  const lastName = teacher => teacher.lastName || (teacher.name || '').split(' ').slice(-1)[0] || '';
  const login = teacher => teacher.email || teacher.login || teacher.username || '';
  const compareText = (a, b) => a.localeCompare(b, 'de', {sensitivity: 'base'});

  function compare(a, b) {
    const values = {firstName: [firstName(a), firstName(b)], lastName: [lastName(a), lastName(b)], login: [login(a), login(b)]};
    const [left, right] = values[sortColumn] || values.lastName;
    const primary = compareText(left, right) * (sortDirection === 'ascending' ? 1 : -1);
    return primary || compareText(lastName(a), lastName(b)) || compareText(firstName(a), firstName(b));
  }

  function cell(row, value) {
    const item = document.createElement('td');
    item.textContent = value;
    row.append(item);
    return item;
  }

  function notificationCell(row, teacher) {
    const item = document.createElement('td');
    const profile = notificationProfiles.get(login(teacher)) || {};
    const input = document.createElement('input');
    input.type = 'email'; input.value = profile.notificationEmail || '';
    input.placeholder = 'keine hinterlegt'; input.setAttribute('aria-label', `Benachrichtigungs-Mail für ${login(teacher)}`);
    const button = document.createElement('button');
    button.type = 'button'; button.textContent = 'Speichern';
    button.addEventListener('click', () => saveNotificationEmail(teacher, input, button));
    item.append(input, button); row.append(item); return item;
  }

  function render() {
    const query = document.getElementById('teacherFilter').value.trim().toLocaleLowerCase('de');
    const rows = teachers.filter(teacher => `${firstName(teacher)} ${lastName(teacher)} ${login(teacher)}`.toLocaleLowerCase('de').includes(query))
      .sort(compare).map(teacher => {
        const row = document.createElement('tr');
        cell(row, lastName(teacher)); cell(row, firstName(teacher)); cell(row, login(teacher));
        notificationCell(row, teacher);
        const rolesCell = cell(row, '');
        const roles = visibleRoles.get(login(teacher)) || {admin:false, orga:false, sl:false, fachleitung:false};
        [['admin','Admin'],['orga','Orgaleitung'],['sl','Schulleitung'],['fachleitung','Fachleitung']].forEach(([key,label]) => {
          const wrapper = document.createElement('label');
          wrapper.className = 'teacher-role-control';
          const checkbox = document.createElement('input');
          checkbox.type = 'checkbox'; checkbox.checked = roles[key] === true;
          checkbox.dataset.role = key; checkbox.setAttribute('aria-label', `${label} für ${login(teacher)}`);
          checkbox.addEventListener('change', () => saveVisibleRole(teacher, key, checkbox));
          wrapper.append(checkbox, document.createTextNode(label)); rolesCell.append(wrapper);
        });
        const action = cell(row, '');
        const button = document.createElement('button');
        button.type = 'button'; button.className = 'view-teacher'; button.textContent = 'Bearbeiten';
        button.addEventListener('click', () => viewTeacher(teacher.id));
        action.append(button);
        const passwordButton = document.createElement('button');
        passwordButton.type = 'button'; passwordButton.className = 'reset-teacher-password'; passwordButton.textContent = 'Passwort neu setzen';
        passwordButton.addEventListener('click', () => openPasswordDialog(teacher));
        action.append(passwordButton);
        return row;
      });
    document.getElementById('teacherTableBody').replaceChildren(...rows);
  }

  async function saveVisibleRole(teacher, role, checkbox) {
    const status = document.getElementById('teacher-list-status');
    const previous = !checkbox.checked;
    checkbox.disabled = true;
    try {
      const response = await fetch('/set-teacher-visible-role', {
        method: 'POST', headers: {'Content-Type':'application/json'},
        body: JSON.stringify({user: login(teacher), role, enabled: checkbox.checked})
      });
      if (!response.ok) throw new Error('Die Zusatzrolle konnte nicht gespeichert werden.');
      if (role === 'admin') {
        const current = visibleRoles.get(login(teacher)) || {};
        current.admin = checkbox.checked; visibleRoles.set(login(teacher), current);
      }
      status.textContent = `${roleLabel(role)} gespeichert.`;
    } catch (error) {
      checkbox.checked = previous;
      status.textContent = error.message || 'Die Zusatzrolle konnte nicht gespeichert werden.';
      status.dataset.state = 'error';
    } finally { checkbox.disabled = false; }
  }

  function roleLabel(role) { return ({admin:'Admin',orga:'Orgaleitung',sl:'Schulleitung',fachleitung:'Fachleitung'})[role] || 'Zusatzrolle'; }

  async function saveNotificationEmail(teacher, input, button) {
    const status = document.getElementById('teacher-list-status');
    const value = input.value.trim(); button.disabled = true;
    try {
      const response = await fetch('/freistellung-mail-profile', {
        method: 'POST', headers: {'Content-Type':'application/json'},
        body: JSON.stringify({username: login(teacher), notificationEmail: value})
      });
      if (!response.ok) throw new Error('Die Benachrichtigungs-Mail konnte nicht gespeichert werden.');
      notificationProfiles.set(login(teacher), {notificationEmail: value});
      status.textContent = 'Benachrichtigungs-Mail gespeichert.'; status.dataset.state = 'success';
    } catch (error) {
      status.textContent = error.message || 'Die Benachrichtigungs-Mail konnte nicht gespeichert werden.';
      status.dataset.state = 'error';
    } finally { button.disabled = false; }
  }

  function bindSort() {
    document.querySelectorAll('#teacherTable [data-sort]').forEach(button => {
      button.addEventListener('click', () => {
        const key = button.dataset.sort;
        sortDirection = sortColumn === key && sortDirection === 'ascending' ? 'descending' : 'ascending';
        sortColumn = key;
        document.querySelectorAll('#teacherTable th').forEach(th => th.removeAttribute('aria-sort'));
        button.closest('th').setAttribute('aria-sort', sortDirection);
        render();
      });
    });
  }

  function openPasswordDialog(teacher) {
    const dialog = document.getElementById('teacher-password-dialog');
    const form = document.getElementById('teacher-password-form');
    const password = document.getElementById('teacher-new-password');
    const confirmation = document.getElementById('teacher-password-confirmation');
    const error = document.getElementById('teacher-password-error');
    document.getElementById('teacher-password-target').textContent = `${firstName(teacher)} ${lastName(teacher)} (${login(teacher)})`;
    password.value = ''; confirmation.value = ''; error.textContent = '';
    form.addEventListener('submit', async event => {
      event.preventDefault(); error.textContent = '';
      if (!form.reportValidity()) return;
      if (password.value !== confirmation.value) { error.textContent = 'Die Passwörter stimmen nicht überein.'; return; }
      if (!window.confirm('Passwort für dieses Konto wirklich neu setzen?')) return;
      const response = await fetch('/admin-reset-password', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({targetType:'teacher', targetId:teacher.id, password:password.value, passwordConfirmation:confirmation.value})});
      if (!response.ok) { error.textContent = 'Das Passwort konnte nicht gespeichert werden.'; return; }
      closePasswordDialog(); document.getElementById('teacher-list-status').textContent = 'Passwort wurde neu gesetzt.';
    });
    document.getElementById('teacher-password-cancel').addEventListener('click', closePasswordDialog);
    if (typeof dialog.showModal === 'function') dialog.showModal(); else dialog.setAttribute('open', '');
    password.focus();
  }

  function closePasswordDialog() {
    const dialog = document.getElementById('teacher-password-dialog');
    if (typeof dialog.close === 'function') dialog.close(); else dialog.removeAttribute('open');
  }

  document.addEventListener('DOMContentLoaded', async () => {
    const root = document.getElementById('teachers');
    if (!root || root.dataset.initialized === 'true') return;
    root.dataset.initialized = 'true';
    document.getElementById('add-teachers-csv-form').addEventListener('submit', event => {
      event.preventDefault();
      postDataAndDownload('/add-teachers', document.getElementById('csv-input').value, 'lehrer.csv');
    });
    document.getElementById('teacherFilter').addEventListener('input', render);
    bindSort();
    try {
      const data = await fetchJson('/teachers');
      teachers = Array.isArray(data) ? data : [];
      const roleResponse = await fetch('/get-teacher-visible-roles', {method:'POST', headers:{'Content-Type':'application/json'}, body:'{}'});
      if (!roleResponse.ok) throw new Error('Die Zusatzrollen konnten nicht geladen werden.');
      for (const item of await roleResponse.json()) visibleRoles.set(item.username, item);
      try {
        const profileResponse = await fetch('/freistellung-mail-admin-data', {method:'POST', headers:{'Content-Type':'application/json'}, body:'{}'});
        if (profileResponse.ok) for (const item of (await profileResponse.json()).profiles || []) notificationProfiles.set(item.username, item);
      } catch (_) { /* Das Mailprofil ist optional für die Lehrkräfteliste. */ }
      render();
    } catch {
      document.getElementById('teacher-list-status').textContent = 'Die Lehrkräfteliste konnte nicht geladen werden.';
      document.getElementById('teacher-list-status').dataset.state = 'error';
    }
  });
})();
