-- Religion/Ethik is an explicitly supported individual subject group for the
-- managed SOL grades.  Keep the historical 027 backfill unchanged and add the
-- missing activation idempotently for fresh installs and upgrades.
INSERT OR IGNORE INTO subject_grade_activation(subject, grade)
SELECT t.subject, g.grade
FROM curriculum_subject_types t
CROSS JOIN (SELECT 5 AS grade UNION ALL SELECT 6 AS grade) g
WHERE UPPER(COALESCE(t.mode, '')) = 'INDIVIDUAL'
  AND UPPER(COALESCE(t.assignment_group, '')) = 'RELIGION_ETHIK';
