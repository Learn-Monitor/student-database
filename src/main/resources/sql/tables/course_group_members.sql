CREATE TABLE IF NOT EXISTS course_group_members (
    course_group INTEGER NOT NULL REFERENCES course_groups(id),
    student INTEGER NOT NULL REFERENCES students(id),
    PRIMARY KEY(course_group,student)
);
CREATE INDEX IF NOT EXISTS idx_course_group_members_student ON course_group_members(student);
