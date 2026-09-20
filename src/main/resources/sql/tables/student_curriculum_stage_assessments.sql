CREATE TABLE IF NOT EXISTS student_curriculum_stage_assessments (
    student INTEGER NOT NULL,
    subject INTEGER NOT NULL,
    semester INTEGER NOT NULL,
    stage_type TEXT NOT NULL,
    stage_id INTEGER NOT NULL,
    status TEXT NOT NULL,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY(student, subject, semester, stage_type, stage_id),

    CHECK (stage_type IN ('CENTRAL', 'FLEXIBLE')),
    CHECK (status IN ('FAILED_ONCE', 'FAILED_TWICE', 'LOCKED')),

    FOREIGN KEY(student) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY(subject) REFERENCES subjects(id),
    FOREIGN KEY(semester) REFERENCES semesters(id)
);
