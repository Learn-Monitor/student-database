CREATE TABLE IF NOT EXISTS curriculum_wpf_assignments (
 student INTEGER NOT NULL REFERENCES students(id),
 semester INTEGER NOT NULL REFERENCES semesters(id),
 subject INTEGER NOT NULL REFERENCES subjects(id),
 PRIMARY KEY(student,semester)
);
