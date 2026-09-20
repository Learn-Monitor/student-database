CREATE TABLE IF NOT EXISTS student_subject_requests (
    student INTEGER NOT NULL,
    subject INTEGER NOT NULL,
    semester INTEGER NOT NULL,
    request_type TEXT NOT NULL CHECK(request_type IN ('HELP','PARTNER','EXPERIMENT','EXAM')),
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(student, subject, semester, request_type),
    FOREIGN KEY(student) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY(subject) REFERENCES subjects(id),
    FOREIGN KEY(semester) REFERENCES semesters(id)
);
