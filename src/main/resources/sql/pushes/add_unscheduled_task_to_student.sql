INSERT INTO completed_unscheduled_tasks (student, unscheduled_task)
VALUES (?, ?)
ON CONFLICT(student, unscheduled_task) DO NOTHING;