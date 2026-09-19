(() => {
    const NO_SELECTION = "Keine Lehrkraft ausgewählt.";
    const LOAD_ERROR = "Die Lehrkraft konnte nicht geladen werden.";
    let initialized = false;

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
        document.getElementById("teacher-back-link").hidden = false;
    }

    function fillTeacherForm(teacher) {
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

    document.addEventListener("DOMContentLoaded", () => {
        if (initialized) return;
        initialized = true;
        bindConfirmation();
        const id = selectedTeacherId();
        if (!id) {
            showError(NO_SELECTION);
            return;
        }
        loadTeacher(id);
    });
})();
