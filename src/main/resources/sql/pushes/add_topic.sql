INSERT INTO topics (name, subject, grade, number, semester)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT(name, subject, grade) DO UPDATE SET
    ratio = excluded.ratio;