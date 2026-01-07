let studentId = Number(sessionStorage.getItem('selectedStudentId'));
let settings;

document.addEventListener('DOMContentLoaded', async () => {
    // Load student data
    const studentData = await fetchStudentData(studentId);
    
    loadStudentResultView(studentData);
});