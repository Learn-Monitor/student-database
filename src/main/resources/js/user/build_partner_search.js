document.addEventListener('DOMContentLoaded', async (_) => {
    const subjects = await fetchMyCurriculumSubjects();
    populateSubjectSelect('subjectSelect', subjects);
    const subjectSelect = document.getElementById('subjectSelect');
    const studentTable = document.getElementById('studentTableBody');
    if (subjects.length === 0) {
        studentTable.innerHTML = '';
        return;
    }
    subjectSelect.addEventListener('change', async (e) => populatePartnerSubjectStudentList(Number(e.target.value)));
    populatePartnerSubjectStudentList(Number(subjectSelect.value));
})
