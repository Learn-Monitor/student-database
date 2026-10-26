package de.igslandstuhl.database.server.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** B8 migration 028: destructive legacy cleanup with canonical preservation. */
class LegacySchemaCleanupTest {
    private static final Path ACTIVATION = Path.of("src/main/resources/sql/migrations/027_subject_grade_activation_and_identity.sql");
    private static final Path CLEANUP = Path.of("src/main/resources/sql/migrations/028_legacy_schema_cleanup.sql");

    @Test
    void prodLike027Then028DropsLegacyWithoutBackfill() throws Exception {
        try (Connection c = database()) {
            seedCore(c);
            legacyTables(c);
            exec(c, "INSERT INTO gradesubjects VALUES(6,1)");
            exec(c, "INSERT INTO student_subjects VALUES(40,1)");
            exec(c, "INSERT INTO curriculum_wpf_assignments VALUES(40,10,1)");
            apply(c, ACTIVATION);
            long activations = scalar(c, "SELECT COUNT(*) FROM subject_grade_activation");
            apply(c, CLEANUP);
            assertLegacyAbsent(c);
            assertEquals(activations, scalar(c, "SELECT COUNT(*) FROM subject_grade_activation"));
            assertEquals(0, scalar(c, "SELECT COUNT(*) FROM curriculum_individual_assignments"));
        }
    }

    @Test
    void demoLike028PreservesEveryCanonicalFixture() throws Exception {
        try (Connection c = database()) {
            seedCore(c);
            exec(c, "INSERT INTO subject_grade_activation VALUES(1,5),(1,6)");
            exec(c, "INSERT INTO curriculum_grade_subjects VALUES(5,10,1)");
            exec(c, "INSERT INTO curriculum_individual_assignments VALUES(40,10,'WPF',2)");
            exec(c, "INSERT INTO student_curriculum_contexts VALUES(40,1,10,20,30,5,NULL)");
            exec(c, "INSERT INTO curriculum_class_teachers VALUES(10,30,1,20)");
            exec(c, "INSERT INTO curriculum_grade_teachers VALUES(10,5,1,20)");
            exec(c, "INSERT INTO course_groups VALUES(7,2,6,10,20,'WPF','WPF',1)");
            exec(c, "INSERT INTO course_group_members VALUES(7,40)");
            legacyTables(c);
            exec(c, "INSERT INTO gradesubjects VALUES(6,1)");
            exec(c, "INSERT INTO student_subjects VALUES(40,1)");
            exec(c, "INSERT INTO curriculum_wpf_assignments VALUES(40,10,2)");
            long[] before = canonicalCounts(c);
            apply(c, CLEANUP);
            assertLegacyAbsent(c);
            assertArrayEquals(before, canonicalCounts(c));
            apply(c, CLEANUP);
            assertArrayEquals(before, canonicalCounts(c));
        }
    }

