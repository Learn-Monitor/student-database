INSERT INTO completed_individual_tasks (student, individual_task)
VALUES (?, ?)
ON CONFLICT(student, individual_task) DO NOTHING;