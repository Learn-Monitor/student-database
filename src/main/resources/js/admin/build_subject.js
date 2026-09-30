'use strict';

(() => {
    const TYPE_OPTIONS = new Set(['REGULAR:', 'INDIVIDUAL:WPF', 'INDIVIDUAL:RELIGION_ETHIK']);
    let selectedSubject = null;

    function storedSubject() {
        try {
            const value = JSON.parse(sessionStorage.getItem('currentSubject'));
            return value && Number.isInteger(Number(value.id)) ? value : null;
        } catch {
            return null;
        }
    }

    function querySubjectId() {
        const value = new URLSearchParams(window.location.search).get('subjectId');
        const id = Number(value);
        return Number.isInteger(id) && id > 0 ? id : null;
    }

    async function loadCatalog() {
        const response = await fetch('/curriculum-enrollment-catalog', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: '{}'
        });
        if (!response.ok) throw new Error('catalog');
        const data = await response.json();
        return Array.isArray(data?.subjects) ? data.subjects : [];
    }

    async function resolveSubject() {
        const requestedId = querySubjectId();
        const saved = storedSubject();
        if (!requestedId && saved) return saved;
        if (!requestedId) return null;
        if (saved && Number(saved.id) === requestedId && saved.name) return saved;
        const match = (await loadCatalog()).find(subject => Number(subject.id) === requestedId);
        return match || null;
    }

    function typeValue(subject) {
        return subject?.mode === 'INDIVIDUAL'
            ? `INDIVIDUAL:${subject.assignmentGroup || 'WPF'}`
            : 'REGULAR:';
    }

    function populateSubject(subject) {
        document.getElementById('subjectNameField').value = subject.name || '';
        Array.from(document.getElementsByClassName('subjectId')).forEach(element => {
            element.value = subject.id;
        });
        const typeField = document.getElementById('subjectTypeField');
        if (typeField && TYPE_OPTIONS.has(typeValue(subject))) typeField.value = typeValue(subject);
    }

    function setStatus(message, state = '') {
        const status = document.getElementById('subjectTypeStatus');
        if (!status) return;
        status.textContent = message;
        status.dataset.state = state;
    }

    async function loadSubjectType(subject) {
        const typeField = document.getElementById('subjectTypeField');
        if (!typeField) return;
        try {
            const subjects = await loadCatalog();
            const current = subjects.find(item => Number(item.id) === Number(subject.id));
            if (current && TYPE_OPTIONS.has(typeValue(current))) typeField.value = typeValue(current);
        } catch {
            setStatus('Fachart konnte nicht geladen werden.', 'error');
        }
    }

    async function saveSubjectType(event) {
        event.preventDefault();
        if (!selectedSubject) return;
        const field = document.getElementById('subjectTypeField');
        const button = event.currentTarget.querySelector('button[type="submit"]');
        const [mode, assignmentGroup] = field.value.split(':');
        button.disabled = true;
        setStatus('Fachart wird gespeichert …');
        try {
            const response = await fetch('/set-curriculum-subject-type', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({subjectId: selectedSubject.id, mode, assignmentGroup: assignmentGroup || null})
            });
            if (!response.ok) throw new Error('type');
            selectedSubject.mode = mode;
            selectedSubject.assignmentGroup = assignmentGroup || null;
            setStatus('Fachart wurde gespeichert.', 'success');
        } catch {
            field.value = typeValue(selectedSubject);
            setStatus('Fachart konnte nicht gespeichert werden.', 'error');
        } finally {
            button.disabled = false;
        }
    }

    function bindDelete() {
        const button = document.getElementById('deleteSubjectButton');
        if (!button) return;
        button.addEventListener('click', () => {
            if (!selectedSubject) return;
            if (confirm('Fach wirklich löschen? Dies ist nur möglich, wenn keine Zuordnungen, Curriculuminhalte oder Leistungsdaten vorhanden sind.')) {
                deleteSubject(selectedSubject.id).then(response => {
                    if (response.ok) {
                        alert('Fach wurde gelöscht.');
                        window.location.href = '/manage_subjects';
                    } else {
                        alert('Das Fach konnte nicht gelöscht werden. Möglicherweise wird es noch verwendet.');
                    }
                }).catch(error => {
                    console.error('Error deleting subject:', error);
                    alert('Das Fach konnte nicht gelöscht werden. Möglicherweise wird es noch verwendet.');
                });
            }
        });
    }

    document.addEventListener('DOMContentLoaded', async () => {
        selectedSubject = await resolveSubject().catch(() => null);
        if (selectedSubject) {
            populateSubject(selectedSubject);
            loadSubjectType(selectedSubject).catch(() => {});
        } else {
            setStatus('Kein Fach ausgewählt.', 'error');
            console.error('No subject data found.');
        }
        document.getElementById('subjectTypeForm')?.addEventListener('submit', saveSubjectType);
        bindDelete();
    });
})();
