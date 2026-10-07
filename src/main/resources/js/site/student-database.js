async function fetchJson(url, options) {
    const res = await fetch(url, options);
    if (res.ok){
        return await res.json();
    }
}
async function fetchJsonStrict(url, options) {
    const res = await fetch(url, options);
    let payload;
    try {
        payload = await res.json();
    } catch (_) {
        payload = null;
    }
    if (!res.ok) {
        const error = new Error(payload && payload.message ? payload.message : `HTTP ${res.status}`);
        error.status = res.status;
        error.code = payload && payload.error ? payload.error : null;
        throw error;
    }
    return payload;
}
async function getJson(url) {
    return await fetchJson(url);
}
async function getJsonWithPost(url, data) {
    return await fetchJson(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    });
}
async function post(url, data) {
    return await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    });
}
async function postStrict(url, data) {
    return await fetchJsonStrict(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    });
}
function openUrlWithPostParams(url, params) {
    const form = document.createElement("form");
    form.setAttribute("method", "post");
    form.setAttribute("action", url);

    Object.keys(params).forEach((key) => {
        const input = document.createElement("input");
        input.setAttribute("type", "hidden")
        input.setAttribute("name", key)
        input.setAttribute("value", params[key])
        form.appendChild(input)
    })

    const submitButton = document.createElement("button")
    submitButton.setAttribute("type", "submit")
    
    form.appendChild(submitButton)
    document.getElementsByTagName("body")[0].appendChild(form)

    submitButton.click()
}
async function fetchClasses() {
    const classes = await fetchJson('/classes');
    return classes;
}
async function fetchMyClasses() {
    const classes = await fetchJson('/myclasses');
    return classes;
}
async function fetchTeacherClasses(teacherId) {
    const classes = await getJsonWithPost('/teacher-classes', { teacherId });
    return classes;
}
async function fetchSubjects(teacherId) {
    const subjects = await getJsonWithPost('/teacher-subjects', { teacherId });
    return subjects;
}
async function fetchMySubjects() {
    const subjects = await fetchJson('/mysubjects');
    return subjects;
}
async function fetchMyCurriculumSubjects() {
    return await getJsonWithPost('/my-curriculum-subjects', {});
}
async function fetchMyManagedSubjects() {
    return await fetchJsonStrict('/my-curriculum-subjects', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({})
    });
}
async function fetchMyCurriculumCatalog(subjectId) {
    return await fetchJsonStrict('/my-curriculum-catalog', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ subjectId })
    });
}
async function fetchStudentSubjects(studentId) {
    const subjects = await getJsonWithPost('/student-subjects', { studentId });
    return subjects;
}
async function fetchAllSubjects() {
    const subjects = await fetchJson('/subjects');
    return subjects;
}
async function fetchStudentData(studentId, endpoint = '/student-data') {
    return await getJsonWithPost(endpoint, { studentId });
}
async function fetchMyData() {
    return await fetchJson('/mydata');
}
async function fetchCurrentTopic(subjectId, studentId) {
    return await getJsonWithPost('/current-topic', { subjectId, studentId });
}
async function fetchMyCurrentTopic(subjectId) {
    return await getJsonWithPost('/current-topic', { subjectId });
}
async function fetchTopicList(subjectId, grade) {
    return await getJsonWithPost('/topic-list', { subjectId, grade });
}
async function fetchTasks(taskIds, studentId) {
    return await getJsonWithPost('/tasks', { ids: taskIds, studentId });
}
async function fetchPluginKeys() {
    return await getJson('/plugin-list');
}
async function fetchPlugin(pluginKey) {
    return await getJsonWithPost('/get-plugin', { key: pluginKey })
}
async function fetchPluginConfig(pluginKey) {
    return (await fetchPlugin(pluginKey)).config;
}

