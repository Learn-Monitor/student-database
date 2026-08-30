cls = JSON.parse(sessionStorage.getItem('currentClass'));

document.addEventListener('DOMContentLoaded', async function() {
    if (cls) {
        document.getElementById('className').value = cls.label;
        document.getElementById('classGrade').value = cls.grade;

        Array.from(document.getElementsByClassName('classId')).forEach(function(element) {
            element.value = cls.id;
        });
    } else {
        console.error('No class data found in sessionStorage.');
    }
    document.getElementById('deleteClassButton').addEventListener('click', function() {
        if (confirm('Are you sure you want to delete this class?')) {
            deleteClass(cls.id).then(response => {
                if (response.ok) {
                    alert('Class deleted successfully.');
                    window.location.href = '/manage_classes';
                } else {
                    alert('Failed to delete class. Please try again.');
                }
            })
            .catch(error => {
                console.error('Error deleting class:', error);
                alert('An error occurred while trying to delete the class.');
            });
        }
    });
    
    await populateStudentTable(Number(cls.id), "studentTable", (row, student) => {
        row.innerHTML = `
            <td>${student.name}</td>
            <td>${student.room}</td>
            <td>${graduationLevels[student.graduationLevel]}</td>
            <td><button class="edit-student" onclick="viewStudent(${JSON.stringify(student).replaceAll('"', "'")})">Bearbeiten</button></td>
        `;
    });

    await populateSubjectList('subjectList', Number(cls.id));

    await populateSubjectSelect('subjectSelect');
});