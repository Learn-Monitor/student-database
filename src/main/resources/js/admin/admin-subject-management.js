'use strict';

(() => {
  const TYPE_OPTIONS = [
    ['REGULAR:', 'REGULAR · verbindlich für alle'],
    ['INDIVIDUAL:WPF', 'WPF · individuell'],
    ['INDIVIDUAL:RELIGION_ETHIK', 'Religion/Ethik · individuell']
  ];

  const byId = id => document.getElementById(id);
  const listFrom = value => Array.isArray(value) ? value : Array.isArray(value?.subjects) ? value.subjects : [];
  const typeValue = subject => subject?.mode === 'INDIVIDUAL'
    ? `INDIVIDUAL:${subject.assignmentGroup || 'WPF'}`
    : 'REGULAR:';
  const typeLabel = value => TYPE_OPTIONS.find(([option]) => option === value)?.[1] || 'REGULAR · verbindlich für alle';
  const setStatus = (node, message, state = '') => {
    if (!node) return;
    node.textContent = message;
    node.dataset.state = state;
  };

  async function requestCatalog() {
    const response = await fetch('/curriculum-enrollment-catalog', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: '{}'
    });
    if (!response.ok) throw new Error('catalog');
    return response.json();
  }

  function appendTypeOptions(select, selected) {
    TYPE_OPTIONS.forEach(([value, label]) => {
      const option = document.createElement('option');
      option.value = value;
      option.textContent = label;
      option.selected = value === selected;
      select.append(option);
    });
  }

  function subjectEditLink(subject) {
    const link = document.createElement('a');
    link.className = 'admin-action-link admin-subject-edit-link';
    link.href = `/subject?subjectId=${encodeURIComponent(subject.id)}`;
    link.textContent = 'Bearbeiten';
    return link;
  }

  async function saveType(subject, select, button, rowStatus) {
    const [mode, assignmentGroup] = select.value.split(':');
    button.disabled = true;
    setStatus(rowStatus, 'Wird gespeichert …');
    try {
      const response = await fetch('/set-curriculum-subject-type', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({
          subjectId: subject.id,
          mode,
          assignmentGroup: assignmentGroup || null
        })
      });
      if (!response.ok) throw new Error('type');
      subject.mode = mode;
      subject.assignmentGroup = assignmentGroup || null;
      setStatus(rowStatus, 'Gespeichert', 'success');
    } catch {
      setStatus(rowStatus, 'Fachart konnte nicht gespeichert werden.', 'error');
      select.value = typeValue(subject);
    } finally {
      button.disabled = false;
    }
  }

  function renderSubjects(subjects) {
    const body = byId('subject-list-body');
    if (!body) return;
    body.replaceChildren();
    if (subjects.length === 0) {
      const row = document.createElement('tr');
      const cell = document.createElement('td');
      cell.colSpan = 4;
      cell.className = 'admin-empty-state';
      cell.textContent = 'Noch keine Fächer angelegt.';
      row.append(cell);
      body.append(row);
      return;
    }
    subjects.forEach(subject => {
      const row = document.createElement('tr');
      const name = document.createElement('th');
      name.scope = 'row';
      name.textContent = subject.name || 'Unbenanntes Fach';
      const typeCell = document.createElement('td');
      const field = document.createElement('div');
      field.className = 'admin-inline-form';
      const select = document.createElement('select');
      select.setAttribute('aria-label', `Fachart für ${subject.name || 'Fach'}`);
      appendTypeOptions(select, typeValue(subject));
      const save = document.createElement('button');
      save.type = 'button';
      save.textContent = 'Speichern';
      const rowStatus = document.createElement('span');
      rowStatus.className = 'admin-inline-status';
      rowStatus.setAttribute('aria-live', 'polite');
      save.addEventListener('click', () => saveType(subject, select, save, rowStatus));
      field.append(select, save);
      typeCell.append(field, rowStatus);
      const assignment = document.createElement('td');
      assignment.textContent = subject.mode === 'INDIVIDUAL'
        ? (subject.assignmentGroup === 'RELIGION_ETHIK' ? 'Religion/Ethik' : 'WPF')
        : 'Alle';
      const action = document.createElement('td');
      action.append(subjectEditLink(subject));
      row.append(name, typeCell, assignment, action);
      body.append(row);
    });
  }

  async function loadSubjects() {
    const status = byId('subject-list-status');
    setStatus(status, 'Fächer werden geladen …');
    try {
      const catalog = await requestCatalog();
      renderSubjects(listFrom(catalog));
      setStatus(status, `${listFrom(catalog).length} Fächer`, 'success');
    } catch {
      const body = byId('subject-list-body');
      if (body) body.replaceChildren();
      setStatus(status, 'Fächer konnten nicht geladen werden.', 'error');
    }
  }

  async function createSubject(form) {
    const status = byId('subject-management-status');
    const submit = form.querySelector('button[type="submit"]');
    const name = form.querySelector('[name="name"]')?.value.trim();
    const value = form.querySelector('[name="subjectType"]')?.value || 'REGULAR:';
    const [mode, assignmentGroup] = value.split(':');
    if (!name) return;
    submit.disabled = true;
    setStatus(status, 'Fach wird angelegt …');
    try {
      const response = await fetch('/add-subject-with-type', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({name, mode, assignmentGroup: assignmentGroup || null})
      });
      if (!response.ok) throw new Error('create');
      form.reset();
      setStatus(status, 'Fach wurde angelegt.', 'success');
      await loadSubjects();
    } catch {
      setStatus(status, 'Fach konnte nicht angelegt werden.', 'error');
    } finally {
      submit.disabled = false;
    }
  }

  function init() {
    const form = byId('manage-subjects-form');
    if (!form) return;
    form.addEventListener('submit', event => {
      event.preventDefault();
      createSubject(form).catch(() => setStatus(byId('subject-management-status'), 'Fach konnte nicht angelegt werden.', 'error'));
    });
    loadSubjects().catch(() => setStatus(byId('subject-list-status'), 'Fächer konnten nicht geladen werden.', 'error'));
  }

  document.addEventListener('DOMContentLoaded', init);
})();
