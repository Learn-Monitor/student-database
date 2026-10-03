(() => {
    const NO_SELECTION = "Keine Lehrkraft ausgewählt.";
    const LOAD_ERROR = "Die Lehrkraft konnte nicht geladen werden.";
    let initialized = false;
    let currentTeacher = null;

    function selectedTeacherId() {
        const raw = sessionStorage.getItem("selectedTeacherId");
        const id = Number(raw);
        return Number.isInteger(id) && id > 0 ? id : null;
    }

    function form() {
        return document.getElementById("teacher-profile-form");
    }

    function showError(message) {
        const profileForm = form();
        if (profileForm) {
            profileForm.hidden = true;
            [...profileForm.elements].forEach(element => element.disabled = true);
        }
        const messageElement = document.getElementById("teacher-load-message");
        messageElement.textContent = message;
        messageElement.hidden = false;
        document.getElementById('deleteTeacher').hidden = true;
    }

    function fillTeacherForm(teacher) {
        currentTeacher = teacher;
        document.getElementById("teacher-name").textContent = `${teacher.firstName || ""} ${teacher.lastName || ""}`.trim();
        document.getElementById("teacherId").value = String(teacher.id);
        document.getElementById("teacherFirstName").value = teacher.firstName || "";
        document.getElementById("teacherLastName").value = teacher.lastName || "";
        document.getElementById("teacherEmail").value = teacher.email || "";
        document.getElementById("teacherPassword").value = "";
    }

    async function loadTeacher(id) {
        try {
            const teachers = await fetchJson("/teachers");
            if (!Array.isArray(teachers)) {
                showError(LOAD_ERROR);
                return;
            }
            const teacher = teachers.find(candidate => Number(candidate.id) === id);
            if (!teacher) {
                showError(LOAD_ERROR);
                return;
            }
            fillTeacherForm(teacher);
        } catch (error) {
            showError(LOAD_ERROR);
        }
    }

    function bindConfirmation() {
        form().addEventListener("submit", event => {
            if (!confirm("Änderungen an dieser Lehrkraft speichern?")) {
                event.preventDefault();
            }
        });
    }

    function setDeleteStatus(message, state = '') {
        const status = document.getElementById('teacherDeleteStatus');
        status.textContent = message;
        status.dataset.state = state;
    }

    async function deletionRequest(action) {
        const response = await fetch('/delete-teacher', {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({id: selectedTeacherId(), action})
        });
        let data = null;
        try { data = await response.json(); } catch { /* render safe generic error */ }
        if (!response.ok) throw Object.assign(new Error(data?.message || 'Löschprüfung fehlgeschlagen.'), {data});
        return data;
    }

    function renderPreflight(result) {
        const button = document.getElementById('deleteTeacherButton');
        const list = document.getElementById('teacherDeleteReasons');
        list.replaceChildren();
        (result.groups || []).filter(group => group.count > 0).forEach(group => {
            const item = document.createElement('li'); item.textContent = `${group.count} · ${group.label}`; list.append(item);
        });
        if (result.protectedReason) { const item = document.createElement('li'); item.textContent = result.protectedReason; list.append(item); }
        button.disabled = !result.deletable;
        setDeleteStatus(result.deletable ? 'Dieses Lehrkraftkonto ist unbenutzt und kann gelöscht werden.' : 'Diese Lehrkraft kann derzeit nicht gelöscht werden.', result.deletable ? 'success' : 'error');
    }

    async function loadPreflight() {
        const button = document.getElementById('deleteTeacherButton');
        button.disabled = true;
        try { renderPreflight(await deletionRequest('preflight')); }
        catch { setDeleteStatus('Die sichere Löschprüfung ist fehlgeschlagen. Löschen ist deshalb gesperrt.', 'error'); }
    }

    function bindDelete() {
        const button = document.getElementById('deleteTeacherButton');
        const confirmation = document.getElementById('teacherDeleteConfirmation');
        button.addEventListener('click', () => {
            if (button.disabled || !currentTeacher) return;
            document.getElementById('teacherDeleteName').textContent = `${currentTeacher.firstName || ''} ${currentTeacher.lastName || ''} (${currentTeacher.email || ''})`;
            confirmation.hidden = false;
            document.getElementById('confirmDeleteTeacher').focus();
        });
        document.getElementById('cancelDeleteTeacher').addEventListener('click', () => { confirmation.hidden = true; button.focus(); });
        document.getElementById('confirmDeleteTeacher').addEventListener('click', async event => {
            event.currentTarget.disabled = true; button.disabled = true;
            setDeleteStatus('Lehrkraft wird sicher gelöscht …');
            try {
                await deletionRequest('delete');
                sessionStorage.removeItem('selectedTeacherId');
                setDeleteStatus('Lehrkraft wurde gelöscht.');
                window.location.assign('/manage_teachers');
            } catch (error) {
                if (error.data?.preflight) renderPreflight(error.data.preflight);
                setDeleteStatus(error.message || 'Lehrkraft konnte nicht gelöscht werden.', 'error');
                confirmation.hidden = true;
                event.currentTarget.disabled = false;
            }
        });
    }

    function bindArchive() {
        const archive = document.getElementById('archiveTeacherButton');
        const restore = document.getElementById('restoreTeacherButton');
        const request = action => fetch('/delete-teacher', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({id:selectedTeacherId(), action})});
        archive?.addEventListener('click', async () => { archive.disabled=true; try { const response=await request('archive'); if(!response.ok) throw Error(); archive.hidden=true; restore.hidden=false; setDeleteStatus('Lehrkraft archiviert. Historie und bestehende Verbindungen bleiben erhalten.','success'); } catch { setDeleteStatus('Lehrkraft konnte nicht archiviert werden.','error'); archive.disabled=false; } });
        restore?.addEventListener('click', async () => { restore.disabled=true; try { const response=await request('restore'); if(!response.ok) throw Error(); restore.hidden=true; archive.hidden=false; archive.disabled=false; setDeleteStatus('Lehrkraft wiederhergestellt.','success'); } catch { setDeleteStatus('Lehrkraft konnte nicht wiederhergestellt werden.','error'); restore.disabled=false; } });
    }

    document.addEventListener("DOMContentLoaded", () => {
        if (initialized) return;
        initialized = true;
        bindConfirmation();
        bindDelete();
        bindArchive();
        const id = selectedTeacherId();
        if (!id) {
            showError(NO_SELECTION);
            return;
        }
        loadTeacher(id).then(() => {
            if (currentTeacher) {
                document.getElementById('teacherDeleteName').textContent = `${currentTeacher.firstName || ''} ${currentTeacher.lastName || ''} (${currentTeacher.email || ''})`;
                loadPreflight();
            }
        });
    });
})();
