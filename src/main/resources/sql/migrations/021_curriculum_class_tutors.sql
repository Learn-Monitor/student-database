CREATE TABLE IF NOT EXISTS curriculum_class_tutors (
    semester INTEGER NOT NULL REFERENCES semesters(id),
    class INTEGER NOT NULL REFERENCES classes(id),
    teacher INTEGER NOT NULL REFERENCES teachers(id),
    tutor_slot INTEGER NOT NULL CHECK(tutor_slot IN (1,2)),
    PRIMARY KEY (semester, class, tutor_slot),
    UNIQUE (semester, class, teacher)
);
