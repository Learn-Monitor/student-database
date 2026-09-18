(function() {
    let allStudents = [];

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
            .filter(student => classId === -1 || studentClassId(student) === classId)
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
                alert("An error occured while trying to change the student's graduation");
            }
        });
        return select;
    }

    function renderStudentRow(student) {
        const row = document.createElement("tr");
        appendCell(row, "student-last-name", studentLastName(student));
        appendCell(row, "student-first-name", studentFirstName(student));
        appendCell(row, "student-class", studentClassLabel(student));
        const graduationCell = appendCell(row, "student-graduation-level", "");
        graduationCell.appendChild(createGraduationSelect(student));

        const actionCell = appendCell(row, "student-action", "");
        const editButton = document.createElement("button");
        editButton.type = "button";
        editButton.className = "edit-student";
        editButton.textContent = "Bearbeiten";
        editButton.addEventListener("click", () => viewStudent(student.id));
        actionCell.appendChild(editButton);
        return row;
    }

    function renderStudents() {
        const body = document.getElementById("studentTableBody");
        const rows = filteredStudents().map(renderStudentRow);
        body.replaceChildren(...rows);
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
        setupDownloads();
        const [students, classes, subjects] = await Promise.all([
            fetchJson("/students"),
            fetchClasses(),
            fetchAllSubjects()
        ]);
        allStudents = Array.isArray(students) ? students : [];
        populateClassFilter((Array.isArray(classes) ? classes : []).filter(cls => Number(cls.id ?? cls.classId) !== 0 && cls.active !== false));
        populateSubjectSelect("subjectSelect", Array.isArray(subjects) ? subjects : []);
        document.getElementById("classSelect").addEventListener("change", renderStudents);
        document.getElementById("studentFilter").addEventListener("input", renderStudents);
        renderStudents();
    });
})();
