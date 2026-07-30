INSERT INTO school_years (label, week_count, current_week, start_date, end_date)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT(label) DO UPDATE SET
    week_count = EXCLUDED.week_count,
    current_week = EXCLUDED.current_week,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date;
