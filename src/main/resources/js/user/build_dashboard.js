let studentData = null;

document.addEventListener('DOMContentLoaded', async () => {
    // Load base data
    studentData = await fetchMyData();
    const subjects = await fetchMySubjects();
    let managedSubjects;
    try {
        managedSubjects = await fetchMyManagedSubjects();
    } catch (error) {
        console.error('Managed curriculum could not be loaded', error);
        managedSubjects = null;
    }

    loadStudentDashboard(studentData, subjects, false, managedSubjects);
});
