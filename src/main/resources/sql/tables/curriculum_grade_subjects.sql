CREATE TABLE IF NOT EXISTS curriculum_grade_subjects (
 grade INTEGER NOT NULL CHECK(grade BETWEEN 1 AND 13),
 semester INTEGER NOT NULL REFERENCES semesters(id),
 subject INTEGER NOT NULL REFERENCES subjects(id),
 PRIMARY KEY(grade,semester,subject)
);
