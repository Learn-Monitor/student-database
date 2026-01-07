let settings;

document.addEventListener('DOMContentLoaded', async () => {
    // Load student data (reuse endpoint from dashboard)
    const studentData = await fetchMyData();
    
    loadStudentResultView(studentData);
});