'use strict';

const subject = JSON.parse(sessionStorage.getItem('currentSubject'));

document.addEventListener('DOMContentLoaded', () => {
    if (subject) {
        document.getElementById('subjectNameField').value = subject.name;
        Array.from(document.getElementsByClassName('subjectId')).forEach(element => {
            element.value = subject.id;
        });
    } else {
        console.error('No subject data found in sessionStorage.');
    }

    document.getElementById('deleteSubjectButton').addEventListener('click', () => {
        if (!subject) return;
        if (confirm('Fach wirklich löschen? Dies ist nur möglich, wenn keine Zuordnungen, Curriculuminhalte oder Leistungsdaten vorhanden sind.')) {
            deleteSubject(subject.id).then(response => {
                if (response.ok) {
                    alert('Fach wurde gelöscht.');
                    window.location.href = '/manage_subjects';
                } else {
                    alert('Das Fach konnte nicht gelöscht werden. Möglicherweise wird es noch verwendet.');
                }
            }).catch(error => {
                console.error('Error deleting subject:', error);
                alert('Das Fach konnte nicht gelöscht werden. Möglicherweise wird es noch verwendet.');
            });
        }
    });
});
