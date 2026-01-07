document.addEventListener('DOMContentLoaded', async () => {
    buildTeacherDashboard(fetchTeacherClasses(teacherId), fetchSubjects(teacherId));
});