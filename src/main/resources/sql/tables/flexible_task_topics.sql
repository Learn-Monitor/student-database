-- Missing row means unassigned. Existing task/completion tables stay intact.
CREATE TABLE IF NOT EXISTS flexible_task_topics (
    flexible_task INTEGER PRIMARY KEY REFERENCES flexible_tasks(id),
    flexible_topic INTEGER NOT NULL REFERENCES flexible_topics(id)
);
