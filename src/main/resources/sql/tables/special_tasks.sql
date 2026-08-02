CREATE TABLE IF NOT EXISTS special_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    tokens INTEGER NOT NULL,
    subject_id INTEGER,

    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE,
    UNIQUE (subject_id, name)
);