CREATE TABLE IF NOT EXISTS student_graduation_history (
    id INTEGER PRIMARY KEY,
    student INTEGER NOT NULL REFERENCES students(id),
    old_graduation_level INTEGER NOT NULL,
    new_graduation_level INTEGER NOT NULL,
    changed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    teacher INTEGER NOT NULL REFERENCES teachers(id),
    semester INTEGER NOT NULL REFERENCES semesters(id)
);
CREATE INDEX IF NOT EXISTS idx_student_graduation_history_student
    ON student_graduation_history(student, changed_at DESC, id DESC);
