let studentData = null;

let studentId = Number(sessionStorage.getItem('selectedStudentId'));

document.addEventListener('DOMContentLoaded', async () => {
    // Load base data
    studentData = await fetchStudentData(studentId);
    const subjects = await fetchStudentSubjects(studentId);

    loadStudentDashboard(studentData, subjects, true);
});
