package de.igslandstuhl.database.api.curriculum;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Migration and domain tests for the additive subject identity foundation. */
class SubjectGradeActivationTest {
    private static final Path MIGRATION = Path.of("src/main/resources/sql/migrations/027_subject_grade_activation_and_identity.sql");
    private static final Path RELIGION_MIGRATION = Path.of("src/main/resources/sql/migrations/032_religion_ethik_grade_activation.sql");

    @Test
    void prodLikeBackfillDoesNotRequireCoreTypeRows() throws Exception {
        try (Connection c = database()) {
            seed(c, false);
            applyMigration(c);

            assertEquals(5, scalar(c, "SELECT COUNT(*) FROM subjects WHERE subject_code IS NOT NULL"));
            assertEquals(16, scalar(c, "SELECT COUNT(*) FROM subject_grade_activation"));
            assertEquals(6, scalar(c, "SELECT COUNT(*) FROM subject_grade_activation a JOIN curriculum_subject_types t ON t.subject=a.subject WHERE a.grade=6 AND t.assignment_group='WPF'"));
            assertEquals(0, scalar(c, "SELECT COUNT(*) FROM subject_grade_activation WHERE grade=5 AND subject IN (SELECT subject FROM curriculum_subject_types WHERE assignment_group='WPF')"));
            assertEquals(0, scalar(c, "SELECT COUNT(*) FROM subject_grade_activation WHERE subject IN (SELECT subject FROM curriculum_subject_types WHERE assignment_group='RELIGION_ETHIK')"));
            assertEquals(0, scalar(c, "SELECT COUNT(*) FROM subject_grade_activation WHERE subject=20"));
            assertEquals(1, scalar(c, "SELECT COUNT(*) FROM curriculum_grade_subjects"));
            assertEquals(1, scalar(c, "SELECT COUNT(*) FROM course_groups"));
        }
    }

    @Test
    void demoLikeBackfillHasTheSameFunctionalResult() throws Exception {
        try (Connection c = database()) {
            seed(c, true);
            applyMigration(c);
            assertEquals(10, scalar(c, "SELECT COUNT(*) FROM subject_grade_activation a JOIN subjects s ON s.id=a.subject WHERE a.grade IN (5,6) AND s.subject_code IS NOT NULL"));
            assertEquals(16, scalar(c, "SELECT COUNT(*) FROM subject_grade_activation"));
            assertEquals("DE", text(c, "SELECT subject_code FROM subjects WHERE name='Deutsch'"));
        }
    }

