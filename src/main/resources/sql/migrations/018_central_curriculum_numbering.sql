PRAGMA foreign_keys=OFF;

ALTER TABLE tasks ADD COLUMN stage_number INTEGER;

UPDATE tasks
SET stage_number = (
    SELECT COUNT(*) FROM tasks previous
    WHERE previous.topic = tasks.topic
      AND (previous.niveau < tasks.niveau OR (previous.niveau = tasks.niveau AND previous.id <= tasks.id))
)
WHERE stage_number IS NULL OR stage_number < 1;

CREATE TABLE IF NOT EXISTS topics_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    subject INTEGER NOT NULL,
    grade INTEGER NOT NULL,
    resource TEXT,
    number INTEGER NOT NULL,
    semester INTEGER,
    UNIQUE(subject, grade, semester, number),
    UNIQUE(subject, grade, semester, name),
    FOREIGN KEY (subject) REFERENCES subjects(id) ON DELETE CASCADE,
    FOREIGN KEY (semester) REFERENCES semesters(id) ON DELETE CASCADE
);

INSERT INTO topics_new(id,name,subject,grade,resource,number,semester)
SELECT id,name,subject,grade,resource,number,semester FROM topics;

CREATE TABLE IF NOT EXISTS tasks_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    topic INTEGER NOT NULL,
    name TEXT NOT NULL,
    niveau INTEGER NOT NULL,
    stage_number INTEGER NOT NULL DEFAULT 1,
    tokens INTEGER NOT NULL,
    UNIQUE(topic, name),
    UNIQUE(topic, stage_number),
    FOREIGN KEY (topic) REFERENCES topics(id) ON DELETE CASCADE
);

INSERT INTO tasks_new(id,topic,name,niveau,stage_number,tokens)
SELECT id,topic,name,niveau,stage_number,tokens
FROM tasks;

DROP TABLE tasks;

ALTER TABLE tasks_new RENAME TO tasks;

DROP TABLE topics;

ALTER TABLE topics_new RENAME TO topics;
