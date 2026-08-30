INSERT INTO unscheduled_tasks (name, class, subject, max_tokens)
VALUES (?, ?, ?, ?)
ON CONFLICT(subject, name, class) DO UPDATE SET
    max_tokens = excluded.max_tokens;