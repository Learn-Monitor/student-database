PRAGMA foreign_keys=off;
CREATE TABLE IF NOT EXISTS students_new (
    id INTEGER PRIMARY KEY,
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    email TEXT NOT NULL,
    password TEXT NOT NULL,
    class INTEGER NOT NULL,
    graduation_level INTEGER NOT NULL,
    FOREIGN KEY (class) REFERENCES classes(id)
);
INSERT OR IGNORE INTO classes(id,label,grade,active) VALUES(0,'Nicht zugeordnet',0,1);
INSERT INTO students_new(id,first_name,last_name,email,password,class,graduation_level)
SELECT id,first_name,last_name,email,password,class,graduation_level FROM students;
DROP TABLE students;
ALTER TABLE students_new RENAME TO students;
PRAGMA foreign_keys=on;
