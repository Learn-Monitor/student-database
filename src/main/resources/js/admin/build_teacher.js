async function onClassChange(event) {
    populateStudentTable(Number(event.target.value), "studentTable", (row, student) => {
        row.innerHTML = `
            <td class="student-name">${student.name}</td>
            <td class="student-room">${student.room}</td>
            <td class="student-graduation-level">${graduationLevels[student.graduationLevel]}</td>
            <td class="student-action"><button onclick="viewStudent(${student.id})">Bearbeiten</button></td>
        `;
    });
}

document.addEventListener('DOMContentLoaded', async () => {
  const classSelect = document.getElementById('classSelect');
  const subjectClassSelect = document.getElementById('classSelectSubject');
  const classes = await fetchTeacherClasses(teacherId);
  populateClassSelect(classSelect, classes);
  populateClassSelect(subjectClassSelect, classes);
  classSelect.addEventListener('change', onClassChange);
  subjectClassSelect.addEventListener('change', (_) => populateSubjectStudentList('subjectSelect', 'classSelectSubject', 'subjectStudentTable'));
  onClassChange({ target: classSelect }); // Trigger initial load

  const subjectSelect = document.getElementById('subjectSelect');
  populateSubjectSelect('subjectSelect', teacherId);
  subjectSelect.addEventListener('change', (_) => populateSubjectStudentList('subjectSelect', 'classSelectSubject', 'subjectStudentTable'));
  populateSubjectStudentList({ target: subjectSelect }); // Trigger initial load

  populateSubjectSelect('editSubjectSelect');

  const editClassSelect = document.getElementById('editClassSelect');
  const allClasses = await fetchClasses();
  populateClassSelect(editClassSelect, allClasses);

  populateRoomSelect('roomSelect');
  roomSelect.addEventListener('change', (e) => populateRoomStudentList(e.target.value));
  populateRoomStudentList({target: roomSelect});
});
