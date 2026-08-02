INSERT INTO unscheduled_tasks (name, class, subject, max_tokens)
VALUES (?, ?, ?)
ON CONFLICT(subject, name, class) DO UPDATE SET
    tokens = excluded.tokens;