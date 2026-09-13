ALTER TABLE curriculum_subject_types ADD COLUMN mode TEXT NOT NULL DEFAULT 'REGULAR';
ALTER TABLE curriculum_subject_types ADD COLUMN assignment_group TEXT;
UPDATE curriculum_subject_types
SET mode=CASE WHEN wpf=1 THEN 'INDIVIDUAL' ELSE 'REGULAR' END,
    assignment_group=CASE WHEN wpf=1 THEN 'WPF' ELSE NULL END
WHERE mode IS NULL OR mode='REGULAR';
