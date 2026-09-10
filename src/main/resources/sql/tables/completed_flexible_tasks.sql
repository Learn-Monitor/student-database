CREATE TABLE IF NOT EXISTS completed_flexible_tasks (
    student INTEGER NOT NULL REFERENCES students(id),
    flexible_task INTEGER NOT NULL REFERENCES flexible_tasks(id),
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(student, flexible_task)
);