async function getStudents(classId) {
    return await getJsonWithPost('/student-list', { classId });
}
async function getStudentsBySubject(classId, subjectId) {
    return await getJsonWithPost('/student-list', { classId, subjectId });
}
async function searchPartner(subjectId) {
    return await getJsonWithPost('/search-partner', { subjectId });
}
async function addSubjectRequest(subjectId, subjectRequest) {
    return await post('/subject-request', { subjectId, subjectRequest });
}
async function removeSubjectRequest(subjectId, subjectRequest) {
    return await post('/subject-request', { subjectId, subjectRequest, remove: true });
}
function viewStudent(studentId) {
    // Add studentId to session storage
    sessionStorage.setItem('selectedStudentId', studentId);
    // Redirect to student dashboard
    window.location.href = `/student`;
}
function viewSubject(subject) {
    sessionStorage.setItem('currentSubject', JSON.stringify(subject));
    window.location.href = '/subject';
}
function viewTeacher(teacherId) {
    const id = Number(teacherId);
    if (!Number.isInteger(id) || id <= 0) return;
    sessionStorage.removeItem('currentTeacher');
    sessionStorage.setItem('selectedTeacherId', String(id));
    window.location.href = '/teacher';
}
function viewClass(cls) {
    sessionStorage.setItem('currentClass', JSON.stringify(cls));
    window.location.href = '/class';
}
async function changeGraduationLevel(studentId, newLevel) {
    return await post('/change-graduation-level', { studentId: studentId, graduationLevel: newLevel });
}
async function changeCurrentTopic(studentId, subjectId, topicId) {
    return await post('/change-current-topic', { studentId, subjectId, topicId });
}
async function completeTask(studentId, taskId) {
    return await post('/complete-task', { studentId, taskId });
}
async function cancelTask(studentId, taskId) {
    return await post('/cancel-task', { studentId, taskId });
}
async function lockTask(studentId, taskId) {
    return await post('/lock-task', { studentId, taskId });
}
async function reopenTask(studentId, taskId) {
    return await post('/reopen-task', { studentId, taskId });
}
async function beginTask(studentId, taskId) {
    return await post('/begin-task', { studentId, taskId });
}
async function togglePlugin(pluginKey) {
    return await post('/toggle-plugin', { key: pluginKey });
}
async function togglePluginSetting(pluginKey, setting) {
    return await post('/toggle-plugin-setting', { key: pluginKey + ":" + setting });
}
async function setPluginSetting(pluginKey, setting, value) {
    return await post('/set-plugin-setting', { key: pluginKey + ":" + setting, value });
}

async function deleteClass(classId) {
    return await post('/delete-class', { classId });
}
async function deleteSubject(subjectId) {
    return await post('/delete-subject', { id: subjectId });
}

// Events
function populateStudentRowEvent(row, student) {
    const event = new CustomEvent("populate-student-row", { detail: {row, student} });
    document.dispatchEvent(event);
}
function populatePartnerRowEvent(row, student) {
    const event = new CustomEvent("populate-partner-row", { detail: {row, student} });
    document.dispatchEvent(event);
}
function populateTeacherClassRowEvent(row, student) {
    const event = new CustomEvent("populate-teacher-class-row", { detail: { row, student } });
    document.dispatchEvent(event);
}
function teacherDashboardLoadEvent() {
    const event = new Event('teacher-dashboard-load');
    document.dispatchEvent(event);
}
function studentDashboardLoadEvent() {
    const event = new Event('student-dashboard-load');
    document.dispatchEvent(event);
}

function appendTextCell(row, className, value) {
    const cell = document.createElement('td');
    cell.className = className;
    cell.textContent = value == null ? '' : String(value);
    row.appendChild(cell);
}
function appendViewStudentCell(row, studentId) {
    const cell = document.createElement('td');
    cell.className = 'student-action';
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = 'Bearbeiten';
    button.addEventListener('click', () => viewStudent(studentId));
    cell.appendChild(button);
    row.appendChild(cell);
}
function buildStudentRow(row, student, includeProgress) {
    appendTextCell(row, 'student-name', student.name);
    if (includeProgress) {
        appendTextCell(row, 'student-current-task', student.currentTask || '–');
        appendTextCell(row, 'student-help', student.help ? 'Ja' : 'Nein');
        appendTextCell(row, 'student-experiment', student.experiment ? 'Ja' : 'Nein');
        appendTextCell(row, 'student-partner', student.partner ? 'Ja' : 'Nein');
        appendTextCell(row, 'student-test', student.test ? 'Ja' : 'Nein');
    } else {
        appendTextCell(row, 'student-graduation-level', graduationLevels[student.graduationLevel]);
    }
    appendViewStudentCell(row, student.id);
}

