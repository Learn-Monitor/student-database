CREATE TABLE IF NOT EXISTS curriculum_class_teachers (
 semester INTEGER NOT NULL REFERENCES semesters(id),
 class INTEGER NOT NULL REFERENCES classes(id),
 subject INTEGER NOT NULL REFERENCES subjects(id),
 teacher INTEGER NOT NULL REFERENCES teachers(id),
 PRIMARY KEY(semester,class,subject)
);
CREATE TABLE IF NOT EXISTS curriculum_grade_teachers (
 semester INTEGER NOT NULL REFERENCES semesters(id),
 grade INTEGER NOT NULL CHECK(grade BETWEEN 1 AND 13),
 subject INTEGER NOT NULL REFERENCES subjects(id),
 teacher INTEGER NOT NULL REFERENCES teachers(id),
 PRIMARY KEY(semester,grade,subject)
);
