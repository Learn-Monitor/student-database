(function() {
    const profileGraduationLevels = Array.isArray(window.graduationLevels)
        ? window.graduationLevels
        : ['Neustarter', 'Starter', 'Durchstarter', 'Lernprofi'];

    function studentClassId(student) {
        const cls = student.schoolClass || student.class || {};
        const id = cls.id ?? cls.classId ?? student.classId;
        return Number(id ?? 0);
    }

    function studentLogin(student) {
        return student.email || student.login || student.username || "";
    }

    function selectedStudentId() {
        const raw = sessionStorage.getItem("selectedStudentId");
        const id = Number(raw);
        if (!raw || !Number.isInteger(id) || id <= 0) {
            return null;
        }
        return id;
    }

    function setFormDisabled(disabled) {
        const form = document.getElementById("adminStudentForm");
        for (const element of form.elements) {
            element.disabled = disabled;
        }
        form.hidden = disabled;
    }

    function showMessage(message) {
        const messageElement = document.getElementById("adminStudentMessage");
        const backLink = document.getElementById("adminStudentBackLink");
        messageElement.textContent = message;
        messageElement.hidden = false;
        backLink.hidden = false;
        setFormDisabled(true);
    }

    function showInlineMessage(message, state = '') {
        const messageElement = document.getElementById("adminStudentMessage");
        messageElement.textContent = message;
        messageElement.dataset.state = state;
        messageElement.hidden = false;
    }

    function addOption(select, value, label, selected) {
        const option = document.createElement("option");
        option.value = String(value);
        option.textContent = label;
        option.selected = selected;
        select.appendChild(option);
    }

    async function fetchAdminStudentData(id) {
        const response = await fetch("/admin-student-data", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({studentId: id})
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return await response.json();
    }

    async function fetchAdminClasses() {
        const response = await fetch("/classes");
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return await response.json();
    }

    function populateGraduationLevels(student) {
        const select = document.getElementById("adminStudentLevel");
        const currentLevel = Number(student.graduationLevel ?? 0);
        const options = [];
        for (let i = 0; i < profileGraduationLevels.length; i++) {
            const option = document.createElement("option");
            option.value = String(i);
            option.textContent = profileGraduationLevels[i];
            option.selected = i === currentLevel;
            options.push(option);
        }
        select.replaceChildren(...options);
    }

    async function populateClasses(student) {
        const select = document.getElementById("adminStudentClass");
        const currentClassId = studentClassId(student);
        const classes = await fetchAdminClasses();
        const activeClasses = (Array.isArray(classes) ? classes : [])
            .filter(cls => Number(cls.id ?? cls.classId) !== 0 && cls.active !== false);
        select.replaceChildren();
        addOption(select, 0, "Nicht zugeordnet", currentClassId === 0);
        for (const cls of activeClasses) {
            const id = Number(cls.id ?? cls.classId);
            addOption(select, id, cls.label || cls.name || "", id === currentClassId);
        }
    }

    function fillForm(student, id) {
        document.getElementById("adminStudentId").value = String(student.id ?? id);
        document.getElementById("adminStudentFirst").value = student.firstName || "";
        document.getElementById("adminStudentLast").value = student.lastName || "";
        document.getElementById("adminStudentEmail").value = studentLogin(student);
        document.getElementById("adminStudentPassword").value = "";
        populateGraduationLevels(student);
    }

    function setupSubmitConfirmation() {
        const form = document.getElementById("adminStudentForm");
        form.addEventListener("submit", async event => {
            event.preventDefault();
            if (!form.reportValidity()) return;
            if (!confirm("Änderungen an diesem Schüler speichern?")) return;
            const submit = form.querySelector('button[type="submit"]');
            submit.disabled = true;
            try {
                const response = await fetch(form.action, {
                    method: "POST",
                    body: new URLSearchParams(new FormData(form)),
                    headers: {"Content-Type": "application/x-www-form-urlencoded"}
                });
                if (!response.ok) {
                    let message = "Schülerprofil konnte nicht gespeichert werden.";
                    try {
                        const body = await response.text();
                        if (body.trim()) message = body.replace(/<[^>]*>/g, "").trim() || message;
                    } catch (_) { /* keep the safe fallback */ }
                    showInlineMessage(message, "error");
                    return;
                }
                window.location.href = "/manage_students?studentSaved=1";
            } catch (_) {
                showInlineMessage("Schülerprofil konnte wegen eines Verbindungsfehlers nicht gespeichert werden.", "error");
            } finally {
                submit.disabled = false;
            }
        });
        document.getElementById("adminStudentCancel").addEventListener("click", () => {
            window.location.href = "/manage_students";
        });
    }

    document.addEventListener("DOMContentLoaded", async () => {
        setupSubmitConfirmation();
        const id = selectedStudentId();
        if (id === null) {
            showMessage("Kein Schüler ausgewählt.");
            return;
        }

        try {
            const student = await fetchAdminStudentData(id);
            if (!student || typeof student !== "object") {
                showMessage("Der Schüler konnte nicht geladen werden.");
                return;
            }
            fillForm(student, id);
            await populateClasses(student);
        } catch (error) {
            showMessage("Der Schüler konnte nicht geladen werden.");
        }
    });
})();
