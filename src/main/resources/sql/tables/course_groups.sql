CREATE TABLE IF NOT EXISTS course_groups (
    id INTEGER PRIMARY KEY,
    subject INTEGER NOT NULL REFERENCES subjects(id),
    grade INTEGER NOT NULL CHECK(grade BETWEEN 1 AND 13),
    semester INTEGER NOT NULL REFERENCES semesters(id),
    teacher INTEGER NOT NULL REFERENCES teachers(id),
    assignment_group TEXT NOT NULL,
    name TEXT NOT NULL,
    active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)),
    UNIQUE(subject,grade,semester)
);
