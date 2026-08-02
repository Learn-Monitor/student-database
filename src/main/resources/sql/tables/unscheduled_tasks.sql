CREATE TABLE IF NOT EXISTS unscheduled_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    class INTEGER NOT NULL,
    subject INTEGER NOT NULL,
    max_tokens INTEGER NOT NULL,

    FOREIGN KEY (subject) REFERENCES subjects(id) ON DELETE CASCADE,
    FOREIGN KEY (class) REFERENCES classes(id) ON DELETE CASCADE,
    UNIQUE (subject, name)
);