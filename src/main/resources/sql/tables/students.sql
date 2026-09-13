CREATE TABLE IF NOT EXISTS students (
    id INTEGER PRIMARY KEY,
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    email TEXT NOT NULL,
    password TEXT NOT NULL,
    class INTEGER NOT NULL,
    graduation_level INTEGER NOT NULL,

    FOREIGN KEY (class) REFERENCES classes(id)
)
