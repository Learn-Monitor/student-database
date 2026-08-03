ALTER TABLE individual_tasks ADD COLUMN tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE individual_tasks DROP COLUMN ratio;