subject = JSON.parse(sessionStorage.getItem("currentSubject"))

document.addEventListener('DOMContentLoaded', async (e) => {
    if (subject) {
        document.getElementById('subjectNameField').value = subject.name;
        Array.from(document.getElementsByClassName('subjectId')).forEach(function(element) {
            element.value = subject.id;
        });
    } else {
        console.error('No class data found in sessionStorage.');
    }

    document.getElementById('deleteSubjectButton').addEventListener('click', function() {
        if (confirm('Are you sure you want to delete this subject?')) {
            deleteSubject(subject.id).then(response => {
                if (response.ok) {
                    alert('Subject deleted successfully.');
                    window.location.href = '/manage_subjects';
                } else {
                    alert('Failed to delete subject. Please try again.');
                }
            })
            .catch(error => {
                console.error('Error deleting subject:', error);
                alert('An error occurred while trying to delete the subject.');
            });
        }
    });

    await populateGradeList('gradeList', subject.id);
    await populateGradeSelect('gradeSelect', subject.id);

    async function updateTopicTable(e) {
        await populateTopicTable('topicTable', subject.id, Number(e.target.value));
    }

    gradeSelect.addEventListener('change', updateTopicTable);
    updateTopicTable({target: {value: gradeSelect.value}});

    document.getElementById("deleteAllTopics").addEventListener('click', e => 
        openUrlWithPostParams("/delete-topics", {
            subjectId: subject.id,
            grade: Number(gradeSelect.value)
        })
    );
})