    @Test
    void missingCoreRollsBackTheEntireMigration() throws Exception {
        try (Connection c = database()) {
            seed(c, false);
            exec(c, "DELETE FROM subjects WHERE name='Deutsch'");
            SQLException failure = assertThrows(SQLException.class, () -> applyMigration(c));
            assertTrue(failure.getMessage().toLowerCase().contains("check") || failure.getMessage().toLowerCase().contains("constraint"));
            assertEquals(0, scalar(c, "SELECT COUNT(*) FROM pragma_table_info('subjects') WHERE name='subject_code'"));
            assertEquals(0, scalar(c, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='subject_grade_activation'"));
        }
    }

    @Test
    void stableIdentityAndPolicyAreIndependentOfDisplayName() throws Exception {
        try (Connection c = database()) {
            seed(c, false);
            applyMigration(c);
            int german = intValue(c, "SELECT id FROM subjects WHERE subject_code='DE'");
            exec(c, "UPDATE subjects SET name='Deutsch umbenannt' WHERE id=?", german);
            assertEquals("DE", text(c, "SELECT subject_code FROM subjects WHERE id=?", german));
            assertTrue(SolSubjectPolicy.isAllowed(c, 5, german));
            assertTrue(SolSubjectPolicy.isAllowed(c, 6, german));
        }
    }

    @Test
    void policyIsFailClosedBeyondActivationAndRole() throws Exception {
        try (Connection c = database()) {
            seed(c, false);
            applyMigration(c);
            int other = intValue(c, "SELECT id FROM subjects WHERE id=21");
            int wpf = intValue(c, "SELECT id FROM subjects WHERE id=10");
            int religion = intValue(c, "SELECT id FROM subjects WHERE id=16");

            exec(c, "INSERT OR IGNORE INTO subject_grade_activation(subject,grade) VALUES(21,5),(10,5),(10,6),(16,5),(16,6)");
            assertFalse(SolSubjectPolicy.isAllowed(c, 5, other));
            assertFalse(SolSubjectPolicy.isAllowed(c, 5, wpf));
            assertTrue(SolSubjectPolicy.isAllowed(c, 6, wpf));
            assertTrue(SolSubjectPolicy.isAllowed(c, 5, religion));
            assertTrue(SolSubjectPolicy.isAllowed(c, 6, religion));
            int german = intValue(c, "SELECT id FROM subjects WHERE subject_code='DE'");
            exec(c, "DELETE FROM subject_grade_activation WHERE subject=? AND grade=5", german);
            assertFalse(SolSubjectPolicy.isAllowed(c, 5, german));
        }
    }

    @Test
    void regularRequiresExplicitTypeAndActivationWhileUnknownTypeFailsClosed() throws Exception {
        try (Connection c = database()) {
            seed(c, false);
            applyMigration(c);
            exec(c, "INSERT INTO subjects(id,name) VALUES(22,'Musik'),(23,'Unklar')");
            exec(c, "INSERT INTO curriculum_subject_types(subject,wpf,mode) VALUES(22,0,'REGULAR')");
            exec(c, "INSERT INTO subject_grade_activation(subject,grade) VALUES(22,5),(22,6),(23,5)");
            assertTrue(SolSubjectPolicy.isAllowed(c, 5, 22));
            assertTrue(SolSubjectPolicy.isAllowed(c, 6, 22));
            exec(c, "DELETE FROM subject_grade_activation WHERE subject=22 AND grade=5");
            assertFalse(SolSubjectPolicy.isAllowed(c, 5, 22));
            assertFalse(SolSubjectPolicy.isAllowed(c, 5, 23));
        }
    }

    @Test
    void activationRepositoryReturnsStableGradeViews() throws Exception {
        try (Connection c = database()) {
            seed(c, false);
            applyMigration(c);
            int german = intValue(c, "SELECT id FROM subjects WHERE subject_code='DE'");
            assertEquals(List.of(5, 6), SubjectGradeActivationRepository.activeGrades(c, german));
            assertTrue(SubjectGradeActivationRepository.isActive(c, german, 5));
            assertEquals(5, SubjectGradeActivationRepository.activeSubjectIds(c, 5).size());
        }
    }

    @Test
    void religionEthikActivationIsAddedForBothManagedGradesWithoutChangingWpfPolicy() throws Exception {
        try (Connection c = database()) {
            seed(c, false);
            applyMigration(c);
            applyScript(c, RELIGION_MIGRATION);
            applyScript(c, RELIGION_MIGRATION);
            assertEquals(10, scalar(c, "SELECT COUNT(*) FROM subject_grade_activation a JOIN curriculum_subject_types t ON t.subject=a.subject WHERE t.assignment_group='RELIGION_ETHIK'"));
            assertEquals(0, scalar(c, "SELECT COUNT(*) FROM subject_grade_activation WHERE grade=5 AND subject IN (SELECT subject FROM curriculum_subject_types WHERE assignment_group='WPF')"));
            assertEquals(6, scalar(c, "SELECT COUNT(*) FROM subject_grade_activation WHERE grade=6 AND subject IN (SELECT subject FROM curriculum_subject_types WHERE assignment_group='WPF')"));
        }
    }

    private static Connection database() throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
        c.createStatement().execute("PRAGMA foreign_keys=ON");
        exec(c, "CREATE TABLE subjects(id INTEGER PRIMARY KEY, name TEXT NOT NULL)");
        exec(c, "CREATE TABLE curriculum_subject_types(subject INTEGER PRIMARY KEY REFERENCES subjects(id), wpf INTEGER NOT NULL DEFAULT 0, mode TEXT NOT NULL, assignment_group TEXT)");
        exec(c, "CREATE TABLE curriculum_grade_subjects(grade INTEGER, semester INTEGER, subject INTEGER)");
        exec(c, "CREATE TABLE course_groups(id INTEGER PRIMARY KEY, subject INTEGER)");
        return c;
    }

    private static void seed(Connection c, boolean demoLike) throws SQLException {
        String[] core = {"Mathematik", "Englisch", "Deutsch", "Naturwissenschaften", "Gesellschaftslehre"};
        for (int i = 0; i < core.length; i++) exec(c, "INSERT INTO subjects(id,name) VALUES(?,?)", i + 1, core[i]);
        String[] wpf = {"WPF Darstellendes Spiel", "WPF Französisch", "WPF Soziales und Familie", "WPF Sport und Gesundheit", "WPF Technologie und Wirtschaft", "WPF Ökologie"};
        for (int i = 0; i < wpf.length; i++) {
            int id = 10 + i;
            exec(c, "INSERT INTO subjects(id,name) VALUES(?,?)", id, wpf[i]);
            exec(c, "INSERT INTO curriculum_subject_types(subject,wpf,mode,assignment_group) VALUES(?,1,'INDIVIDUAL','WPF')", id);
        }
        String[] religion = {"Ethik01", "Ethik02", "Reli01", "Reli02", "Reli03"};
        for (int i = 0; i < religion.length; i++) {
            int id = 16 + i;
            exec(c, "INSERT INTO subjects(id,name) VALUES(?,?)", id, religion[i]);
            exec(c, "INSERT INTO curriculum_subject_types(subject,wpf,mode,assignment_group) VALUES(?,0,'INDIVIDUAL','RELIGION_ETHIK')", id);
        }
        exec(c, "INSERT INTO subjects(id,name) VALUES(21,'Other')");
        if (demoLike) for (int i = 1; i <= 5; i++) exec(c, "INSERT INTO curriculum_subject_types(subject,wpf,mode) VALUES(?,0,'REGULAR')", i);
        exec(c, "INSERT INTO curriculum_grade_subjects VALUES(5,1,1)");
        exec(c, "INSERT INTO course_groups(id,subject) VALUES(1,10)");
    }

    private static void applyMigration(Connection c) throws Exception {
        applyScript(c, MIGRATION);
    }

    private static void applyScript(Connection c, Path migration) throws Exception {
        c.setAutoCommit(false);
        try (Statement s = c.createStatement()) {
            for (String statement : Files.readString(migration).split(";")) {
                if (!statement.isBlank()) s.executeUpdate(statement);
            }
            c.commit();
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }

    private static void exec(Connection c, String sql, Object... args) throws SQLException {
        try (var s = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            s.executeUpdate();
        }
    }

    private static long scalar(Connection c, String sql, Object... args) throws SQLException {
        try (var s = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            try (var r = s.executeQuery()) { assertTrue(r.next()); return r.getLong(1); }
        }
    }

    private static int intValue(Connection c, String sql, Object... args) throws SQLException { return (int) scalar(c, sql, args); }

    private static String text(Connection c, String sql, Object... args) throws SQLException {
        try (var s = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            try (var r = s.executeQuery()) { assertTrue(r.next()); return r.getString(1); }
        }
    }
}
