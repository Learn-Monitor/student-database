CREATE TABLE IF NOT EXISTS school_years (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    label TEXT NOT NULL UNIQUE,
    week_count INTEGER NOT NULL,
    current_week INTEGER NOT NULL,
    start_date TEXT,
    end_date TEXT,
    current_semester INTEGER NOT NULL
);
