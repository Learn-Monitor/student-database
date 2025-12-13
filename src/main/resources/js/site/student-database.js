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
async function fetchRooms() {
    const rooms = await fetchJson('/rooms');
    return rooms;
}
async function fetchTeacherClasses(teacherId) {
    const classes = await getJsonWithPost('/teacher-classes', { teacherId });
    return classes;
}
async function fetchSubjects(teacherId) {
    const subjects = await getJsonWithPost('/teacher-subjects', { teacherId });
    return subjects;
}

async function getStudents(classId) {
    return await getJsonWithPost('/student-list', { classId });
}
async function getStudentsBySubject(classId, subjectId) {
    return await getJsonWithPost('/student-list', { classId, subjectId });
}
async function getStudentsByRoom(room) {
    return await getJsonWithPost('/get-students-by-room', { room });
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
async function changeGraduationLevel(studentId, newLevel) {
    return await getJsonWithPost('/change-graduation-level', { studentId: studentId, graduationLevel: newLevel });
}
async function deleteClass(classId) {
    return await post('/delete-class', { id: classId });
}
async function deleteSubject(subjectId) {
    return await post('/delete-subject', { id: subjectId });
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
async function populateRoomStudentList(room) {
  const students = await getStudentsByRoom(room);

  const studentTable = document.getElementById("roomStudentTableBody");
  studentTable.innerHTML = ""; // clear previous rows
  students.forEach(student => {
      const row = document.createElement('tr');
      row.innerHTML = `
          <td class="student-name">${student.name}</td>
          <td class="student-action-required">${student.actionRequired ? "Ja" : "Nein"}</td>
          <td class="student-action"><button onclick="viewStudent(${student.id})">Bearbeiten</button></td>
      `;
      studentTable.appendChild(row);
  });
}
async function populateTopicTable(table, subjectId, grade) {
    const table = document.querySelector("#topicTable tbody");
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
async function populateSubjectSelect(subjectSelectId, teacherId) {
    const subjectSelect = document.getElementById(subjectSelectId);
    subjectSelect.innerHTML = ''; // Clear existing options
    const subjects = teacherId ? await fetchSubjects(teacherId) : await fetchJson('/subjects');
    subjects.forEach(function(subject) {
        const option = document.createElement('option');
        option.value = subject.id;
        option.textContent = subject.name;
        subjectSelect.appendChild(option);
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
async function populateRoomSelect(roomSelectId) {
  const roomSelect = document.getElementById(roomSelectId);
  roomSelect.innerHTML = ""; // clear previous options if any
  const rooms = await fetchRooms();
  rooms.forEach(room => {
    const option = document.createElement('option');
    option.value = room.label;
    option.textContent = room.label;
    roomSelect.appendChild(option);
  });
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
const graduationLevels = ["Neustarter", "Starter", "Durchstarter", "Lernprofi"];