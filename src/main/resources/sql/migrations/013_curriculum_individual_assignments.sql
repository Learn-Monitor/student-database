CREATE TABLE IF NOT EXISTS curriculum_individual_assignments (
 student INTEGER NOT NULL REFERENCES students(id),
 semester INTEGER NOT NULL REFERENCES semesters(id),
 assignment_group TEXT NOT NULL,
 subject INTEGER NOT NULL REFERENCES subjects(id),
 PRIMARY KEY(student,semester,assignment_group)
);
INSERT OR IGNORE INTO curriculum_individual_assignments(student,semester,assignment_group,subject)
SELECT student,semester,'WPF',subject FROM curriculum_wpf_assignments;
