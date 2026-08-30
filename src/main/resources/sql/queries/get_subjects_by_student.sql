SELECT subjects.*
FROM subjects
INNER JOIN student_subjects ON subjects.id = student_subjects.subject_id
WHERE student_subjects.student_id = ?
ORDER BY subjects.id;
