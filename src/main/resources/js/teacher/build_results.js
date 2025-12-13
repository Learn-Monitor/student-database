let studentId = Number(sessionStorage.getItem('selectedStudentId'));
let settings;

document.addEventListener('DOMContentLoaded', async () => {
    // Load student data
    const studentData = await fetchStudentData(studentId);
    const settings = await fetch('/get-module', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ key: 'result_view' })
    }).then(settingsRes => settingsRes.json()).then(data => data.settings);

    document.getElementById('student-name').textContent = `${studentData.firstName} ${studentData.lastName}`;

    // Get all subjects from progress keys
    const subjectNames = Object.keys(studentData.currentProgress || {});
    // If you have subject objects, map them here; else, use names as fallback
    // For demo: create fake subject objects
    const subjects = subjectNames.map(name => ({ id: name, name }));

    const charts = document.getElementById('charts');
    subjects.forEach(subject => {
        charts.appendChild(createBarChart(subject, subject.name, studentData));
    });
});