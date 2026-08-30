INSERT INTO student_subjects (student_id, subject_id)
VALUES (?, ?)
ON CONFLICT(student_id, subject_id) DO NOTHING;
