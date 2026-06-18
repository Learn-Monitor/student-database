async function fetchJson(url, options) {
    const res = await fetch(url, options);
    if (res.ok){
        return await res.json();
    }
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
async function fetchStudentSubjects(studentId) {
    const subjects = await getJsonWithPost('/student-subjects', { studentId });
    return subjects;
}
async function fetchAllSubjects() {
    const subjects = await fetchJson('/subjects');
    return subjects;
}
async function fetchStudentData(studentId) {
    return await getJsonWithPost('/student-data', { studentId });
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
async function searchPartner(subjectId, topicId, classId, studentId) {
    return await getJsonWithPost('/search-partner', { subjectId, topicId, classId, studentId});
}
async function addSubjectRequest(subjectId, subjectRequest, studentId) {
    return await post('/subject-request', { subjectId, subjectRequest, studentId });
}
async function removeSubjectRequest(subjectId, subjectRequest, studentId) {
    return await post('/subject-request', { subjectId, subjectRequest, studentId, remove: true });
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
function viewTeacher(teacher) {
    sessionStorage.setItem('currentTeacher', JSON.stringify(teacher));
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
    return await post('/delete-class', { id: classId });
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

// Populating functions
async function populateTable(url, tableId, rowBuilder) {
    const data = await fetchJson(url);
    const tableBody = document.getElementById(tableId).getElementsByTagName('tbody')[0];
    tableBody.innerHTML = '';
    data.forEach(item => {
        const newRow = tableBody.insertRow();
        rowBuilder(newRow, item);
    });
}
async function populateStudentTable(classId, tableId, rowBuilder) {
    const students = await getStudents(classId);
    const tableBody = document.getElementById(tableId).getElementsByTagName('tbody')[0];
    tableBody.innerHTML = '';
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
    subjectSelect.innerHTML = ""; // clear previous options if no class is selected
    return;
  }

  const students = await getStudentsBySubject(Number(selectedClassId), Number(subjectSelect.value));

  const studentTable = document.getElementById(studentTableId).getElementsByTagName('tbody')[0];
  studentTable.innerHTML = ""; // clear previous rows
  students.forEach(student => {
      const row = document.createElement('tr');
      row.innerHTML = `
          <td class="student-name">${student.name}</td>
          <td class="student-help">${student.help ? "Ja" : "Nein"}</td>
          <td class="student-experiment">${student.experiment ? "Ja" : "Nein"}</td>
          <td class="student-partner">${student.partner ? "Ja" : "Nein"}</td>
          <td class="student-test">${student.test ? "Ja" : "Nein"}</td>
          <td class="student-action"><button onclick="viewStudent(${student.id})">Bearbeiten</button></td>
      `;
      studentTable.appendChild(row);
  });
}
async function populatePartnerSubjectStudentList(subjectId, studentData) {
    const topicId = (await fetchMyCurrentTopic(subjectId)).id;
    const classId = studentData.schoolClass.id;
    const studentId = studentData.id;

    const students = await searchPartner(subjectId, topicId, classId, studentId);

    const studentTable = document.getElementById("studentTableBody");
    studentTable.innerHTML = ""; // clear previous rows
    students.forEach(student => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td class="student-name">${student.name}</td>
        `;
        populatePartnerRowEvent(row, student);
        studentTable.appendChild(row);
    });
}
async function populateTopicTable(tableId, subjectId, grade) {
    const table = document.getElementById(tableId).getElementsByTagName("tbody")[0];
    table.innerHTML = '';
    const topics = await getJsonWithPost('/topic-list', { subjectId, grade});
    topics.forEach(topic => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td class="topic-name">${topic.name}</td>
            <td class="topic-ratio">${topic.ratio}</td>
            <td class="topic-number">${topic.number}</td>
            <td class="topic-tasks">${topic.tasks.length}</td>
        `
        table.appendChild(row)
    })
}
function populateClassSelect(classSelect, classes) {
    classSelect.innerHTML = ""; // clear previous options if any
    classes.forEach(cls => {
        const option = document.createElement('option');
        option.value = cls.classId || cls.id;
        option.textContent = cls.name || cls.label;
        classSelect.appendChild(option);
    });
}
async function populateSubjectSelect(subjectSelectId, subjects) {
    const subjectSelect = document.getElementById(subjectSelectId);
    subjectSelect.innerHTML = ''; // Clear existing options
    subjects.forEach(function(subject) {
        const option = document.createElement('option');
        option.value = subject.id;
        option.textContent = subject.name;
        subjectSelect.appendChild(option);
    });
}
async function populateTopicSelect(topicSelect, subjectId, grade, currentTopic) {
    const topics = await fetchTopicList(subjectId, grade);
    topicSelect.innerHTML = ''; // Clear existing options
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
    gradeSelect.innerHTML = '';
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
    subjectList.innerHTML = ''; // Clear existing items
    subjects.forEach(function(subject) {
        const listItem = document.createElement('li');
        listItem.textContent = subject.name;
        subjectList.appendChild(listItem);
    });
}
async function populateGradeList(listId, subjectId) {
    const list = document.getElementById(listId);
    list.innerHTML = '';
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
    return createList(tasks, task => `${task.number} ${decodeEntities(task.name)} (Niveau ${task.niveau}, Gesamtanteil: ${Math.round(task.ratio * 10000) / 100}%)`, titleText, onClick);
}
async function buildTeacherDashboard(classes, subjects) {
    async function onClassChange(event) {
        populateStudentTable(Number(event.target.value), "studentTable", (row, student) => {
            row.innerHTML = `
                <td class="student-name">${student.name}</td>
                <td class="student-graduation-level">${graduationLevels[student.graduationLevel]}</td>
                <td class="student-action"><button onclick="viewStudent(${student.id})">Bearbeiten</button></td>
            `;
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
function createRequestButton(subject, type, label) {
    const btn = document.createElement('button');
    btn.textContent = label;

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
    }
    updateButton();

    btn.addEventListener('click', async () => {
        if (isActive()) {
            await removeSubjectRequest(subject.id, type, studentData.id);
            // Update local state
            if (studentData.currentRequests[subject.id]) {
                studentData.currentRequests[subject.id] = studentData.currentRequests[subject.id].filter(t => t !== type);
                if (studentData.currentRequests[subject.id].length === 0) {
                    delete studentData.currentRequests[subject.id];
                }
            }
        } else {
            await addSubjectRequest(subject.id, type, studentData.id);
            // Update local state
            if (!studentData.currentRequests[subject.id]) {
                studentData.currentRequests[subject.id] = [];
            }
            studentData.currentRequests[subject.id].push(type);
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
        bodyContent.innerHTML = '';
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
function createSubjectPanel(subject, studentData, teacherPerms) {
    const body = document.createElement('div');
    function createRequestButtons(body) {
        ['hilfe', 'partner', 'betreuung', 'gelingensnachweis'].forEach(type => {
            const label = {
                hilfe: 'Schüler braucht Hilfe',
                partner: 'Schüler sucht einen Partner',
                betreuung: 'Schüler braucht Betreuung für ein Experiment',
                gelingensnachweis: 'Schüler ist bereit für den Gelingensnachweis'
            }[type];

            const btn = createRequestButton(subject, type, label);
            body.appendChild(btn);
        });
    }
    const studentId = studentData.id;

    const panel = createPanel(subject.name, body, async (header, body) => {
        body.innerHTML = ''; // Clear previous content
        // Request buttons
        createRequestButtons(body);
        // Load current topic for this subject
        const topic = await fetchCurrentTopic(subject.id, studentId);

        const topicTitle = document.createElement('p');
        if (teacherPerms) {
            topicTitle.innerHTML = `<label for="topicSelect">Aktuelles Thema:</label>`;
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
            allTasks = await fetchTasks(topic.tasks, studentId);
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
function decodeEntities(str) {
    const txt = document.createElement("textarea");
    txt.innerHTML = str;
    return txt.value;
}
function loadStudentDashboard(studentData, subjects, teacherPerms) { // Show student info
    setStudentInfo(studentData);

    // Show subjects
    const subjectList = document.getElementById('subject-list');
    subjects.forEach(subject => {
        const panel = createSubjectPanel(subject, studentData, teacherPerms);
        subjectList.appendChild(panel);
    });

    studentDashboardLoadEvent();
}
let plugin_panels = {}
function loadPluginSection(pluginKey) {
    return createPanel(pluginKey, document.createElement("div"), async (header, body) => {
        const plugin = await fetchPlugin(pluginKey);
        header.textContent = plugin.name;
        body.innerHTML = `
            <p>${plugin.description.replace(/\n/g, "</p><p>")}</p>
            <table>
                <thead>
                    <th>Key</th>
                    <th>Value</th>
                    <th/>
                </thead>
                <tbody>
                    <tr><td>ID</td><td>${plugin.id}</td><td/></tr>
                    <tr><td>Name</td><td>${plugin.name}</td><td/></tr>
                    <tr><td>Enabled</td><td>${plugin.enabled}</td><td><button onclick="togglePlugin('${plugin.id}');plugin_panels['${plugin.id}'].refresh()">Toggle</button></td></tr>
                </tbody>
            </table>
        `;
        const tbody = body.getElementsByTagName("tbody")[0]
        const settings = plugin.config.settings;
        settings.bools.forEach((b) => {
            const tr = document.createElement("tr");
            tr.innerHTML = `<td>${b.name}</td><td>${b.value}</td><td><button onclick="togglePluginSetting('${plugin.id}', '${b.key}');plugin_panels['${plugin.id}'].refresh()">Toggle</button></td>`;
            tbody.appendChild(tr);
        });
        settings.ints.forEach((i) => {
            const tr = document.createElement("tr");
            tr.innerHTML = `<td>${i.name}</td><td>${i.value}</td><td><input type="number" value="${i.value}" id="${plugin.id}-${i.key}-input"/><button onclick="setPluginSetting('${plugin.id}', '${i.key}', Number(document.getElementById('${plugin.id}-${i.key}-input').value));plugin_panels['${plugin.id}'].refresh()">Set</button></td>`;
            tbody.appendChild(tr);
        })
        settings.shortAnswers.forEach((s) => {
            const tr = document.createElement("tr");
            tr.innerHTML = `<td>${s.name}</td><td>${s.value}</td><td><input type="text" value="${s.value}" id="${plugin.id}-${s.key}-input"/><button onclick="setPluginSetting('${plugin.id}', '${s.key}', document.getElementById('${plugin.id}-${s.key}-input').value);plugin_panels['${plugin.id}'].refresh()">Set</button></td>`;
            tbody.appendChild(tr);
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
                row.innerHTML = `
                    <td class="student-name">${student.name}</td>
                    <td class="student-graduation-level">${graduationLevels[student.graduationLevel]}</td>
                    <td class="student-action"><button onclick="viewStudent(${student.id})">Bearbeiten</button></td>
                `;
                populateStudentRowEvent(row, student);
            })
        }
        if (document.getElementById('subjectSelect')) {
            populateSubjectSelect('subjectSelect', await fetchAllSubjects());
        }
    }
})