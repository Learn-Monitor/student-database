CREATE TABLE IF NOT EXISTS curriculum_subject_types (
 subject INTEGER PRIMARY KEY REFERENCES subjects(id),
 wpf INTEGER NOT NULL CHECK(wpf IN (0,1))
);
