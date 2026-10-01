const cls = JSON.parse(sessionStorage.getItem('currentClass'));

document.addEventListener('DOMContentLoaded', async function() {
    if (cls) {
        document.getElementById('className').value = cls.label;
        document.getElementById('classGrade').value = cls.grade;
        const heading = document.getElementById('class-heading-name');
        if (heading) heading.textContent = cls.label || '';

        Array.from(document.getElementsByClassName('classId')).forEach(function(element) {
            element.value = cls.id;
        });
    } else {
        console.error('No class data found in sessionStorage.');
        return;
    }

    const isSystemClass = cls && Number(cls.id) === 0;
    if (isSystemClass) {
        document.getElementById('systemClassNotice').hidden = false;
        document.getElementById('className').disabled = true;
        document.getElementById('classGrade').disabled = true;
        document.getElementById('saveClassButton').disabled = true;
        document.getElementById('deleteClassButton').disabled = true;
    }

    document.getElementById('deleteClassButton').addEventListener('click', function() {
        if (isSystemClass) {
            return;
        }
        if (confirm('Klasse wirklich archivieren? Die Klasse wird deaktiviert. Die Schülerinnen und Schüler bleiben erhalten und werden der Klasse „Nicht zugeordnet“ zugewiesen.')) {
            deleteClass(cls.id).then(response => {
                if (response.ok) {
                    const status = document.getElementById('class-admin-status');
                    if (status) status.textContent = 'Klasse wurde archiviert.';
                    window.location.href = '/manage_classes';
                } else {
                    const status = document.getElementById('class-admin-status');
                    if (status) { status.textContent = 'Die Klasse konnte nicht archiviert werden.'; status.dataset.state = 'error'; }
                }
            })
            .catch(() => {
                const status = document.getElementById('class-admin-status');
                if (status) { status.textContent = 'Die Klasse konnte nicht archiviert werden.'; status.dataset.state = 'error'; }
            });
        }
    });
    
    await populateStudentTable(Number(cls.id), "studentTable", (row, student) => {
        const nameCell = document.createElement('td');
        nameCell.textContent = student.name;
        row.appendChild(nameCell);

        const graduationCell = document.createElement('td');
        graduationCell.textContent = graduationLevels[student.graduationLevel];
        row.appendChild(graduationCell);

        const actionCell = document.createElement('td');
        const editButton = document.createElement('button');
        editButton.className = 'edit-student';
        editButton.type = 'button';
        editButton.textContent = 'Bearbeiten';
        editButton.addEventListener('click', () => viewStudent(student.id));
        actionCell.appendChild(editButton);
        row.appendChild(actionCell);
    });
});
