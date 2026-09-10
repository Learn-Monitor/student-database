-- Identity-only audit trail: original completion rows are retained, never counted twice.
CREATE TABLE IF NOT EXISTS curriculum_completion_transfers (
    student INTEGER NOT NULL REFERENCES students(id),
    source_task INTEGER NOT NULL REFERENCES flexible_tasks(id),
    target_task INTEGER NOT NULL REFERENCES flexible_tasks(id),
    transferred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(student, source_task),
    UNIQUE(student, target_task),
    CHECK(source_task <> target_task),
    FOREIGN KEY(student, source_task) REFERENCES completed_flexible_tasks(student, flexible_task),
    FOREIGN KEY(student, target_task) REFERENCES completed_flexible_tasks(student, flexible_task)
);
