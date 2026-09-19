let studentData = null;

let studentId = Number(sessionStorage.getItem('selectedStudentId'));

document.addEventListener('DOMContentLoaded', async () => {
    if (!Number.isInteger(studentId) || studentId <= 0) {
        return;
    }

    // Load base data
    try {
        studentData = await fetchStudentData(studentId);
        const subjects = await fetchStudentSubjects(studentId);

        loadStudentDashboard(studentData, subjects, true);
    } catch (error) {
        return;
    }
});
