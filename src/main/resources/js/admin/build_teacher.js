let teacher = JSON.parse(sessionStorage.getItem("currentTeacher"));
console.log(teacher);
document.getElementById("teacher-name").textContent = teacher.firstName + " " + teacher.lastName;
let teacherId = teacher.id;
Array.from(document.getElementsByClassName("teacherId")).forEach(e => e.value = teacherId);

document.addEventListener('DOMContentLoaded', async () => {
    buildTeacherDashboard(await fetchTeacherClasses(teacherId), await fetchSubjects(teacherId));
    populateSubjectSelect('editSubjectSelect', await fetchAllSubjects());
    const editClassSelect = document.getElementById('editClassSelect');
    const allClasses = await fetchClasses();
    populateClassSelect(editClassSelect, allClasses);
});
