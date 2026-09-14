CREATE TABLE IF NOT EXISTS topics (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    subject INTEGER NOT NULL,
    grade INTEGER NOT NULL,
    resource TEXT,
    number INTEGER NOT NULL,
    semester INTEGER,

    UNIQUE(subject, grade, semester, number),
    UNIQUE(subject, grade, semester, name),
    FOREIGN KEY (subject) REFERENCES subjects(id) ON DELETE CASCADE,
    FOREIGN KEY (semester) REFERENCES semesters(id) ON DELETE CASCADE
)
