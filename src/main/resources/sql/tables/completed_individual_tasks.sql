CREATE TABLE IF NOT EXISTS completed_individual_tasks (
    student INTEGER NOT NULL,
    individual_task INTEGER NOT NULL,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (student, individual_task),

    FOREIGN KEY (student) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (individual_task) REFERENCES individual_tasks(id) ON DELETE CASCADE
)