let studentData = null;

document.addEventListener('DOMContentLoaded', async () => {
    // Load base data
    studentData = await fetchMyData();
    const subjects = await fetchMySubjects();

    loadStudentDashboard(studentData, subjects);
});
