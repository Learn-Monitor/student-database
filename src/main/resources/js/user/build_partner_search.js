const studentData = fetchMyData();

document.addEventListener('DOMContentLoaded', async (_) => {
    const subjects = await fetchJson('/mysubjects');
    populateSubjectSelect('subjectSelect', subjects);
    subjectSelect.addEventListener('change', async (e) => populatePartnerSubjectStudentList(e.target.value, await studentData));
    populatePartnerSubjectStudentList(subjectSelect.value, await studentData);
})