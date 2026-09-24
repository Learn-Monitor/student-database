package de.igslandstuhl.database.server.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudentActiveMigrationTest {
    @Test
    void migratesLegacyStudentsTableAndKeepsExistingStudentsActive(@TempDir Path directory) throws Exception {
        try (SQLiteConnection connection = new SQLiteConnection(directory.resolve("legacy-students").toString())) {
            connection.executeVoidProcessSecure("CREATE TABLE students (id INTEGER PRIMARY KEY, first_name TEXT NOT NULL, last_name TEXT NOT NULL, email TEXT NOT NULL, password TEXT NOT NULL, class INTEGER NOT NULL, graduation_level INTEGER NOT NULL)");
            connection.executeVoidProcessSecure("INSERT INTO students VALUES (1, 'Legacy', 'Student', 'legacy@example.test', 'hash', 7, 1)");

            assertFalse(connection.hasColumn("students", "active"));
            connection.migrateTables();
            assertTrue(connection.hasColumn("students", "active"));

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
            connection.migrateTables();

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

    @Test
    void currentSchemaSkipsLegacyReplaysAndPreservesDataAcrossRepeatedCalls(@TempDir Path directory) throws Exception {
        try (SQLiteConnection connection = new SQLiteConnection(directory.resolve("current-schema").toString())) {
            connection.createTables();
            connection.executeVoidProcessSecure("INSERT INTO students VALUES (101, 'Active', 'Student', 'active@example.test', 'hash-a', 11, 2, 1)");
            connection.executeVoidProcessSecure("INSERT INTO students VALUES (102, 'Archived', 'Student', 'archived@example.test', 'hash-b', 12, 3, 0)");
            connection.executeVoidProcessSecure("INSERT INTO topics(id,name,subject,grade,resource,number,semester) VALUES (201, 'Guard Topic', 301, 9, 'guard-resource', 4, 401)");
            connection.executeVoidProcessSecure("INSERT INTO tasks(id,topic,name,niveau,stage_number,tokens) VALUES (202, 201, 'Guard Task', 2, 5, 13)");
            connection.executeVoidProcessSecure("INSERT INTO teacher_subjects(teacher_id,subject_id) VALUES (203, 204)");

            Map<String, String> tableIdentities = tableIdentities(connection);

            connection.migrateTables();
            assertCurrentDataUnchanged(connection);
            assertEquals(1, rowCount(connection, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='curriculum_class_tutors'"));
            assertEquals(tableIdentities, tableIdentities(connection));

            connection.migrateTables();
            connection.migrateTables();
            assertCurrentDataUnchanged(connection);
            assertEquals(tableIdentities, tableIdentities(connection));
            assertEquals("ok", integrityCheck(connection));
        }
    }

    private static void assertCurrentDataUnchanged(SQLiteConnection connection) throws Exception {
        try (Statement statement = connection.getSQLConnection().createStatement();
                ResultSet result = statement.executeQuery("SELECT id,first_name,last_name,email,password,class,graduation_level,active FROM students ORDER BY id")) {
            assertTrue(result.next());
            assertEquals(101, result.getInt("id"));
            assertEquals("Active", result.getString("first_name"));
            assertEquals("Student", result.getString("last_name"));
            assertEquals("active@example.test", result.getString("email"));
            assertEquals("hash-a", result.getString("password"));
            assertEquals(11, result.getInt("class"));
            assertEquals(2, result.getInt("graduation_level"));
            assertEquals(1, result.getInt("active"));

            assertTrue(result.next());
            assertEquals(102, result.getInt("id"));
            assertEquals("Archived", result.getString("first_name"));
            assertEquals("Student", result.getString("last_name"));
            assertEquals("archived@example.test", result.getString("email"));
            assertEquals("hash-b", result.getString("password"));
            assertEquals(12, result.getInt("class"));
            assertEquals(3, result.getInt("graduation_level"));
            assertEquals(0, result.getInt("active"));
            assertFalse(result.next());
        }
        assertEquals(1, rowCount(connection, "SELECT COUNT(*) FROM topics WHERE id=201 AND name='Guard Topic' AND resource='guard-resource'"));
        assertEquals(1, rowCount(connection, "SELECT COUNT(*) FROM tasks WHERE id=202 AND topic=201 AND name='Guard Task' AND stage_number=5 AND tokens=13"));
        assertEquals(1, rowCount(connection, "SELECT COUNT(*) FROM teacher_subjects WHERE teacher_id=203 AND subject_id=204"));
    }

    private static Map<String, String> tableIdentities(SQLiteConnection connection) throws Exception {
        Map<String, String> identities = new LinkedHashMap<>();
        try (Statement statement = connection.getSQLConnection().createStatement();
                ResultSet result = statement.executeQuery("SELECT name,rootpage,sql FROM sqlite_schema WHERE type='table' AND name IN ('students','topics','tasks','teacher_subjects') ORDER BY name")) {
            while (result.next()) {
                identities.put(result.getString("name"), result.getInt("rootpage") + ":" + result.getString("sql"));
            }
        }
        assertEquals(4, identities.size());
        return identities;
    }

    private static int rowCount(SQLiteConnection connection, String sql) throws Exception {
        try (Statement statement = connection.getSQLConnection().createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
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
