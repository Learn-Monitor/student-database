CREATE TABLE IF NOT EXISTS student_subjects (
    student_id INTEGER NOT NULL,
    subject_id INTEGER NOT NULL,
    PRIMARY KEY (student_id, subject_id),
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE
);
