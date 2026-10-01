(() => {
  let teachers = [];
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

  function render() {
    const query = document.getElementById('teacherFilter').value.trim().toLocaleLowerCase('de');
    const rows = teachers.filter(teacher => `${firstName(teacher)} ${lastName(teacher)} ${login(teacher)}`.toLocaleLowerCase('de').includes(query))
      .sort(compare).map(teacher => {
        const row = document.createElement('tr');
        cell(row, lastName(teacher)); cell(row, firstName(teacher)); cell(row, login(teacher));
        const action = cell(row, '');
        const button = document.createElement('button');
        button.type = 'button'; button.className = 'view-teacher'; button.textContent = 'Bearbeiten';
        button.addEventListener('click', () => viewTeacher(teacher.id));
        action.append(button);
        return row;
      });
    document.getElementById('teacherTableBody').replaceChildren(...rows);
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
      render();
    } catch {
      document.getElementById('teacher-list-status').textContent = 'Die Lehrkräfteliste konnte nicht geladen werden.';
      document.getElementById('teacher-list-status').dataset.state = 'error';
    }
  });
})();
