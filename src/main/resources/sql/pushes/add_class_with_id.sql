INSERT INTO classes (id, label, grade, active)
VALUES (?, ?, ?, 1)
ON CONFLICT (id) DO UPDATE SET label = EXCLUDED.label, grade = EXCLUDED.grade, active = 1
ON CONFLICT(label, grade) DO NOTHING;
