ALTER TABLE special_tasks
RENAME TO temp_special_tasks;
DROP TABLE IF EXISTS individual_tasks;
ALTER TABLE temp_special_tasks
RENAME TO individual_tasks;