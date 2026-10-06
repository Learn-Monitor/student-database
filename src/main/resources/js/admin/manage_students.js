(function() {
    let allStudents = [];
    let sortColumn = 'lastName';
    let sortDirection = 'ascending';

    function setAdminStatus(message, state = '') {
        const status = document.getElementById('student-admin-status');
        if (!status) return;
        status.textContent = message;
        status.dataset.state = state;
    }

    function currentStatus() {
        return document.getElementById("studentStatus").value;
    }

    function activeStatus() {
        return currentStatus() === "active";
    }

    function studentFirstName(student) {
        if (student.firstName) return student.firstName;
        const name = student.name || "";
        return name.split(" ").slice(0, -1).join(" ") || name;
    }

    function studentLastName(student) {
        if (student.lastName) return student.lastName;
        const name = student.name || "";
        return name.split(" ").slice(-1)[0] || "";
    }

    function studentLogin(student) {
        return student.email || student.login || student.username || "";
    }

    function studentClass(student) {
        return student.schoolClass || student.class || {};
    }

    function studentClassId(student) {
        const cls = studentClass(student);
        const id = cls.id ?? cls.classId ?? student.classId;
        return Number(id ?? 0);
    }

    function studentClassLabel(student) {
        const cls = studentClass(student);
        return cls.label || cls.name || student.room || "";
    }

    function compareStudents(a, b) {
        const primaryValues = sortColumn === 'firstName'
            ? [studentFirstName(a), studentFirstName(b)]
            : [studentLastName(a), studentLastName(b)];
        const primary = primaryValues[0].localeCompare(primaryValues[1], "de", {sensitivity: "base"}) * (sortDirection === 'ascending' ? 1 : -1);
        if (primary !== 0) return primary;
        const last = studentLastName(a).localeCompare(studentLastName(b), "de", {sensitivity: "base"});
        if (last !== 0) return last;
        return studentFirstName(a).localeCompare(studentFirstName(b), "de", {sensitivity: "base"});
    }

    function matchesSearch(student, query) {
        if (!query) return true;
        const haystack = [
            studentFirstName(student),
            studentLastName(student),
            studentLogin(student),
            studentClassLabel(student)
        ].join(" ").toLocaleLowerCase("de");
        return haystack.includes(query.toLocaleLowerCase("de"));
    }

    function selectedClassId() {
        return Number(document.getElementById("classSelect").value);
    }

    function filteredStudents() {
        const classId = selectedClassId();
        const query = document.getElementById("studentFilter").value || "";
        return allStudents
            .filter(student => !activeStatus() || classId === -1 || studentClassId(student) === classId)
            .filter(student => matchesSearch(student, query))
            .sort(compareStudents);
    }

    function appendCell(row, className, text) {
        const cell = document.createElement("td");
        cell.className = className;
        cell.textContent = text;
        row.appendChild(cell);
        return cell;
    }

    function createGraduationText(student) {
        return graduationLevels[Number(student.graduationLevel)] || "";
    }

    function createGraduationSelect(student) {
        const select = document.createElement("select");
        for (let i = 0; i < graduationLevels.length; i++) {
            const option = document.createElement("option");
            option.value = String(i);
            option.textContent = graduationLevels[i];
            option.selected = i === Number(student.graduationLevel);
            select.appendChild(option);
        }
        select.addEventListener("change", async event => {
            const previous = student.graduationLevel;
            const next = Number(event.target.value);
            const result = await changeGraduationLevel(student.id, next);
            if (result.ok) {
                student.graduationLevel = next;
            } else {
                select.value = String(previous);
                setAdminStatus("Die Abschlussstufe konnte nicht geändert werden.", 'error');
            }
        });
        return select;
    }

    async function archiveStudent(student) {
        if (!confirm("Schüler wirklich archivieren? Der Schüler kann sich danach nicht mehr anmelden. Leistungsdaten und Zuordnungen bleiben erhalten.")) {
            return;
        }
        const response = await post("/archive-student", {id: student.id});
        if (response.ok) {
            await loadStudents();
            setAdminStatus('Schüler wurde archiviert.', 'success');
        } else {
            setAdminStatus("Der Schüler konnte nicht archiviert werden.", 'error');
        }
    }

    async function reactivateStudent(student) {
        if (!confirm("Schüler wieder aktivieren? Der Schüler kann sich anschließend wieder mit seinen vorhandenen Zugangsdaten anmelden.")) {
            return;
        }
        const response = await post("/reactivate-student", {id: student.id});
        if (response.ok) {
            await loadStudents();
            setAdminStatus('Schüler wurde wieder aktiviert.', 'success');
        } else {
            setAdminStatus("Der Schüler konnte nicht wiederhergestellt werden.", 'error');
        }
    }

    function renderStudentRow(student) {
        const row = document.createElement("tr");
        appendCell(row, "student-last-name", studentLastName(student));
        appendCell(row, "student-first-name", studentFirstName(student));
        appendCell(row, "student-class", studentClassLabel(student));
        const graduationCell = appendCell(row, "student-graduation-level", "");
        if (activeStatus()) {
            graduationCell.appendChild(createGraduationSelect(student));
        } else {
            graduationCell.textContent = createGraduationText(student);
        }

        const actionCell = appendCell(row, "student-action", "");
        if (activeStatus()) {
            const editButton = document.createElement("button");
            editButton.type = "button";
            editButton.className = "edit-student";
            editButton.textContent = "Bearbeiten";
            editButton.addEventListener("click", () => viewStudent(student.id));
            actionCell.appendChild(editButton);

            const archiveButton = document.createElement("button");
            archiveButton.type = "button";
            archiveButton.className = "archive-student";
            archiveButton.textContent = "Archivieren";
            archiveButton.addEventListener("click", () => archiveStudent(student));
            actionCell.appendChild(archiveButton);
        } else {
            const reactivateButton = document.createElement("button");
            reactivateButton.type = "button";
            reactivateButton.className = "reactivate-student";
            reactivateButton.textContent = "Wiederherstellen";
            reactivateButton.addEventListener("click", () => reactivateStudent(student));
            actionCell.appendChild(reactivateButton);
        }
        if (activeStatus()) {
            const passwordButton = document.createElement("button");
            passwordButton.type = "button";
            passwordButton.className = "reset-student-password";
            passwordButton.textContent = "Passwort neu setzen";
            passwordButton.addEventListener("click", () => openPasswordDialog(student));
            actionCell.appendChild(passwordButton);
        }
        return row;
    }

    function openPasswordDialog(student) {
        const dialog = document.getElementById('student-password-dialog');
        const form = document.getElementById('student-password-form');
        const password = document.getElementById('student-new-password');
        const confirmation = document.getElementById('student-password-confirmation');
        const error = document.getElementById('student-password-error');
        document.getElementById('student-password-target').textContent = `${studentFirstName(student)} ${studentLastName(student)} (${studentLogin(student)})`;
        password.value = ''; confirmation.value = ''; error.textContent = '';
        form.onsubmit = async event => {
            event.preventDefault();
            error.textContent = '';
            if (!form.reportValidity()) return;
            if (password.value !== confirmation.value) { error.textContent = 'Die Passwörter stimmen nicht überein.'; return; }
            if (!window.confirm('Passwort für dieses Konto wirklich neu setzen?')) return;
            const response = await post('/admin-reset-password', {targetType: 'student', targetId: student.id, password: password.value, passwordConfirmation: confirmation.value});
            if (!response.ok) { error.textContent = 'Das Passwort konnte nicht gespeichert werden.'; return; }
            closePasswordDialog(); setAdminStatus('Passwort wurde neu gesetzt.', 'success');
        };
        document.getElementById('student-password-cancel').onclick = closePasswordDialog;
        if (typeof dialog.showModal === 'function') dialog.showModal(); else dialog.setAttribute('open', '');
        password.focus();
    }

    function closePasswordDialog() {
        const dialog = document.getElementById('student-password-dialog');
        if (typeof dialog.close === 'function') dialog.close(); else dialog.removeAttribute('open');
    }

    function bindSort() {
        document.querySelectorAll('#studentTable [data-sort]').forEach(button => button.addEventListener('click', () => {
            const key = button.dataset.sort;
            sortDirection = sortColumn === key && sortDirection === 'ascending' ? 'descending' : 'ascending';
            sortColumn = key;
            document.querySelectorAll('#studentTable th').forEach(th => th.removeAttribute('aria-sort'));
            button.closest('th').setAttribute('aria-sort', sortDirection);
            renderStudents();
        }));
    }

    function renderStudents() {
        const body = document.getElementById("studentTableBody");
        const rows = filteredStudents().map(renderStudentRow);
        body.replaceChildren(...rows);
    }

    async function loadStudents() {
        setAdminStatus('Schüler werden geladen …');
        try {
            const students = await fetchJson(activeStatus() ? "/students" : "/archived-students");
            allStudents = Array.isArray(students) ? students : [];
            renderStudents();
            setAdminStatus(`${allStudents.length} Schüler geladen.`);
        } catch {
            allStudents = [];
            renderStudents();
            setAdminStatus('Die Schülerliste konnte nicht geladen werden.', 'error');
        }
    }

    function updateClassFilterState() {
        const classSelect = document.getElementById("classSelect");
        const classLabel = document.querySelector('label[for="classSelect"]');
        const archived = !activeStatus();
        classSelect.disabled = archived;
        classSelect.hidden = archived;
        if (classLabel) classLabel.hidden = archived;
    }

    function populateClassFilter(classes) {
        const classSelect = document.getElementById("classSelect");
        const options = [
            {id: -1, label: "Alle Klassen"},
            {id: 0, label: "Nicht zugeordnet"},
            ...classes
        ].map(cls => {
            const option = document.createElement("option");
            option.value = String(cls.id ?? cls.classId);
            option.textContent = cls.label || cls.name;
            return option;
        });
        classSelect.replaceChildren(...options);
    }

    function setupDownloads() {
        document.getElementById("add-students-form").addEventListener("submit", async event => {
            event.preventDefault();
            const form = event.target;
            postDataAndDownload("/add-students", form.elements.csv.value, "schueler.csv");
        });
        document.getElementById("download-grade-results-form").addEventListener("submit", async event => {
            event.preventDefault();
            const form = event.target;
            const grade = Number(form.elements.grade.value);
            const subjectId = Number(form.elements.subject.value);
            postDataAndDownload("/grade-results", JSON.stringify({grade, subjectId}), "schueler_ergebnisse_" + grade + "_" + subjectId + ".csv");
        });
    }

    document.addEventListener("DOMContentLoaded", async () => {
        if (new URLSearchParams(window.location.search).get("studentSaved") === "1") {
            setAdminStatus("Schülerprofil wurde erfolgreich gespeichert.", "success");
        }
        setupDownloads();
        bindSort();
        const [classes, subjects] = await Promise.all([
            fetchClasses(),
            fetchAllSubjects()
        ]);
        populateClassFilter((Array.isArray(classes) ? classes : []).filter(cls => Number(cls.id ?? cls.classId) !== 0 && cls.active !== false));
        populateSubjectSelect("subjectSelect", Array.isArray(subjects) ? subjects : []);
        document.getElementById("studentStatus").addEventListener("change", async () => {
            updateClassFilterState();
            await loadStudents();
        });
        document.getElementById("classSelect").addEventListener("change", renderStudents);
        document.getElementById("studentFilter").addEventListener("input", renderStudents);
        updateClassFilterState();
        await loadStudents();
    });
})();
