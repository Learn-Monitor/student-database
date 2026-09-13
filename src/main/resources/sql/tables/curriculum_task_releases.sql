CREATE TABLE IF NOT EXISTS curriculum_task_releases (
 teacher INTEGER NOT NULL REFERENCES teachers(id),
 class INTEGER NOT NULL REFERENCES classes(id),
 subject INTEGER NOT NULL REFERENCES subjects(id),
 semester INTEGER NOT NULL REFERENCES semesters(id),
 task INTEGER NOT NULL REFERENCES tasks(id),
 active INTEGER NOT NULL CHECK(active IN (0,1)),
 PRIMARY KEY(teacher,class,subject,semester,task)
);
