INSERT INTO individual_tasks (name, tokens, subject_id)
VALUES (?, ?, ?)
ON CONFLICT(subject_id, name) DO UPDATE SET
    tokens = excluded.tokens;