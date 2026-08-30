INSERT INTO semesters (label, position, school_year) VALUES (?, ?, ?)
ON CONFLICT (label) DO UPDATE SET position = EXCLUDED.position, school_year = EXCLUDED.school_year;