CREATE TABLE IF NOT EXISTS student_active_curriculum_stages (
    student INTEGER NOT NULL,
    subject INTEGER NOT NULL,
    semester INTEGER NOT NULL,
    central_task INTEGER,
    flexible_task INTEGER,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(student, subject),
    CHECK ((central_task IS NOT NULL AND flexible_task IS NULL) OR (central_task IS NULL AND flexible_task IS NOT NULL)),
    FOREIGN KEY(student) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY(subject) REFERENCES subjects(id),
    FOREIGN KEY(semester) REFERENCES semesters(id),
    FOREIGN KEY(central_task) REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY(flexible_task) REFERENCES flexible_tasks(id) ON DELETE CASCADE
)
