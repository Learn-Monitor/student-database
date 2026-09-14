CREATE TABLE IF NOT EXISTS tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    topic INTEGER NOT NULL,
    name TEXT NOT NULL,
    niveau INTEGER NOT NULL,
    stage_number INTEGER NOT NULL DEFAULT 1,
    tokens INTEGER NOT NULL,
    UNIQUE(topic, name),
    UNIQUE(topic, stage_number),
    FOREIGN KEY (topic) REFERENCES topics(id) ON DELETE CASCADE
)
