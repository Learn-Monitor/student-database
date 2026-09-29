CREATE TABLE IF NOT EXISTS course_groups (
    id INTEGER PRIMARY KEY,
    subject INTEGER NOT NULL REFERENCES subjects(id),
    grade INTEGER NOT NULL CHECK(grade BETWEEN 1 AND 13),
    semester INTEGER NOT NULL REFERENCES semesters(id),
    teacher INTEGER NOT NULL REFERENCES teachers(id),
    assignment_group TEXT NOT NULL,
    name TEXT NOT NULL,
    active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)),
    UNIQUE(subject,grade,semester)
);

CREATE TABLE IF NOT EXISTS course_group_members (
    course_group INTEGER NOT NULL REFERENCES course_groups(id),
    student INTEGER NOT NULL REFERENCES students(id),
    PRIMARY KEY(course_group,student)
);
CREATE INDEX IF NOT EXISTS idx_course_group_members_student ON course_group_members(student);

ALTER TABLE student_curriculum_contexts ADD COLUMN course_group INTEGER REFERENCES course_groups(id);
ALTER TABLE flexible_topics ADD COLUMN course_group INTEGER REFERENCES course_groups(id);
ALTER TABLE flexible_tasks ADD COLUMN course_group INTEGER REFERENCES course_groups(id);

CREATE TABLE IF NOT EXISTS course_group_topic_releases (
    course_group INTEGER NOT NULL REFERENCES course_groups(id),
    topic INTEGER NOT NULL REFERENCES topics(id),
    active INTEGER NOT NULL CHECK(active IN (0,1)),
    PRIMARY KEY(course_group,topic)
);
CREATE TABLE IF NOT EXISTS course_group_task_releases (
    course_group INTEGER NOT NULL REFERENCES course_groups(id),
    task INTEGER NOT NULL REFERENCES tasks(id),
    active INTEGER NOT NULL CHECK(active IN (0,1)),
    PRIMARY KEY(course_group,task)
);
CREATE TABLE IF NOT EXISTS course_group_flexible_topic_releases (
    course_group INTEGER NOT NULL REFERENCES course_groups(id),
    flexible_topic INTEGER NOT NULL REFERENCES flexible_topics(id),
    active INTEGER NOT NULL CHECK(active IN (0,1)),
    PRIMARY KEY(course_group,flexible_topic)
);
CREATE TABLE IF NOT EXISTS course_group_flexible_task_releases (
    course_group INTEGER NOT NULL REFERENCES course_groups(id),
    flexible_task INTEGER NOT NULL REFERENCES flexible_tasks(id),
    active INTEGER NOT NULL CHECK(active IN (0,1)),
    PRIMARY KEY(course_group,flexible_task)
);
