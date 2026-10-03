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

    function setDeleteStatus(message, state = '') {
        const status = document.getElementById('subjectDeleteStatus');
        if (status) { status.textContent = message; status.dataset.state = state; }
    }

    async function requestDelete(action) {
        const response = await fetch('/delete-subject', {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({id: Number(selectedSubject.id), action})
        });
        let data = null;
        try { data = await response.json(); } catch { /* handled as an API error below */ }
        if (!response.ok) throw Object.assign(new Error(data?.message || 'Löschprüfung fehlgeschlagen.'), {data, status: response.status});
        return data;
    }

    function renderDeletePreflight(result) {
        const button = document.getElementById('deleteSubjectButton');
        const list = document.getElementById('subjectDeleteReasons');
        list.replaceChildren();
        (result.groups || []).filter(group => group.count > 0).forEach(group => {
            const item = document.createElement('li');
            item.textContent = `${group.count} · ${group.label}`;
            list.append(item);
        });
        if (result.protectedReason) {
            const item = document.createElement('li'); item.textContent = result.protectedReason; list.append(item);
        }
        button.disabled = !result.deletable;
        setDeleteStatus(result.deletable
            ? 'Dieses Fach ist unbenutzt und kann gelöscht werden.'
            : 'Dieses Fach kann derzeit nicht gelöscht werden.', result.deletable ? 'success' : 'error');
    }

    async function loadDeletePreflight() {
        const button = document.getElementById('deleteSubjectButton');
        button.disabled = true;
        setDeleteStatus('Löschbarkeit wird geprüft …');
        try { renderDeletePreflight(await requestDelete('preflight')); }
        catch { setDeleteStatus('Die sichere Löschprüfung ist fehlgeschlagen. Löschen ist deshalb gesperrt.', 'error'); }
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
        const confirmation = document.getElementById('subjectDeleteConfirmation');
        button.addEventListener('click', () => {
            if (!selectedSubject || button.disabled) return;
            document.getElementById('subjectDeleteName').textContent = selectedSubject.name || '';
            confirmation.hidden = false;
            document.getElementById('confirmDeleteSubject').focus();
        });
        document.getElementById('cancelDeleteSubject').addEventListener('click', () => { confirmation.hidden = true; button.focus(); });
        document.getElementById('confirmDeleteSubject').addEventListener('click', async event => {
            const confirmButton = event.currentTarget; confirmButton.disabled = true; button.disabled = true;
            setDeleteStatus('Fach wird sicher gelöscht …');
            try {
                await requestDelete('delete');
                setDeleteStatus('Fach wurde gelöscht.');
                sessionStorage.removeItem('currentSubject');
                window.location.assign('/manage_subjects');
            } catch (error) {
                if (error.data?.preflight) renderDeletePreflight(error.data.preflight);
                setDeleteStatus(error.message || 'Fach konnte nicht gelöscht werden.', 'error');
                confirmation.hidden = true;
                confirmButton.disabled = false;
            }
        });
    }

    function bindArchive() {
        const archive = document.getElementById('archiveSubjectButton');
        const restore = document.getElementById('restoreSubjectButton');
        const request = action => fetch('/delete-subject', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id:Number(selectedSubject.id), action})});
        archive?.addEventListener('click', async () => { archive.disabled=true; try { const response=await request('archive'); if(!response.ok) throw Error(); archive.hidden=true; restore.hidden=false; setDeleteStatus('Fach archiviert. Verbindungen und Lernstände bleiben erhalten.','success'); } catch { setDeleteStatus('Fach konnte nicht archiviert werden.','error'); archive.disabled=false; } });
        restore?.addEventListener('click', async () => { restore.disabled=true; try { const response=await request('restore'); if(!response.ok) throw Error(); restore.hidden=true; archive.hidden=false; archive.disabled=false; setDeleteStatus('Fach wiederhergestellt.','success'); } catch { setDeleteStatus('Fach konnte nicht wiederhergestellt werden.','error'); restore.disabled=false; } });
    }

    document.addEventListener('DOMContentLoaded', async () => {
        selectedSubject = await resolveSubject().catch(() => null);
        if (selectedSubject) {
            populateSubject(selectedSubject);
            loadSubjectType(selectedSubject).catch(() => {});
        } else {
            setStatus('Kein Fach ausgewählt.', 'error');
            document.getElementById('deleteSubject').hidden = true;
            console.error('No subject data found.');
        }
        document.getElementById('subjectTypeForm')?.addEventListener('submit', saveSubjectType);
        bindDelete();
        bindArchive();
        if (selectedSubject) loadDeletePreflight();
    });
})();