    @Test
    void missingCanonicalPreconditionsFailClosedAndKeepLegacy() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            exec(c, "CREATE TABLE gradesubjects(grade INTEGER,subject INTEGER)");
            exec(c, "INSERT INTO gradesubjects VALUES(6,1)");
            assertThrows(SQLException.class, () -> apply(c, CLEANUP));
            assertEquals(1, scalar(c, "SELECT COUNT(*) FROM gradesubjects"));
        }
    }

    @Test
    void freshInstallEndsWithCanonicalSchemaOnly(@TempDir Path directory) throws Exception {
        try (SQLiteConnection db = new SQLiteConnection(directory.resolve("fresh").toString())) {
            db.createTables();
            db.writeTransaction(c -> {
                for (int i = 0; i < 5; i++) exec(c, "INSERT INTO subjects(name) VALUES(?)", new String[]{"Deutsch", "Englisch", "Mathematik", "Naturwissenschaften", "Gesellschaftslehre"}[i]);
                return null;
            });
            db.migrateTables();
            try (var s = db.getSQLConnection().createStatement()) {
                assertEquals(0, scalar(db.getSQLConnection(), "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('gradesubjects','student_subjects','curriculum_wpf_assignments')"));
                assertEquals(1, scalar(db.getSQLConnection(), "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='subject_grade_activation'"));
                assertEquals(1, scalar(db.getSQLConnection(), "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='curriculum_individual_assignments'"));
                assertEquals(1, scalar(db.getSQLConnection(), "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='student_curriculum_contexts'"));
                assertEquals(1, scalar(db.getSQLConnection(), "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='curriculum_subject_types'"));
            }
        }
    }

    @Test
    void emptyFreshInstallDefersDataDependentMigrations(@TempDir Path directory) throws Exception {
        try (SQLiteConnection db = new SQLiteConnection(directory.resolve("empty-fresh").toString())) {
            db.createTables();
            db.migrateTables();

            assertEquals(0, scalar(db.getSQLConnection(), "SELECT COUNT(*) FROM subjects"));
            assertEquals(0, scalar(db.getSQLConnection(), "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='subject_grade_activation'"));

            db.writeTransaction(c -> {
                exec(c, "INSERT INTO subjects(name) VALUES(?)", "Deutsch");
                exec(c, "INSERT INTO subjects(name) VALUES(?)", "Englisch");
                exec(c, "INSERT INTO subjects(name) VALUES(?)", "Mathematik");
                exec(c, "INSERT INTO subjects(name) VALUES(?)", "Naturwissenschaften");
                exec(c, "INSERT INTO subjects(name) VALUES(?)", "Gesellschaftslehre");
                return null;
            });
            db.migrateTables();
            assertEquals(1, scalar(db.getSQLConnection(), "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='subject_grade_activation'"));
            assertEquals(5, scalar(db.getSQLConnection(), "SELECT COUNT(*) FROM subjects WHERE subject_code IS NOT NULL"));
        }
    }

    private static Connection database() throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
        c.createStatement().execute("PRAGMA foreign_keys=ON");
        for (String sql : new String[]{
                "CREATE TABLE subjects(id INTEGER PRIMARY KEY,name TEXT NOT NULL)",
                "CREATE TABLE subject_grade_activation(subject INTEGER,grade INTEGER)",
                "CREATE TABLE semesters(id INTEGER PRIMARY KEY)",
                "CREATE TABLE students(id INTEGER PRIMARY KEY)",
                "CREATE TABLE teachers(id INTEGER PRIMARY KEY)",
                "CREATE TABLE classes(id INTEGER PRIMARY KEY,grade INTEGER)",
                "CREATE TABLE curriculum_subject_types(subject INTEGER PRIMARY KEY,wpf INTEGER NOT NULL DEFAULT 0,mode TEXT NOT NULL,assignment_group TEXT)",
                "CREATE TABLE curriculum_grade_subjects(grade INTEGER,semester INTEGER,subject INTEGER)",
                "CREATE TABLE curriculum_individual_assignments(student INTEGER,semester INTEGER,assignment_group TEXT,subject INTEGER,PRIMARY KEY(student,semester,assignment_group))",
                "CREATE TABLE student_curriculum_contexts(student INTEGER,subject INTEGER,semester INTEGER,teacher INTEGER,class INTEGER,grade INTEGER,course_group INTEGER)",
                "CREATE TABLE curriculum_class_teachers(semester INTEGER,class INTEGER,subject INTEGER,teacher INTEGER)",
                "CREATE TABLE curriculum_grade_teachers(semester INTEGER,grade INTEGER,subject INTEGER,teacher INTEGER)",
                "CREATE TABLE course_groups(id INTEGER PRIMARY KEY,subject INTEGER,grade INTEGER,semester INTEGER,teacher INTEGER,assignment_group TEXT,name TEXT,active INTEGER)",
                "CREATE TABLE course_group_members(course_group INTEGER,student INTEGER)"}) exec(c, sql);
        return c;
    }

    private static void seedCore(Connection c) throws SQLException {
        String[] names = {"Deutsch", "Englisch", "Mathematik", "Naturwissenschaften", "Gesellschaftslehre"};
        for (int i = 0; i < names.length; i++) exec(c, "INSERT INTO subjects VALUES(?,?)", i + 1, names[i]);
        exec(c, "INSERT INTO semesters VALUES(10)");
        exec(c, "INSERT INTO students VALUES(40)");
        exec(c, "INSERT INTO teachers VALUES(20)");
        exec(c, "INSERT INTO classes VALUES(30,5)");
        exec(c, "INSERT INTO curriculum_subject_types VALUES(1,0,'REGULAR',NULL)");
    }

    private static void legacyTables(Connection c) throws SQLException {
        exec(c, "CREATE TABLE gradesubjects(grade INTEGER,subject INTEGER)");
        exec(c, "CREATE TABLE student_subjects(student_id INTEGER,subject_id INTEGER)");
        exec(c, "CREATE TABLE curriculum_wpf_assignments(student INTEGER,semester INTEGER,subject INTEGER)");
    }

    private static void assertLegacyAbsent(Connection c) throws SQLException {
        assertEquals(0, scalar(c, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('gradesubjects','student_subjects','curriculum_wpf_assignments')"));
    }

    private static long[] canonicalCounts(Connection c) throws SQLException {
        return new long[]{scalar(c, "SELECT COUNT(*) FROM subject_grade_activation"), scalar(c, "SELECT COUNT(*) FROM curriculum_grade_subjects"), scalar(c, "SELECT COUNT(*) FROM curriculum_individual_assignments"), scalar(c, "SELECT COUNT(*) FROM student_curriculum_contexts"), scalar(c, "SELECT COUNT(*) FROM curriculum_class_teachers"), scalar(c, "SELECT COUNT(*) FROM curriculum_grade_teachers"), scalar(c, "SELECT COUNT(*) FROM course_groups"), scalar(c, "SELECT COUNT(*) FROM course_group_members")};
    }

    private static void apply(Connection c, Path path) throws Exception {
        c.setAutoCommit(false);
        try (Statement s = c.createStatement()) {
            for (String statement : SQLiteConnection.splitSqlScript(Files.readString(path))) s.executeUpdate(statement);
            c.commit();
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally { c.setAutoCommit(true); }
    }

    private static void exec(Connection c, String sql, Object... args) throws SQLException {
        try (var s = c.prepareStatement(sql)) { for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]); s.executeUpdate(); }
    }

    private static long scalar(Connection c, String sql) throws SQLException {
        try (var s = c.prepareStatement(sql); var r = s.executeQuery()) { assertTrue(r.next()); return r.getLong(1); }
    }
}
