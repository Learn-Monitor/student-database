let studentData = null;

let studentId = Number(sessionStorage.getItem('selectedStudentId'));

document.addEventListener('DOMContentLoaded', async () => {
    // Load base data
    studentData = await fetchStudentData(studentId);
    const subjects = await fetchStudentSubjects(studentId);

    // Show student info
    setStudentInfo(studentData);

    // Show rooms
    populateRoomSelectWithLevel('room', studentData.graduationLevel);

    // Wait for the browser to render (next tick)
    setTimeout(() => {
        // Set room select value to current room
        if (studentData.currentRoom && studentData.currentRoom.label) {
            roomSelect.value = studentData.currentRoom.label;
        }
    }, 0);

    roomSelect.addEventListener('change', async () => updateRoom(studentId, roomSelect.value));

    // Show subjects
    const subjectList = document.getElementById('subject-list');
    subjects.forEach(subject => {
        const panel = createSubjectPanel(subject, studentData);
        subjectList.appendChild(panel);
    });
});
