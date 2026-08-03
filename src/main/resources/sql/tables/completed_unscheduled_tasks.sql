CREATE TABLE IF NOT EXISTS completed_unscheduled_tasks (
    student INTEGER NOT NULL,
    unscheduled_task INTEGER NOT NULL,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (student, unscheduled_task),

    FOREIGN KEY (student) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (unscheduled_task) REFERENCES unscheduled_tasks(id) ON DELETE CASCADE
)