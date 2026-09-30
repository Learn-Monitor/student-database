let studentData = null;

document.addEventListener('DOMContentLoaded', async () => {
    // These are independent read models. Start them together so the initial
    // dashboard does not create a serial base-data waterfall.
    const [loadedStudentData, subjects, managedSubjects] = await Promise.all([
        fetchMyData(),
        fetchMySubjects(),
        fetchMyManagedSubjects().catch(error => {
            console.error('Managed curriculum could not be loaded', error);
            return null;
        })
    ]);
    studentData = loadedStudentData;

    loadStudentDashboard(studentData, subjects, false, managedSubjects);
});
