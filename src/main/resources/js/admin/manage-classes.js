(() => {
  const status = message => { const node = document.getElementById('class-list-status'); node.textContent = message; };

  async function loadClasses() {
    status('Klassen werden geladen …');
    const body = document.querySelector('#classTable tbody');
    try {
      const classes = await fetchJson('/classes');
      const rows = (Array.isArray(classes) ? classes : []).map(schoolClass => {
        const row = document.createElement('tr');
        const name = document.createElement('th'); name.scope = 'row'; name.textContent = schoolClass.label || ''; row.append(name);
        const grade = document.createElement('td'); grade.textContent = schoolClass.grade ?? ''; row.append(grade);
        const actions = document.createElement('td');
        const edit = document.createElement('button'); edit.type = 'button'; edit.className = 'edit'; edit.textContent = 'Bearbeiten';
        edit.addEventListener('click', () => viewClass(schoolClass)); actions.append(edit);
        const archive = document.createElement('button'); archive.type = 'button'; archive.className = 'admin-button-secondary'; archive.textContent = 'Archivieren';
        archive.addEventListener('click', async () => {
          if (!confirm('Klasse wirklich archivieren? Die Klasse wird deaktiviert. Die Schülerinnen und Schüler bleiben erhalten und werden der Klasse „Nicht zugeordnet“ zugewiesen.')) return;
          archive.disabled = true; status('Klasse wird archiviert …');
          try {
            const response = await post('/delete-class', {classId: schoolClass.id});
            if (!response.ok) throw new Error('archive');
            await loadClasses();
            status('Klasse wurde archiviert.');
          } catch {
            status('Die Klasse konnte nicht archiviert werden.');
            archive.disabled = false;
          }
        });
        actions.append(archive); row.append(actions); return row;
      });
      body.replaceChildren(...rows);
      status(rows.length ? `${rows.length} Klassen geladen.` : 'Keine Klassen vorhanden.');
    } catch {
      body.replaceChildren(); status('Die Klassenliste konnte nicht geladen werden.');
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    const root = document.getElementById('classes');
    if (!root || root.dataset.initialized === 'true') return;
    root.dataset.initialized = 'true';
    loadClasses();
  });
})();
