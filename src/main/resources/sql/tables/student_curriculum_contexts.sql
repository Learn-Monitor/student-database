-- One explicit context per student/subject/semester; no inferred legacy assignments.
CREATE TABLE IF NOT EXISTS student_curriculum_contexts (
    student INTEGER NOT NULL REFERENCES students(id),
    subject INTEGER NOT NULL REFERENCES subjects(id),
    semester INTEGER NOT NULL REFERENCES semesters(id),
    teacher INTEGER NOT NULL REFERENCES teachers(id),
    class INTEGER NOT NULL REFERENCES classes(id),
    grade INTEGER NOT NULL CHECK(grade BETWEEN 1 AND 13),
    PRIMARY KEY(student, subject, semester)
);
