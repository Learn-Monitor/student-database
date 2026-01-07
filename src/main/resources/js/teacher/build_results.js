let studentId = Number(sessionStorage.getItem('selectedStudentId'));
let settings;

document.addEventListener('DOMContentLoaded', async () => {
    // Load student data
    const studentData = await fetchStudentData(studentId);
    
    loadStudentResultView(studentData);
    document.getElementById('studentId').value = studentId;
    document.getElementById('download-student-results-csv').addEventListener('submit', async function(e) {
        e.preventDefault();
        const form = e.target;
        const studentId = Number(form['studentId'].value);

        postDataAndDownload('/student-results-csv', JSON.stringify({ studentId }), 'schueler_ergebnisse_' + studentId + '.csv');
    });
});