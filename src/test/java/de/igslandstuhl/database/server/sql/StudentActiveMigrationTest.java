package de.igslandstuhl.database.server.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudentActiveMigrationTest {
    @Test
    void migratesLegacyStudentsTableAndKeepsExistingStudentsActive(@TempDir Path directory) throws Exception {
        try (SQLiteConnection connection = new SQLiteConnection(directory.resolve("legacy-students").toString())) {
            connection.executeVoidProcessSecure("CREATE TABLE students (id INTEGER PRIMARY KEY, first_name TEXT NOT NULL, last_name TEXT NOT NULL, email TEXT NOT NULL, password TEXT NOT NULL, class INTEGER NOT NULL, graduation_level INTEGER NOT NULL)");
            connection.executeVoidProcessSecure("INSERT INTO students VALUES (1, 'Legacy', 'Student', 'legacy@example.test', 'hash', 7, 1)");

            connection.migrateTables();

            try (Statement statement = connection.getSQLConnection().createStatement();
                    ResultSet result = statement.executeQuery("SELECT active FROM students WHERE id = 1")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
            assertEquals("ok", integrityCheck(connection));
        }
    }

    @Test
    void studentsActiveMigrationContainsExactlyOneStatement() throws Exception {
        Path migration = Path.of("src/main/resources/sql/migrations/020_students_active.sql");
        String sql = Files.readString(migration);

        assertEquals(1, SQLiteConnection.splitSqlScript(sql).size());
        assertEquals("ALTER TABLE students ADD COLUMN active INTEGER NOT NULL DEFAULT 1", SQLiteConnection.splitSqlScript(sql).get(0));
    }

    @Test
    void freshlyCreatedStudentsTableHasActiveDefaultOne(@TempDir Path directory) throws Exception {
        try (SQLiteConnection connection = new SQLiteConnection(directory.resolve("fresh-students").toString())) {
            connection.createTables();

            try (Statement statement = connection.getSQLConnection().createStatement();
                    ResultSet result = statement.executeQuery("PRAGMA table_info(students)")) {
                boolean foundActive = false;
                while (result.next()) {
                    if ("active".equals(result.getString("name"))) {
                        foundActive = true;
                        assertEquals("INTEGER", result.getString("type"));
                        assertEquals(1, result.getInt("notnull"));
                        assertEquals("1", result.getString("dflt_value"));
                    }
                }
                assertTrue(foundActive);
            }
            assertEquals("ok", integrityCheck(connection));
        }
    }

    private static String integrityCheck(SQLiteConnection connection) throws Exception {
        try (Statement statement = connection.getSQLConnection().createStatement();
                ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }
}
