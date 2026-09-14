INSERT INTO topics (name, subject, grade, number, semester)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT(subject, grade, semester, number) DO NOTHING;
