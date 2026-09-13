CREATE TABLE IF NOT EXISTS curriculum_subject_types (
 subject INTEGER PRIMARY KEY REFERENCES subjects(id),
 wpf INTEGER NOT NULL DEFAULT 0 CHECK(wpf IN (0,1)),
 mode TEXT NOT NULL DEFAULT 'REGULAR' CHECK(mode IN ('REGULAR','INDIVIDUAL')),
 assignment_group TEXT
);
