package de.igslandstuhl.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.server.sql.SQLiteConnection;

class ApplicationStartupTest {
    @Test
    void repeatedNormalStartupPreservesCourseGroupMembers() throws Exception {
        Path directory = Files.createTempDirectory("student-database-startup-");
        Path database = directory.resolve("startup");
        try (SQLiteConnection connection = new SQLiteConnection(database.toString())) {
            connection.createTables();
            connection.writeTransaction(c -> {
                try (var statement = c.prepareStatement("INSERT OR IGNORE INTO subjects(name) VALUES (?)")) {
                    for (String subject : List.of("Deutsch", "Englisch", "Mathematik", "Naturwissenschaften", "Gesellschaftslehre")) {
                        statement.setString(1, subject);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                return null;
            });
            Application.initializeDatabase(connection);
            connection.writeTransaction(c -> {
                try (var statement = c.prepareStatement(
                        "INSERT INTO course_group_members(course_group,student) VALUES(?,?)")) {
                    statement.setInt(1, 900001);
                    statement.setInt(2, 900002);
                    statement.executeUpdate();
                }
                return null;
            });
            List<String> expected = members(connection);
            for (int startup = 0; startup < 3; startup++) {
                Application.initializeDatabase(connection);
                assertEquals(expected, members(connection), "startup #" + (startup + 1));
            }
        } finally {
            Files.deleteIfExists(Path.of(database + ".db"));
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void prodLikeReligionEthikTypesRemainUnactivatedAcrossRepeatedStartup() throws Exception {
        Path directory = Files.createTempDirectory("student-database-prod-policy-");
        Path database = directory.resolve("prod-like");
        try (SQLiteConnection connection = new SQLiteConnection(database.toString())) {
            connection.createTables();
            seedSubjectsAndReligionTypes(connection);

            for (int startup = 0; startup < 3; startup++) {
                Application.initializeDatabase(connection);
                assertEquals(0, religionActivationCount(connection), "startup #" + (startup + 1));
            }
        } finally {
            Files.deleteIfExists(Path.of(database + ".db"));
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void demoLikeExplicitReligionEthikActivationsRemainSupportedAcrossStartup() throws Exception {
        Path directory = Files.createTempDirectory("student-database-demo-policy-");
        Path database = directory.resolve("demo-like");
        try (SQLiteConnection connection = new SQLiteConnection(database.toString())) {
            connection.createTables();
            seedSubjectsAndReligionTypes(connection);
            Application.initializeDatabase(connection);

            connection.writeTransaction(c -> {
                try (var statement = c.prepareStatement(
                        "INSERT OR IGNORE INTO subject_grade_activation(subject,grade) "
                                + "SELECT subject, ? FROM curriculum_subject_types "
                                + "WHERE assignment_group = 'RELIGION_ETHIK'")) {
                    for (int grade : new int[]{5, 6}) {
                        statement.setInt(1, grade);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                return null;
            });

            assertEquals(10, religionActivationCount(connection));

            Application.initializeDatabase(connection);
            assertEquals(10, religionActivationCount(connection));
        } finally {
            Files.deleteIfExists(Path.of(database + ".db"));
            Files.deleteIfExists(directory);
        }
    }

    private static void seedSubjectsAndReligionTypes(SQLiteConnection connection) throws Exception {
        connection.writeTransaction(c -> {
            try (var subjects = c.prepareStatement("INSERT INTO subjects(id,name) VALUES(?,?)")) {
                String[] core = {"Deutsch", "Englisch", "Mathematik", "Naturwissenschaften", "Gesellschaftslehre"};
                for (int i = 0; i < core.length; i++) {
                    subjects.setInt(1, i + 1);
                    subjects.setString(2, core[i]);
                    subjects.addBatch();
                }
                String[] religion = {"Ethik01", "Ethik02", "Reli01", "Reli02", "Reli03"};
                for (int i = 0; i < religion.length; i++) {
                    subjects.setInt(1, i + 16);
                    subjects.setString(2, religion[i]);
                    subjects.addBatch();
                }
                subjects.executeBatch();
            }
            try (var types = c.prepareStatement(
                    "INSERT INTO curriculum_subject_types(subject,wpf,mode,assignment_group) VALUES(?,0,'INDIVIDUAL','RELIGION_ETHIK')")) {
                for (int subject = 16; subject <= 20; subject++) {
                    types.setInt(1, subject);
                    types.addBatch();
                }
                types.executeBatch();
            }
            return null;
        });
    }

    private static long religionActivationCount(SQLiteConnection connection) throws Exception {
        try (var statement = connection.getSQLConnection().prepareStatement(
                "SELECT COUNT(*) FROM subject_grade_activation a "
                        + "JOIN curriculum_subject_types t ON t.subject = a.subject "
                        + "WHERE t.assignment_group = 'RELIGION_ETHIK'")) {
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static List<String> members(SQLiteConnection connection) throws Exception {
        try (var statement = connection.getSQLConnection().prepareStatement(
                "SELECT course_group || ':' || student FROM course_group_members ORDER BY course_group,student");
             var rows = statement.executeQuery()) {
            var result = new java.util.ArrayList<String>();
            while (rows.next()) result.add(rows.getString(1));
            return result;
        }
    }
}
