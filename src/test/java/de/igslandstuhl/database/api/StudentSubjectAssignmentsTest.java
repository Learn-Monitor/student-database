package de.igslandstuhl.database.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLiteConnection;

class StudentSubjectAssignmentsTest {
    @BeforeAll
    static void setupDatabase() throws SQLException {
        PreConditions.setupDatabase();
    }

    @Test
    void upgradesExistingDatabaseWithoutChangingExistingSubjectBehavior(@TempDir Path directory) throws SQLException {
        String databasePath = directory.resolve("legacy").toString();
        try (SQLiteConnection connection = new SQLiteConnection(databasePath)) {
            connection.executeVoidProcessSecure("CREATE TABLE classes (id INTEGER PRIMARY KEY, label TEXT NOT NULL, grade INTEGER NOT NULL)");
            connection.executeVoidProcessSecure("CREATE TABLE students (id INTEGER PRIMARY KEY, first_name TEXT NOT NULL, last_name TEXT NOT NULL, email TEXT NOT NULL, password TEXT NOT NULL, class INTEGER NOT NULL, graduation_level INTEGER NOT NULL, FOREIGN KEY (class) REFERENCES classes(id) ON DELETE CASCADE)");
            connection.executeVoidProcessSecure("CREATE TABLE subjects (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)");
            connection.executeVoidProcessSecure("CREATE TABLE gradesubjects (grade INTEGER NOT NULL, subject INTEGER NOT NULL, UNIQUE(grade, subject), FOREIGN KEY (subject) REFERENCES subjects(id) ON DELETE CASCADE)");
            connection.executeVoidProcessSecure("INSERT INTO classes VALUES (1, '6a', 6)");
            connection.executeVoidProcessSecure("INSERT INTO students VALUES (1, 'Legacy', 'Student', 'legacy@example.test', 'hash', 1, 1)");
            connection.executeVoidProcessSecure("INSERT INTO subjects VALUES (1, 'Deutsch')");
            connection.executeVoidProcessSecure("INSERT INTO gradesubjects VALUES (6, 1)");

            connection.createTables();

            try (Statement statement = connection.getSQLConnection().createStatement();
                    ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM student_subjects")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
            try (Statement statement = connection.getSQLConnection().createStatement();
                    ResultSet result = statement.executeQuery("SELECT subjects.name FROM subjects INNER JOIN gradesubjects ON subjects.id = gradesubjects.subject WHERE gradesubjects.grade = 6")) {
                assertTrue(result.next());
                assertEquals("Deutsch", result.getString(1));
                assertFalse(result.next());
            }
        }
    }

    @Test
    void supportsAdditiveIndividualSubjectsAndWpfAssignments() throws Exception {
        String suffix = Long.toUnsignedString(System.nanoTime());
        SchoolClass schoolClass = SchoolClass.addClass("6wpf" + suffix, 6);

        List<Subject> standardSubjects = List.of(
            createSubject("Deutsch", suffix),
            createSubject("Englisch", suffix),
            createSubject("Mathematik", suffix),
            createSubject("Gesellschaftslehre", suffix),
            createSubject("Naturwissenschaften", suffix)
        );
        for (Subject subject : standardSubjects) subject.addToGrade(6);

        Subject french = createSubject("WPF Französisch", suffix);
        Subject sports = createSubject("WPF Sport und Gesundheit", suffix);
        Subject ecology = createSubject("WPF Ökologie", suffix);

        int idBase = 1_000_000_000 + Math.floorMod(suffix.hashCode(), 100_000_000) * 3;
        Student studentA = createStudent(idBase, "A", suffix, schoolClass);
        Student studentB = createStudent(idBase + 1, "B", suffix, schoolClass);
        Student studentC = createStudent(idBase + 2, "C", suffix, schoolClass);

        Set<Integer> standardIds = ids(standardSubjects);
        assertEquals(standardIds, ids(studentA.getSubjects()));
        assertEquals(standardIds, ids(studentB.getSubjects()));
        assertEquals(standardIds, ids(studentC.getSubjects()));

        studentA.addSubject(french);
        studentB.addSubject(sports);
        studentC.addSubject(ecology);

        assertEquals(union(standardIds, french.getId()), ids(studentA.getSubjects()));
        assertEquals(union(standardIds, sports.getId()), ids(studentB.getSubjects()));
        assertEquals(union(standardIds, ecology.getId()), ids(studentC.getSubjects()));
        assertEquals(Set.of(french.getId()), ids(studentA.getIndividualSubjects()));
        assertEquals(Set.of(sports.getId()), ids(studentB.getIndividualSubjects()));
        assertEquals(Set.of(ecology.getId()), ids(studentC.getIndividualSubjects()));
        assertTrue(studentA.hasSubject(french));
        assertFalse(studentA.hasSubject(sports));
        assertFalse(studentA.hasSubject(ecology));
        assertEquals(standardIds, ids(schoolClass.getSubjects()));

        studentA.addSubject(french);
        assertEquals(1, assignmentCount(studentA.getId(), french.getId()));
        assertThrows(SQLException.class, () -> insertAssignment(studentA.getId(), french.getId()));

        evictStudent(studentA.getId());
        Student reloadedA = Student.get(studentA.getId());
        assertEquals(Set.of(french.getId()), ids(reloadedA.getIndividualSubjects()));

        Subject mathematics = standardSubjects.get(2);
        reloadedA.removeSubject(mathematics);
        assertTrue(reloadedA.hasSubject(mathematics));
        reloadedA.removeSubject(french);
        assertFalse(reloadedA.hasSubject(french));
        assertTrue(reloadedA.getIndividualSubjects().isEmpty());

        studentC.delete();
        assertEquals(0, assignmentCount(studentC.getId(), ecology.getId()));

        studentB.addSubject(ecology);
        ecology.delete();
        assertEquals(0, assignmentCount(studentB.getId(), ecology.getId()));
    }

    private static Subject createSubject(String name, String suffix) throws SQLException {
        return Subject.addSubject(name + " " + suffix);
    }

    private static Student createStudent(int id, String name, String suffix, SchoolClass schoolClass) throws SQLException {
        return Student.registerStudentWithPassword(id, name, "WPF", name.toLowerCase() + suffix + "@example.test", "password", schoolClass, GraduationLevel.LEVEL1);
    }

    private static Set<Integer> ids(List<Subject> subjects) {
        return subjects.stream().map(Subject::getId).collect(Collectors.toSet());
    }

    private static Set<Integer> union(Set<Integer> subjects, int subject) {
        return java.util.stream.Stream.concat(subjects.stream(), java.util.stream.Stream.of(subject)).collect(Collectors.toSet());
    }

    private static int assignmentCount(int studentId, int subjectId) throws SQLException {
        try (PreparedStatement statement = Server.getInstance().getConnection().getSQLConnection()
                .prepareStatement("SELECT COUNT(*) FROM student_subjects WHERE student_id = ? AND subject_id = ?")) {
            statement.setInt(1, studentId);
            statement.setInt(2, subjectId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static void insertAssignment(int studentId, int subjectId) throws SQLException {
        try (PreparedStatement statement = Server.getInstance().getConnection().getSQLConnection()
                .prepareStatement("INSERT INTO student_subjects (student_id, subject_id) VALUES (?, ?)")) {
            statement.setInt(1, studentId);
            statement.setInt(2, subjectId);
            statement.executeUpdate();
        }
    }

    @SuppressWarnings("unchecked")
    private static void evictStudent(int studentId) throws ReflectiveOperationException {
        Field field = Student.class.getDeclaredField("students");
        field.setAccessible(true);
        ((Map<Integer, Student>) field.get(null)).remove(studentId);
    }
}
