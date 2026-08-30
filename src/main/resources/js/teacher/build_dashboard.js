document.addEventListener('DOMContentLoaded', async () => {
    buildTeacherDashboard(await fetchMyClasses(), await fetchMySubjects());
});