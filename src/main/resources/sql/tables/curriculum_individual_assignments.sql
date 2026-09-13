CREATE TABLE IF NOT EXISTS curriculum_individual_assignments (
 student INTEGER NOT NULL REFERENCES students(id),
 semester INTEGER NOT NULL REFERENCES semesters(id),
 assignment_group TEXT NOT NULL,
 subject INTEGER NOT NULL REFERENCES subjects(id),
 PRIMARY KEY(student,semester,assignment_group)
);