// Populating functions
async function populateTable(url, tableId, rowBuilder) {
    const data = await fetchJson(url);
    const tableBody = document.getElementById(tableId).getElementsByTagName('tbody')[0];
    tableBody.replaceChildren();
    data.forEach(item => {
        const newRow = tableBody.insertRow();
        rowBuilder(newRow, item);
    });
}
async function populateStudentTable(classId, tableId, rowBuilder) {
    const students = await getStudents(classId);
    const tableBody = document.getElementById(tableId).getElementsByTagName('tbody')[0];
    tableBody.replaceChildren();
    students.forEach(item => {
        const newRow = tableBody.insertRow();
        rowBuilder(newRow, item);
    });
}
async function populateSubjectStudentList(subjectSelectId, classSelectId, studentTableId) {
  const subjectSelect = document.getElementById(subjectSelectId);
  const classSelect = document.getElementById(classSelectId);
  const selectedClassId = classSelect.value;

  if (!selectedClassId) {
    subjectSelect.replaceChildren(); // clear previous options if no class is selected
    return;
  }

  const students = await getStudentsBySubject(Number(selectedClassId), Number(subjectSelect.value));

  const studentTable = document.getElementById(studentTableId).getElementsByTagName('tbody')[0];
  studentTable.replaceChildren(); // clear previous rows
  students.forEach(student => {
      const row = document.createElement('tr');
      buildStudentRow(row, student, true);
      studentTable.appendChild(row);
  });
}
async function populatePartnerSubjectStudentList(subjectId) {
    const studentTable = document.getElementById("studentTableBody");
    if (!studentTable) return;
    studentTable.replaceChildren(); // clear previous rows
    const status = document.getElementById('partnerSearchStatus');
    if (status) status.textContent = 'Partner werden gesucht …';
    let students;
    try {
        students = await searchPartner(subjectId);
    } catch (error) {
        const message = String(error?.message || '');
        if (status) status.textContent = message.includes('aktive Etappe')
            ? 'Wähle zuerst eine Etappe aus, bevor du die Partnersuche aktivierst.'
            : 'Die Partnersuche konnte nicht geladen werden. Bitte später erneut versuchen.';
        return;
    }
    if (!Array.isArray(students) || students.length === 0) {
        if (status) status.textContent = 'Für diese Etappe sind derzeit keine passenden Partner verfügbar.';
        return;
    }
    if (status) status.textContent = `${students.length} passende Partner gefunden.`;
    students.forEach(student => {
        const row = document.createElement('tr');
        const nameCell = document.createElement('td');
        nameCell.className = 'student-name';
        nameCell.textContent = student.name;
        row.appendChild(nameCell);
        populatePartnerRowEvent(row, student);
        studentTable.appendChild(row);
    });
}
async function populateTopicTable(tableId, subjectId, grade) {
    const table = document.getElementById(tableId).getElementsByTagName("tbody")[0];
    table.replaceChildren();
    const topics = await getJsonWithPost('/topic-list', { subjectId, grade});
    topics.forEach(topic => {
        const row = document.createElement('tr');
        appendTextCell(row, 'topic-name', topic.name);
        appendTextCell(row, 'topic-ratio', topic.ratio);
        appendTextCell(row, 'topic-number', topic.number);
        appendTextCell(row, 'topic-tasks', topic.tasks.length);
        table.appendChild(row)
    })
}
function populateClassSelect(classSelect, classes) {
    classSelect.replaceChildren(); // clear previous options if any
    classes.forEach(cls => {
        const option = document.createElement('option');
        option.value = cls.classId || cls.id;
        option.textContent = cls.name || cls.label;
        classSelect.appendChild(option);
    });
}
async function populateSubjectSelect(subjectSelectId, subjects) {
    const subjectSelect = document.getElementById(subjectSelectId);
    subjectSelect.replaceChildren(); // Clear existing options
    subjects.forEach(function(subject) {
        const option = document.createElement('option');
        option.value = subject.id;
        option.textContent = subject.name;
        subjectSelect.appendChild(option);
    });
}
async function populateTopicSelect(topicSelect, subjectId, grade, currentTopic) {
    const topics = await fetchTopicList(subjectId, grade);
    topicSelect.replaceChildren(); // Clear existing options
    topics.forEach(t => {
      const option = document.createElement('option');
      option.value = t.id;
      option.textContent = `${t.name} (${t.number})`;
      option.selected = (currentTopic && currentTopic.id == t.id);
      topicSelect.appendChild(option);
    });
}
async function populateGradeSelect(gradeSelectId, subjectId) {
    const gradeSelect = document.getElementById(gradeSelectId);
    gradeSelect.replaceChildren();
    const grades = await getJsonWithPost('/grade-list', { subjectId });
    grades.forEach(grade => {
        const option = document.createElement('option');
        option.text = grade;
        option.value = grade;
        gradeSelect.appendChild(option)
    })
}
async function populateSubjectList(subjectListId, classId) {
    const subjectList = document.getElementById(subjectListId);
    const subjects = await fetchJson("/class-subjects", {
        method: 'POST',
        body: JSON.stringify({ classId }),
        headers: {
            'Content-Type': 'application/json'
        }
    });
    subjectList.replaceChildren(); // Clear existing items
    subjects.forEach(function(subject) {
        const listItem = document.createElement('li');
        listItem.textContent = subject.name;
        subjectList.appendChild(listItem);
    });
}
async function populateGradeList(listId, subjectId) {
    const list = document.getElementById(listId);
    list.replaceChildren();
    const grades = await getJsonWithPost('/grade-list', { subjectId });
    grades.forEach(grade => {
        const li = document.createElement("li");
        li.textContent = grade;
        li.style.cursor = 'pointer';
        li.title = 'Klicken, um die Klasenstufe zu entfernen';
        li.addEventListener('click', (e) => {
            if (confirm('Soll die Klassenstufe wirklich von diesem Fach entfernt werden?')) {
                openUrlWithPostParams('/delete-grade-from-subject', {
                    "subject": subjectId,
                    "grade": grade
                })
            }
        })
        list.appendChild(li)
    });
}
async function postDataAndDownload(url, data, filename) {
    const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'text/plain' },
        body: data
    });

    if (response.ok) {
        const blob = await response.blob();
        const disposition = response.headers.get('Content-Disposition');
        if (disposition && disposition.includes('filename=')) {
            filename = disposition.split('filename=')[1].split(';')[0].trim();
        }
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        window.location.reload()
    } else if (response.status === 401) {
        alert("Nicht autorisiert! Bitte melden Sie sich an.");
        window.location.href = "/login";
    } else {
        alert("Fehler beim Hochladen der Datei!");
    }
}
function createList(items, textBuilder, labelText, onClick) {
    const label = document.createElement('h4');
    label.textContent = labelText;
    const list = document.createElement('ul');
    items.forEach(item => {
        const li = document.createElement('li');
        li.textContent = textBuilder(item);
        if (typeof onClick === 'function') {
            li.style.cursor = 'pointer';
            li.addEventListener('click', () => onClick(item, li));
        }
      list.appendChild(li);
    });
    return { label, list };
}
function createTaskList(tasks, titleText, onClick) {
    const levels = {1:'Wanderer', 2:'Bergsteiger', 3:'Gipfelstürmer'};
    return createList(tasks, task => `${task.number} ${decodeEntities(task.name)} · ${levels[task.niveau] || 'Niveau ' + task.niveau} (Gesamtanteil: ${Math.round(task.ratio * 10000) / 100}%)`, titleText, onClick);
}
async function buildTeacherDashboard(classes, subjects) {
    async function onClassChange(event) {
        populateStudentTable(Number(event.target.value), "studentTable", (row, student) => {
            buildStudentRow(row, student, false);
            populateTeacherClassRowEvent(row, student);
        });
    }

    const classSelect = document.getElementById('classSelect');
    const subjectClassSelect = document.getElementById('classSelectSubject');
    populateClassSelect(classSelect, classes);
    populateClassSelect(subjectClassSelect, classes);
    classSelect.addEventListener('change', onClassChange);
    subjectClassSelect.addEventListener('change', (_) => populateSubjectStudentList('subjectSelect', 'classSelectSubject', 'subjectStudentTable'));
    onClassChange({ target: classSelect }); // Trigger initial load

    const subjectSelect = document.getElementById('subjectSelect');
    populateSubjectSelect('subjectSelect', subjects);
    subjectSelect.addEventListener('change', (_) => populateSubjectStudentList('subjectSelect', 'classSelectSubject', 'subjectStudentTable'));
    populateSubjectStudentList('subjectSelect', 'classSelectSubject', 'subjectStudentTable'); // Trigger initial load

    teacherDashboardLoadEvent();
}
function createRequestButton(subject, type, label, readOnly, options = {}) {
    if (readOnly) return document.createTextNode('');
    const btn = document.createElement('button');
    btn.textContent = label;
    const managed = options.managed === true;

    // Helper to check if this request is active
    function isActive() {
        return (
            studentData.currentRequests &&
            studentData.currentRequests[subject.id] &&
            studentData.currentRequests[subject.id].includes(type)
        );
    }

    // Set initial state
    function updateButton() {
        if (isActive()) {
          btn.classList.add('active-request');
        } else {
          btn.classList.remove('active-request');
        }
        // A stale request may still be removed, but new requests require an active stage.
        btn.disabled = managed && !options.activeStage && !isActive();
        if (btn.disabled) btn.title = 'Wähle zuerst eine aktive Etappe.';
    }
    updateButton();

    btn.addEventListener('click', async () => {
        if (btn.disabled) return;
        try {
            if (isActive()) {
                const response = await removeSubjectRequest(subject.id, type);
                if (!response || !response.ok) return;
                // Update local state only after the server accepted the removal.
                if (studentData.currentRequests[subject.id]) {
                    studentData.currentRequests[subject.id] = studentData.currentRequests[subject.id].filter(t => t !== type);
                    if (studentData.currentRequests[subject.id].length === 0) {
                        delete studentData.currentRequests[subject.id];
                    }
                }
            } else {
                if (managed && !options.activeStage) return;
                const response = await addSubjectRequest(subject.id, type);
                if (!response || !response.ok) return;
                // Update local state only after the server accepted the addition.
                if (!studentData.currentRequests[subject.id]) {
                    studentData.currentRequests[subject.id] = [];
                }
                studentData.currentRequests[subject.id].push(type);
            }
        } catch (_) {
            // Keep the last confirmed server state visible after a network/API error.
            return;
        }
        updateButton();
    });

    return btn;
}
function setStudentInfo(studentData) {
    document.getElementById('student-name').textContent = `${studentData.firstName} ${studentData.lastName}`;
    document.getElementById('student-class').textContent = studentData.schoolClass.label;
    document.getElementById('student-email').textContent = studentData.email;
    const graduationLevels = ["Neustarter", "Starter", "Durchstarter", "Lernprofi"];
    document.getElementById('student-graduation').textContent = graduationLevels[studentData.graduationLevel];
}
function createPanel(header, bodyContent, loadCallback) {
    const panel = document.createElement('div');
    panel.className = 'panel';

    const headerElem = document.createElement('h3');
    headerElem.textContent = header;
    panel.appendChild(headerElem);

    const body = document.createElement('div');
    body.className = 'panel-body';
    body.appendChild(bodyContent);

    panel.appendChild(body);

    function refreshPanel() {
        bodyContent.replaceChildren();
        headerElem.click(); // Re-trigger the header click to close the panel
        panel.classList.remove('loaded'); // Reset loaded state
        headerElem.click(); // Re-trigger the header click to load tasks
    }
    headerElem.addEventListener('click', async () => {
        panel.classList.toggle('active');
        if (panel.classList.contains('loaded')) return;
        panel.classList.add('loaded');
        loadCallback(headerElem, bodyContent)
    });
    panel.refresh = refreshPanel;
    return panel;
}
function createLegacySubjectPanel(subject, studentData, teacherPerms) {
    const body = document.createElement('div');
    function createRequestButtons(body) {
        ['hilfe', 'partner', 'betreuung', 'gelingensnachweis'].forEach(type => {
            const label = {
                hilfe: 'Schüler braucht Hilfe',
                partner: 'Schüler sucht einen Partner',
                betreuung: 'Schüler braucht Betreuung für ein Experiment',
                gelingensnachweis: 'Schüler ist bereit für den Gelingensnachweis'
            }[type];

            const btn = createRequestButton(subject, type, label, teacherPerms);
            body.appendChild(btn);
        });
    }
    const studentId = studentData.id;

    const panel = createPanel(subject.name, body, async (header, body) => {
        body.replaceChildren(); // Clear previous content
        // Request buttons
        createRequestButtons(body);
        // Load current topic for this subject
        const topic = await fetchCurrentTopic(subject.id, studentId);
        if (!topic) {
            const empty = document.createElement('p');
            empty.textContent = 'Für dieses Fach ist aktuell kein Thema ausgewählt.';
            body.appendChild(empty);
            return;
        }

        const topicTitle = document.createElement('p');
        if (teacherPerms) {
            const topicLabel = document.createElement('label');
            topicLabel.htmlFor = 'topicSelect';
            topicLabel.textContent = 'Aktuelles Thema:';
            topicTitle.appendChild(topicLabel);
            const topicSelect = document.createElement('select');
            topicSelect.id = "topicSelect";
            populateTopicSelect(topicSelect, subject.id, studentData.schoolClass.grade, topic);
            topicSelect.addEventListener('change', async e => {
                result = await changeCurrentTopic(studentId, subject.id, Number(topicSelect.value));
                if (result.ok) {
                    panel.refresh();
                } else {
                    alert('Fehler beim Ändern des Themas')
                }
            });
            topicSelect.class = 'topicSelect';
            topicTitle.appendChild(topicSelect);
        } else {
            topicTitle.textContent = "Aktuelles Thema: " + topic.name
        }
        body.appendChild(topicTitle);

        // Filter tasks for the current topic
        const selectedTasks = studentData.selectedTasks.filter(
            task => task.topic && task.topic.id === topic.id
        );
        const completedTasks = studentData.completedTasks.filter(
            task => task.topic && task.topic.id === topic.id
        );
        const lockedTasks = studentData.lockedTasks.filter(
            task => task.topic && task.topic.id === topic.id
        );
        let allTasks = [];
        if (Array.isArray(topic.tasks) && topic.tasks.length > 0) {
            allTasks = await fetchTasks(topic.tasks, studentId) || [];
        }

        const otherTasks = allTasks.filter(
            task =>
                !selectedTasks.some(t => t.id === task.id) &&
                !completedTasks.some(t => t.id === task.id) &&
                !lockedTasks.some(t => t.id === task.id)
        );

        // Current stage (selectedTasks)
        const { label: selectedLabel, list: selectedList } = createTaskList(selectedTasks, 'Aktuelle Etappe:', async (task) => {
            if (teacherPerms){
                const action = window.prompt(
                    'Was möchten Sie tun?\n1: Als abgeschlossen markieren\n2: Aufgabe abbrechen\n3: Aufgabe sperren',
                    '1'
                );
                if (action === '1') {
                    // Move to completed
                    completeTask(studentId, task.id);
                    // Update local state
                    studentData.selectedTasks = studentData.selectedTasks.filter(t => t.id !== task.id);
                    studentData.completedTasks.push(task);
                } else if (action === '2') {
                    // Cancel task
                    cancelTask(studentId, task.id);
                    studentData.selectedTasks = studentData.selectedTasks.filter(t => t.id !== task.id);
                    // No need to push to completedTasks or otherTasks, UI will refresh
                } else if (action === '3') {
                    lockTask(studentId, task.id);
                    studentData.selectedTasks = studentData.selectedTasks.filter(t => t.id !== task.id);
                    studentData.lockedTasks.push(task)
                }
                panel.refresh(); // Refresh the panel to show updated tasks
            } else {
                // Cancel task
                cancelTask(studentId, task.id);
                studentData.selectedTasks = studentData.selectedTasks.filter(t => t.id !== task.id);
                // No need to push to completedTasks or otherTasks, UI will refresh
                panel.refresh(); // Refresh the panel to show updated tasks
            }
        });
        body.appendChild(selectedLabel);
        body.appendChild(selectedList);


        // Completed stages
        const { label: completedLabel, list: completedList } = createTaskList(completedTasks, 'Abgeschlossene Etappen:', async (task) => {
            if (teacherPerms && window.confirm('Soll diese Aufgabe wirklich wieder in die offenen Aufgaben verschoben werden?')) {
                reopenTask(studentId, task.id);
                studentData.completedTasks = studentData.completedTasks.filter(t => t.id !== task.id);
                // No need to push to otherTasks, UI will refresh
                panel.refresh(); // Refresh the panel to show updated tasks
            }
        });
        body.appendChild(completedLabel);
        body.appendChild(completedList);

        // locked stages
        const { label: lockedLabel, list: lockedList } = createTaskList(lockedTasks, 'Gesperrte Etappen:', async (task) => {
            if (teacherPerms && window.confirm('Soll diese Aufgabe wirklich wieder in die offenen Aufgaben verschoben werden?')) {
                reopenTask(studentId, task.id);
                studentData.lockedTasks = studentData.lockedTasks.filter(t => t.id !== task.id);
                // No need to push to otherTasks, UI will refresh
                panel.refresh(); // Refresh the panel to show updated tasks
            }
        });
        body.appendChild(lockedLabel);
        body.appendChild(lockedList);

        // Other stages
        const { label: otherLabel, list: otherList } = createTaskList(otherTasks, 'Weitere Etappen:', async (task) => {
            beginTask(studentId, task.id);
            studentData.selectedTasks.push(task);
            panel.refresh(); // Refresh the panel to show updated tasks
        });
        body.appendChild(otherLabel);
        body.appendChild(otherList);
    });
    return panel;
}
function curriculumLevelLabel(level) {
    return {1: 'Wanderer', 2: 'Bergsteiger', 3: 'Gipfelstürmer'}[level] || '';
}
function curriculumStageLabel(task) {
    const stage = task.stageNumber == null ? '' : `Etappe ${task.stageNumber} · `;
    const topic = task.topicName ? `${decodeEntities(task.topicName)} · ` : '';
    const level = curriculumLevelLabel(task.niveau);
    const levelText = level ? ` · ${level}` : '';
    const activeText = task.inProgress ? ' · Aktive Etappe' : '';
    return `${stage}${topic}${decodeEntities(task.name || '')} · ${task.tokens ?? 0} Münzen${levelText}${activeText}`;
}
function appendCurriculumStageSection(body, title, tasks, onClick) {
    if (!Array.isArray(tasks) || tasks.length === 0) return;
    const section = createList(tasks, curriculumStageLabel, title, onClick);
    body.appendChild(section.label);
    body.appendChild(section.list);
}
function renderManagedActiveStage(body, catalog) {
    const heading = document.createElement('h4');
    heading.textContent = 'Aktive Etappe';
    body.appendChild(heading);
    const active = catalog && catalog.activeStage;
    const message = document.createElement('p');
    if (!active) {
        message.textContent = 'Noch keine aktive Etappe gewählt.';
    } else {
        const allTasks = [...(catalog.centralTasks || []), ...(catalog.flexibleTasks || [])];
        const task = allTasks.find(candidate => Number(candidate.id) === Number(active.taskId));
        const topic = task && task.topicName ? ` · ${decodeEntities(task.topicName)}` : '';
        const level = active.niveau ? ` · ${curriculumLevelLabel(active.niveau)}` : '';
        message.textContent = `${decodeEntities(active.name || (task && task.name) || '')}${topic}${level}`;
    }
    body.appendChild(message);
}
function renderManagedSubjectPanel(body, subject, panel, catalog) {
    renderManagedActiveStage(body, catalog);
    const activeStage = catalog && catalog.activeStage;
    const requests = document.createElement('div');
    ['hilfe', 'partner', 'betreuung', 'gelingensnachweis'].forEach(type => {
        const label = {
            hilfe: 'Schüler braucht Hilfe',
            partner: 'Schüler sucht einen Partner',
            betreuung: 'Schüler braucht Betreuung für ein Experiment',
            gelingensnachweis: 'Schüler ist bereit für den Gelingensnachweis'
        }[type];
        requests.appendChild(createRequestButton(subject, type, label, false, {managed: true, activeStage}));
    });
    body.appendChild(requests);
    if (!activeStage) {
        const hint = document.createElement('p');
        hint.textContent = 'Wähle zuerst eine aktive Etappe.';
        body.appendChild(hint);
    }

    const centralOpen = (catalog.centralTasks || []).filter(task => task.active && !task.completed);
    const flexibleOpen = (catalog.flexibleTasks || []).filter(task => task.active && !task.completed);
    const completed = [...(catalog.centralTasks || []), ...(catalog.flexibleTasks || [])].filter(task => task.completed);
    const changeStage = async (path, task) => {
        try {
            await postStrict(path, {taskId: task.id});
            panel.refresh();
        } catch (error) {
            const failure = document.createElement('p');
            failure.textContent = error.message || 'Die Etappe konnte nicht aktiviert werden.';
            failure.className = 'curriculum-error';
            body.appendChild(failure);
        }
    };
    appendCurriculumStageSection(body, 'Freigegebene zentrale Etappen:', centralOpen,
        task => changeStage(task.inProgress ? '/cancel-task' : '/begin-task', task));
    appendCurriculumStageSection(body, 'Freigegebene flexible Etappen:', flexibleOpen,
        task => changeStage(task.inProgress ? '/cancel-flexible-task' : '/begin-flexible-task', task));
    if (centralOpen.length === 0 && flexibleOpen.length === 0) {
        const empty = document.createElement('p');
        empty.textContent = 'Für dieses Fach sind noch keine Etappen freigeschaltet.';
        body.appendChild(empty);
    }
    appendCurriculumStageSection(body, 'Abgeschlossene Etappen:', completed);
}
function createSubjectPanel(subject, studentData, teacherPerms, managedSubjectIds) {
    const managed = !teacherPerms && managedSubjectIds && typeof managedSubjectIds.has === 'function'
        && managedSubjectIds.has(Number(subject.id));
    const managedLookupFailed = !teacherPerms && managedSubjectIds === null;
    if (managed || managedLookupFailed) {
        const body = document.createElement('div');
        const panel = createPanel(subject.name, body, async (header, body) => {
            body.replaceChildren();
            if (managedLookupFailed) {
                const error = document.createElement('p');
                error.textContent = 'Der verwaltete Curriculum-Kontext konnte nicht geladen werden.';
                error.className = 'curriculum-error';
                body.appendChild(error);
                return;
            }
            try {
                const catalog = await fetchMyCurriculumCatalog(subject.id);
                renderManagedSubjectPanel(body, subject, panel, catalog);
            } catch (error) {
                const failure = document.createElement('p');
                failure.textContent = error.code === 'context_unassigned'
                    ? 'Für dieses Fach ist kein verwalteter Curriculum-Kontext zugeordnet.'
                    : error.message || 'Der Curriculum-Katalog konnte nicht geladen werden.';
                failure.className = 'curriculum-error';
                body.appendChild(failure);
            }
        });
        return panel;
    }
    return createLegacySubjectPanel(subject, studentData, teacherPerms);
}
function decodeEntities(str) {
    const txt = document.createElement("textarea");
    txt.innerHTML = str;
    return txt.value;
}
function loadStudentDashboard(studentData, subjects, teacherPerms, managedSubjects) { // Show student info
    setStudentInfo(studentData);

    // Show subjects
    const subjectList = document.getElementById('subject-list');
    const managedSubjectIds = teacherPerms ? undefined
        : managedSubjects === null ? null
        : new Set((managedSubjects || []).map(subject => Number(subject.id)));
    subjects.forEach(subject => {
        const panel = createSubjectPanel(subject, studentData, teacherPerms, managedSubjectIds);
        subjectList.appendChild(panel);
    });

    studentDashboardLoadEvent();
}
let plugin_panels = {}
function loadPluginSection(pluginKey) {
    return createPanel(pluginKey, document.createElement("div"), async (header, body) => {
        const plugin = await fetchPlugin(pluginKey);
        header.textContent = plugin.name;
        body.replaceChildren();
        for (const paragraph of String(plugin.description || '').split('\n')) {
            const p = document.createElement('p');
            p.textContent = paragraph;
            body.appendChild(p);
        }
        const table = document.createElement('table');
        const head = table.createTHead().insertRow();
        ['Key', 'Value', ''].forEach(value => appendTextCell(head, '', value));
        const tbody = table.createTBody();
        const addPluginRow = (name, value, action) => {
            const row = tbody.insertRow();
            appendTextCell(row, '', name);
            appendTextCell(row, '', value);
            const cell = row.insertCell();
            if (action) cell.appendChild(action);
        };
        const refresh = () => plugin_panels[plugin.id].refresh();
        const toggle = document.createElement('button');
        toggle.type = 'button'; toggle.textContent = 'Toggle';
        toggle.addEventListener('click', async () => { await togglePlugin(plugin.id); refresh(); });
        addPluginRow('ID', plugin.id, null);
        addPluginRow('Name', plugin.name, null);
        addPluginRow('Enabled', plugin.enabled, toggle);
        table.appendChild(tbody);
        body.appendChild(table);
        const settings = plugin.config.settings;
        settings.bools.forEach((b) => {
            const button = document.createElement('button');
            button.type = 'button'; button.textContent = 'Toggle';
            button.addEventListener('click', async () => { await togglePluginSetting(plugin.id, b.key); refresh(); });
            addPluginRow(b.name, b.value, button);
        });
        settings.ints.forEach((i) => {
            const input = document.createElement('input');
            input.type = 'number'; input.value = i.value;
            const button = document.createElement('button');
            button.type = 'button'; button.textContent = 'Set';
            button.addEventListener('click', async () => { await setPluginSetting(plugin.id, i.key, Number(input.value)); refresh(); });
            const action = document.createDocumentFragment(); action.append(input, button);
            addPluginRow(i.name, i.value, action);
        })
        settings.shortAnswers.forEach((s) => {
            const input = document.createElement('input');
            input.type = 'text'; input.value = s.value;
            const button = document.createElement('button');
            button.type = 'button'; button.textContent = 'Set';
            button.addEventListener('click', async () => { await setPluginSetting(plugin.id, s.key, input.value); refresh(); });
            const action = document.createDocumentFragment(); action.append(input, button);
            addPluginRow(s.name, s.value, action);
        })
    })
}
async function loadPluginsView(pluginContainer) {
    const plugins = fetchPluginKeys();
    (await plugins).forEach(async key => {
        const pluginSection = loadPluginSection(key);
        plugin_panels[key] = pluginSection;
        pluginContainer.appendChild(pluginSection);
    });
}
const graduationLevels = ["Neustarter", "Starter", "Durchstarter", "Lernprofi"];
let currentClass = JSON.parse(sessionStorage.getItem('currentClass'));
document.addEventListener('DOMContentLoaded', async () => {
    if (currentClass) {
        for (const element of document.getElementsByClassName('classId')) {
            element.textContent = currentClass.id;
            element.value = currentClass.id;
        }
        for (const element of document.getElementsByClassName('className')) {
            element.textContent = currentClass.label;
            element.value = currentClass.label;
        }
        for (const element of document.getElementsByClassName('classGrade')) {
            element.textContent = currentClass.grade;
            element.value = currentClass.grade;
        }
        const studentList = document.getElementById('studentTableBody');
        if (studentList) {
            populateStudentTable(currentClass.id, 'studentTable', (row, student) => {
                buildStudentRow(row, student, false);
                populateStudentRowEvent(row, student);
            })
        }
        if (document.getElementById('subjectSelect')) {
            populateSubjectSelect('subjectSelect', await fetchAllSubjects());
        }
    }
})
