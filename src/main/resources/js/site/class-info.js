document.getElementById('download-class-results-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    const classId = Number(document.getElementById('classSelect').value);
    const subjectId = Number(form.subject.value);
    postDataAndDownload('/class-results', JSON.stringify({ classId, subjectId }), `schueler_ergebnisse_${classId}_${subjectId}.csv`);
});

document.getElementById('download-completed-tasks-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    const classId = Number(document.getElementById('classSelect').value);
    const subjectId = Number(form.subject.value);
    postDataAndDownload('/completed-tasks', JSON.stringify({ classId, subjectId }), `abgeschlossene_etappen_${classId}_${subjectId}.csv`);
});

document.addEventListener('DOMContentLoaded', async () => {
    populateSubjectSelect('subjectSelectClass', await fetchAllSubjects());
    populateSubjectSelect('subjectSelectTasks', await fetchAllSubjects());
});
