CREATE UNIQUE INDEX IF NOT EXISTS uq_flexible_topics_course_group_name ON flexible_topics(course_group,name) WHERE course_group IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_flexible_tasks_course_group_name ON flexible_tasks(course_group,name) WHERE course_group IS NOT NULL;
