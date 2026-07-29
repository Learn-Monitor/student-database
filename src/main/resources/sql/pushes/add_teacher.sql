INSERT INTO teachers (first_name, last_name, email, password)
VALUES (?, ?, ?, ?)
ON CONFLICT(email) DO UPDATE SET
    first_name = excluded.first_name,
    last_name = excluded.last_name,
    password = excluded.password;
