CREATE TABLE IF NOT EXISTS curriculum_enrolled_students (
 student INTEGER NOT NULL REFERENCES students(id),
 semester INTEGER NOT NULL REFERENCES semesters(id),
 grade INTEGER NOT NULL CHECK(grade BETWEEN 1 AND 13),
 PRIMARY KEY(student,semester)
);
