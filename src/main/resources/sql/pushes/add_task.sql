INSERT INTO tasks (topic, name, niveau, tokens)
VALUES (?, ?, ?, ?)
ON CONFLICT(topic, name) DO UPDATE SET
    niveau = excluded.niveau,
    tokens = excluded.tokens;