package de.igslandstuhl.database.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import de.igslandstuhl.database.server.Server;

class StudentArchiveTest {
    private static final AtomicInteger SEQUENCE = new AtomicInteger(-1_200_000_000);

    @BeforeAll
    static void setupDatabase() throws SQLException {
        PreConditions.setupDatabase();
    }

    @Test
    void newStudentIsActiveAndSerializesActiveState() throws Exception {
        int id = SEQUENCE.getAndAdd(10);
        SchoolClass schoolClass = createClass(id, "archive-new-" + id, 5);

        Student student = Student.registerStudentWithPassword(id, "Active", "Student", email(id), "password", schoolClass, GraduationLevel.LEVEL1);

        assertTrue(student.isActive());
        assertTrue(Student.get(id).isActive());
        assertTrue(JsonParser.parseString(Student.get(id).toJSON()).getAsJsonObject().get("active").getAsBoolean());
    }

    @Test
    void archiveAndReactivateControlLookupListsAndCache() throws Exception {
        int id = SEQUENCE.getAndAdd(10);
        SchoolClass schoolClass = createClass(id, "archive-flow-" + id, 6);
        Student student = Student.registerStudentWithPassword(id, "Flow", "Student", email(id), "password", schoolClass, GraduationLevel.LEVEL2);

        assertTrue(student.isActive());
        assertNotNull(Student.getByEmail(email(id)));
        assertContainsStudent(Student.getAll(), id);
        assertContainsStudent(schoolClass.getStudents(), id);

        Student archived = student.archive();

        assertFalse(archived.isActive());
        assertFalse(Student.get(id).isActive());
        assertEquals(schoolClass.getId(), Student.get(id).getSchoolClass().getId());
        assertEquals(null, Student.getByEmail(email(id)));
        assertDoesNotContainStudent(Student.getAll(), id);
        assertContainsStudent(Student.getArchived(), id);
        assertDoesNotContainStudent(schoolClass.getStudents(), id);

        Student reactivated = archived.reactivate();

        assertTrue(reactivated.isActive());
        assertTrue(Student.get(id).isActive());
        assertNotNull(Student.getByEmail(email(id)));
        assertContainsStudent(Student.getAll(), id);
        assertDoesNotContainStudent(Student.getArchived(), id);
        assertContainsStudent(schoolClass.getStudents(), id);
    }

    @Test
    void archiveAndReactivateKeepHistoricalRowsUnchanged() throws Exception {
        int id = SEQUENCE.getAndAdd(10);
        int subjectId = id;
        int schoolYearId = id;
        int semesterId = id;
        int classId = id;
        int teacherId = id;
        int topicId = id;
        int taskId = id;

        insertHistoryFixture(id, subjectId, schoolYearId, semesterId, classId, teacherId, topicId, taskId);
        evictStudent(id);
        Student student = Student.get(id);
        assertNotNull(student);

        Counts before = counts(id, taskId, subjectId, semesterId);
        assertEquals(new Counts(1, 1, 1), before);

        student.archive();
        Counts afterArchive = counts(id, taskId, subjectId, semesterId);

        Student.get(id).reactivate();
        Counts afterReactivate = counts(id, taskId, subjectId, semesterId);

        assertEquals(before, afterArchive);
        assertEquals(before, afterReactivate);
        assertEquals(classId, scalar("SELECT class FROM students WHERE id = ?", id));
    }

    private static void insertHistoryFixture(int studentId, int subjectId, int schoolYearId, int semesterId, int classId, int teacherId, int topicId, int taskId) throws SQLException {
        Server.getInstance().getConnection().writeTransaction(c -> {
            exec(c, "INSERT INTO classes(id,label,grade) VALUES(?,?,5)", classId, "Archive-Class-" + studentId);
            exec(c, "INSERT INTO subjects(id,name) VALUES(?,?)", subjectId, "Archive-Subject-" + studentId);
            exec(c, "INSERT INTO teachers(id,first_name,last_name,email,password) VALUES(?,'Archive','Teacher',?,'hash')", teacherId, "archive-teacher-" + studentId + "@example.test");
            exec(c, "INSERT INTO school_years(id,label,week_count,current_week,current_semester) VALUES(?,?,39,1,?)", schoolYearId, "Archive-Year-" + studentId, semesterId);
            exec(c, "INSERT INTO semesters(id,label,position,school_year) VALUES(?,?,1,?)", semesterId, "Archive-Semester-" + studentId, schoolYearId);
            exec(c, "INSERT INTO topics(id,name,subject,grade,number,semester) VALUES(?,?,?,5,1,?)", topicId, "Archive-Topic-" + studentId, subjectId, semesterId);
            exec(c, "INSERT INTO tasks(id,topic,name,niveau,stage_number,tokens) VALUES(?,?,?,?,1,7)", taskId, topicId, "Archive-Task-" + studentId, TaskLevel.LEVEL1.getNumber());
            exec(c, "INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level) VALUES(?,'Archive','Student',?,'hash',?,1)", studentId, email(studentId), classId);
            exec(c, "INSERT INTO taskstats(student,task,status) VALUES(?,?,?)", studentId, taskId, Task.STATUS_COMPLETED);
            exec(c, "INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade) VALUES(?,?,?,?,?,5)", studentId, subjectId, semesterId, teacherId, classId);
            return null;
        });
    }

    private static SchoolClass createClass(int id, String label, int grade) throws Exception {
        Server.getInstance().getConnection().writeTransaction(c -> {
            exec(c, "INSERT INTO classes(id,label,grade) VALUES(?,?,?)", id, label, grade);
            return null;
        });
        return SchoolClass.get(id);
    }

    private static void exec(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            statement.executeUpdate();
        }
    }

    private static long scalar(String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = Server.getInstance().getConnection().getSQLConnection().prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static Counts counts(int studentId, int taskId, int subjectId, int semesterId) throws SQLException {
        return new Counts(
            scalar("SELECT COUNT(*) FROM taskstats WHERE student = ? AND task = ? AND status = ?", studentId, taskId, Task.STATUS_COMPLETED),
            scalar("SELECT COUNT(*) FROM student_curriculum_contexts WHERE student = ? AND subject = ? AND semester = ?", studentId, subjectId, semesterId),
            scalar("SELECT COUNT(*) FROM students WHERE id = ?", studentId)
        );
    }

    private static void assertContainsStudent(List<Student> students, int id) {
        assertTrue(students.stream().anyMatch(student -> student.getId() == id));
    }

    private static void assertDoesNotContainStudent(List<Student> students, int id) {
        assertFalse(students.stream().anyMatch(student -> student.getId() == id));
    }

    private static String email(int id) {
        return "archive-student-" + id + "@example.test";
    }

    @SuppressWarnings("unchecked")
    private static void evictStudent(int studentId) throws ReflectiveOperationException {
        Field field = Student.class.getDeclaredField("students");
        field.setAccessible(true);
        ((Map<Integer, Student>) field.get(null)).remove(studentId);
    }

    private record Counts(long taskstats, long curriculumContexts, long students) {}
}
