CREATE TABLE IF NOT EXISTS flexible_task_releases (
 teacher INTEGER NOT NULL REFERENCES teachers(id),
 class INTEGER NOT NULL REFERENCES classes(id),
 subject INTEGER NOT NULL REFERENCES subjects(id),
 semester INTEGER NOT NULL REFERENCES semesters(id),
 flexible_task INTEGER NOT NULL REFERENCES flexible_tasks(id),
 active INTEGER NOT NULL CHECK(active IN (0,1)),
 PRIMARY KEY(teacher,class,subject,semester,flexible_task)
);
