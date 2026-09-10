-- Separate from legacy, per-student-variable UnscheduledTask records.
CREATE TABLE IF NOT EXISTS flexible_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_teacher INTEGER NOT NULL REFERENCES teachers(id),
    subject INTEGER NOT NULL REFERENCES subjects(id),
    class INTEGER NOT NULL REFERENCES classes(id),
    semester INTEGER NOT NULL REFERENCES semesters(id),
    grade INTEGER NOT NULL,
    name TEXT NOT NULL CHECK(length(trim(name)) BETWEEN 1 AND 200),
    tokens INTEGER NOT NULL CHECK(tokens BETWEEN 0 AND 105),
    UNIQUE(owner_teacher, subject, class, semester, name)
);
