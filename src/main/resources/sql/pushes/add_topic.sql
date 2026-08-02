INSERT INTO topics (name, subject, ratio, grade, number, semester)
VALUES (?, ?, ?, ?, ?, ?)
ON CONFLICT(name, subject, grade) DO UPDATE SET
    ratio = excluded.ratio;