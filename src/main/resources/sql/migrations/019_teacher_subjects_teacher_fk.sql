PRAGMA foreign_keys=off;

CREATE TABLE IF NOT EXISTS teacher_subjects_new (
    teacher_id INTEGER,
    subject_id INTEGER,
    PRIMARY KEY (teacher_id, subject_id),
    FOREIGN KEY (teacher_id) REFERENCES teachers(id) ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE
);

INSERT OR IGNORE INTO teacher_subjects_new(teacher_id,subject_id)
SELECT teacher_id,subject_id FROM teacher_subjects;

DROP TABLE teacher_subjects;

ALTER TABLE teacher_subjects_new RENAME TO teacher_subjects;
