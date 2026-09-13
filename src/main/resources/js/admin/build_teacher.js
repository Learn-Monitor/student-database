let teacher = JSON.parse(sessionStorage.getItem("currentTeacher"));
console.log(teacher);
document.getElementById("teacher-name").textContent = teacher.firstName + " " + teacher.lastName;
document.getElementById('teacherFirstName').value=teacher.firstName||'';
document.getElementById('teacherLastName').value=teacher.lastName||'';
document.getElementById('teacherEmail').value=teacher.email||'';
let teacherId = teacher.id;
Array.from(document.getElementsByClassName("teacherId")).forEach(e => e.value = teacherId);

document.addEventListener('DOMContentLoaded', async () => {
    buildTeacherDashboard(await fetchTeacherClasses(teacherId), await fetchSubjects(teacherId));
});
