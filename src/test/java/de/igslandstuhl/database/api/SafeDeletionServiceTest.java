package de.igslandstuhl.database.api;

import de.igslandstuhl.database.server.sql.SQLiteConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SafeDeletionServiceTest {
    private static final AtomicInteger IDS = new AtomicInteger(700_000);
    @TempDir Path directory;
    SQLiteConnection db;
    int id;

    @BeforeEach void setup() throws Exception {
        id = IDS.getAndAdd(10);
        db = new SQLiteConnection(directory.resolve("safe-delete").toString());
        db.createTables();
        try (Statement statement = db.getSQLConnection().createStatement()) { statement.execute("PRAGMA foreign_keys=ON"); }
        db.writeTransaction(c -> {
            exec(c, "INSERT INTO school_years(id,label,week_count,current_week) VALUES(?,?,39,1)", id, "A2 year " + id);
            exec(c, "INSERT INTO semesters(id,label,position,school_year) VALUES(?,?,1,?)", id, "A2 semester " + id, id);
            exec(c, "INSERT INTO classes(id,label,grade) VALUES(?,?,6)", id, "A2 class " + id);
            exec(c, "INSERT INTO subjects(id,name) VALUES(?,?)", id, "A2 subject " + id);
            exec(c, "INSERT INTO teachers(id,first_name,last_name,email,password) VALUES(?,'A2','Teacher',?,'unused')", id, "a2-" + id + "@example.invalid");
            exec(c, "INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level) VALUES(?,'A2','Student',?,'unused',?,1)", id, "a2-student-" + id + "@example.invalid", id);
            return null;
        });
    }

    @AfterEach void close() throws Exception { if (db != null) db.close(); }

    static void exec(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    long count(String table, String column, int value) throws SQLException {
        try (PreparedStatement statement = db.getSQLConnection().prepareStatement("SELECT COUNT(*) FROM \"" + table + "\" WHERE \"" + column + "\"=?")) {
            statement.setInt(1, value);
            try (ResultSet result = statement.executeQuery()) { assertTrue(result.next()); return result.getLong(1); }
        }
    }

    @Test void subjectDeletePreflightAllowsUnusedSubjectAndDeletesCleanly() throws Exception {
        DeletionPreflight preview = SafeDeletionService.subjectPreflight(db, id);
        assertTrue(preview.deletable());
        SafeDeletionService.deleteSubject(db, id);
        assertEquals(0, count("subjects", "id", id));
        assertDatabaseIntegrity();
    }

    @Test void subjectDeletePreflightBlocksCourseGroupEvenWithoutLegacyAssignmentsOrCentralTopics() throws Exception {
        db.writeTransaction(c -> {
            exec(c, "INSERT INTO course_groups(id,subject,grade,semester,teacher,assignment_group,name) VALUES(?,?,6,?,?,?,?)", id, id, id, id, "WPF", "A2 group " + id);
            exec(c, "INSERT INTO course_group_members(course_group,student) VALUES(?,?)", id, id);
            return null;
        });
        DeletionPreflight preview = SafeDeletionService.subjectPreflight(db, id);
        assertFalse(preview.deletable());
        assertTrue(preview.groups().stream().anyMatch(group -> group.key().equals("course_groups")));
        assertThrows(ObjectInUseException.class, () -> SafeDeletionService.deleteSubject(db, id));
        assertEquals(1, count("course_groups", "id", id));
        assertEquals(1, count("course_group_members", "course_group", id));
        assertEquals(0, count("topics", "subject", id));
    }

    @Test void subjectDeletePreflightBlocksGroupReleasesAndKeepsAllRows() throws Exception {
        db.writeTransaction(c -> {
            exec(c, "INSERT INTO course_groups(id,subject,grade,semester,teacher,assignment_group,name) VALUES(?,?,6,?,?,?,?)", id, id, id, id, "WPF", "A2 group " + id);
            exec(c, "INSERT INTO course_group_members(course_group,student) VALUES(?,?)", id, id);
            exec(c, "INSERT INTO topics(id,name,subject,grade,number,semester) VALUES(?,?,?,6,1,?)", id, "A2 topic " + id, id, id);
            exec(c, "INSERT INTO tasks(id,topic,name,niveau,stage_number,tokens) VALUES(?,?,?,1,1,10)", id, id, "A2 task " + id);
            exec(c, "INSERT INTO course_group_task_releases(course_group,task,active) VALUES(?,?,1)", id, id);
            return null;
        });
        DeletionPreflight preview = SafeDeletionService.subjectPreflight(db, id);
        assertFalse(preview.deletable());
        assertTrue(preview.groups().stream().anyMatch(group -> group.key().equals("course_groups")));
        assertThrows(ObjectInUseException.class, () -> SafeDeletionService.deleteSubject(db, id));
        assertEquals(1, count("course_groups", "id", id));
        assertEquals(1, count("course_group_members", "course_group", id));
        assertEquals(1, count("course_group_task_releases", "course_group", id));
    }

    @Test void subjectDeletePreflightBlocksIndividualAssignmentAndFlexibleContent() throws Exception {
        db.writeTransaction(c -> {
            exec(c, "INSERT INTO curriculum_individual_assignments(student,semester,assignment_group,subject) VALUES(?,?,?,?)", id, id, "WPF", id);
            exec(c, "INSERT INTO flexible_topics(owner_teacher,subject,class,semester,grade,name) VALUES(?,?,?,?,6,?)", id, id, id, id, "A2 flexible " + id);
            return null;
        });
        DeletionPreflight preview = SafeDeletionService.subjectPreflight(db, id);
        assertFalse(preview.deletable());
        assertTrue(preview.groups().stream().anyMatch(group -> group.key().equals("curriculum_assignments")));
        assertThrows(ObjectInUseException.class, () -> SafeDeletionService.deleteSubject(db, id));
        assertEquals(1, count("curriculum_individual_assignments", "subject", id));
        assertEquals(1, count("flexible_topics", "subject", id));
    }

    @Test void subjectDeletePreflightBlocksPerformanceHistoryAndDoesNotCascade() throws Exception {
        db.writeTransaction(c -> {
            exec(c, "INSERT INTO topics(id,name,subject,grade,number,semester) VALUES(?,?,?,6,1,?)", id, "A2 history topic " + id, id, id);
            exec(c, "INSERT INTO tasks(id,topic,name,niveau,stage_number,tokens) VALUES(?,?,?,1,1,10)", id, id, "A2 history task " + id);
            exec(c, "INSERT INTO taskstats(student,task,status) VALUES(?,?,2)", id, id);
            return null;
        });
        DeletionPreflight preview = SafeDeletionService.subjectPreflight(db, id);
        assertFalse(preview.deletable());
        assertTrue(preview.groups().stream().anyMatch(group -> group.key().equals("learning_history")));
        assertThrows(ObjectInUseException.class, () -> SafeDeletionService.deleteSubject(db, id));
        assertEquals(1, count("taskstats", "task", id));
        assertEquals(1, count("topics", "id", id));
        assertEquals(1, count("tasks", "id", id));
    }

    @Test void subjectDeleteRechecksDependenciesInsideDeleteTransaction() throws Exception {
        assertTrue(SafeDeletionService.subjectPreflight(db, id).deletable());
        db.writeTransaction(c -> { exec(c, "INSERT INTO student_subjects(student_id,subject_id) VALUES(?,?)", id, id); return null; });
        assertThrows(ObjectInUseException.class, () -> SafeDeletionService.deleteSubject(db, id));
        assertEquals(1, count("subjects", "id", id));
        assertEquals(1, count("student_subjects", "subject_id", id));
    }

    @Test void protectedLegacyReligionAndEthikSubjectsCannotBeDeleted() throws Exception {
        db.writeTransaction(c -> {
            exec(c, "INSERT INTO subjects(id,name) VALUES(8,'Religion')");
            exec(c, "INSERT INTO subjects(id,name) VALUES(9,'Ethik')");
            return null;
        });
        for (int protectedId : new int[]{8, 9}) {
            assertFalse(SafeDeletionService.subjectPreflight(db, protectedId).deletable());
            assertNotNull(SafeDeletionService.subjectPreflight(db, protectedId).protectedReason());
            assertThrows(ObjectInUseException.class, () -> SafeDeletionService.deleteSubject(db, protectedId));
        }
    }

    @Test void teacherDeletePreflightBlocksGradeClassTutorContextAndCourseGroupAssignments() throws Exception {
        db.writeTransaction(c -> {
            exec(c, "INSERT INTO curriculum_grade_teachers(semester,grade,subject,teacher) VALUES(?,6,?,?)", id, id, id);
            exec(c, "INSERT INTO teacher_classes(teacher_id,class_id) VALUES(?,?)", id, id);
            exec(c, "CREATE TABLE IF NOT EXISTS curriculum_class_tutors(semester INTEGER NOT NULL REFERENCES semesters(id),class INTEGER NOT NULL REFERENCES classes(id),teacher INTEGER NOT NULL REFERENCES teachers(id),tutor_slot INTEGER NOT NULL,PRIMARY KEY(semester,class,tutor_slot))");
            exec(c, "INSERT INTO curriculum_class_tutors(semester,class,teacher,tutor_slot) VALUES(?,?,?,1)", id, id, id);
            exec(c, "INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade) VALUES(?,?,?,?,?,6)", id, id, id, id, id);
            exec(c, "INSERT INTO student_curriculum_stage_assessments(student,subject,semester,stage_type,stage_id,status) VALUES(?,?,?,'CENTRAL',?,'FAILED_ONCE')", id, id, id, id);
            exec(c, "INSERT INTO course_groups(id,subject,grade,semester,teacher,assignment_group,name) VALUES(?,?,6,?,?,?,?)", id, id, id, id, "WPF", "A2 group " + id);
            return null;
        });
        DeletionPreflight preview = SafeDeletionService.teacherPreflight(db, id);
        assertFalse(preview.deletable());
        assertTrue(preview.groups().stream().anyMatch(group -> group.key().equals("course_groups")));
        assertTrue(preview.groups().stream().anyMatch(group -> group.key().equals("tutor_assignments")));
        assertThrows(ObjectInUseException.class, () -> SafeDeletionService.deleteTeacher(db, id));
        assertEquals(1, count("teachers", "id", id));
        assertEquals(1, count("course_groups", "teacher", id));
        assertEquals(1, count("curriculum_class_tutors", "teacher", id));
        assertEquals(1, count("student_curriculum_stage_assessments", "student", id));
    }

    @Test void teacherDeletePreflightBlocksFlexibleOwnershipAndAttendanceHistory() throws Exception {
        db.writeTransaction(c -> {
            exec(c, "INSERT INTO flexible_topics(owner_teacher,subject,class,semester,grade,name) VALUES(?,?,?,?,6,?)", id, id, id, id, "A2 owned " + id);
            exec(c, "CREATE TABLE attendance_teacher_preferences(principal TEXT PRIMARY KEY, preference TEXT)");
            exec(c, "INSERT INTO attendance_teacher_preferences(principal,preference) VALUES(?,?)", "TEACHER:" + id, "synthetic");
            return null;
        });
        DeletionPreflight preview = SafeDeletionService.teacherPreflight(db, id);
        assertFalse(preview.deletable());
        assertTrue(preview.groups().stream().anyMatch(group -> group.key().equals("flexible_content")));
        assertTrue(preview.groups().stream().anyMatch(group -> group.key().equals("attendance")));
        assertThrows(ObjectInUseException.class, () -> SafeDeletionService.deleteTeacher(db, id));
        assertEquals(1, count("teachers", "id", id));
        assertEquals(1, count("attendance_teacher_preferences", "principal", id));
    }

    @Test void teacherDeletePreflightBlocksGraduationHistory() throws Exception {
        db.writeTransaction(c -> {
            exec(c, "CREATE TABLE student_graduation_history(id INTEGER PRIMARY KEY,student INTEGER NOT NULL REFERENCES students(id),old_graduation_level INTEGER NOT NULL,new_graduation_level INTEGER NOT NULL,changed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,teacher INTEGER NOT NULL REFERENCES teachers(id),semester INTEGER NOT NULL REFERENCES semesters(id))");
            exec(c, "INSERT INTO student_graduation_history(id,student,old_graduation_level,new_graduation_level,teacher,semester) VALUES(?, ?,1,2,?,?)", id, id, id, id);
            return null;
        });
        DeletionPreflight preview = SafeDeletionService.teacherPreflight(db, id);
        assertFalse(preview.deletable());
        assertTrue(preview.groups().stream().anyMatch(group -> group.key().equals("learning_history")));
        assertThrows(ObjectInUseException.class, () -> SafeDeletionService.deleteTeacher(db, id));
        assertEquals(1, count("student_graduation_history", "teacher", id));
    }

    @Test void teacherDeleteRechecksDependenciesTransactionally() throws Exception {
        assertTrue(SafeDeletionService.teacherPreflight(db, id).deletable());
        db.writeTransaction(c -> { exec(c, "INSERT INTO teacher_classes(teacher_id,class_id) VALUES(?,?)", id, id); return null; });
        assertThrows(ObjectInUseException.class, () -> SafeDeletionService.deleteTeacher(db, id));
        assertEquals(1, count("teachers", "id", id));
        assertEquals(1, count("teacher_classes", "teacher_id", id));
    }

    @Test void teacherDeletePreflightProtectsAdminAndStudentAccountIdentityCollisions() throws Exception {
        String email = "a2-" + id + "@example.invalid";
        db.writeTransaction(c -> {
            exec(c, "INSERT INTO admins(username,password_hash) VALUES(?,'unused')", email);
            return null;
        });
        DeletionPreflight preview = SafeDeletionService.teacherPreflight(db, id);
        assertFalse(preview.deletable());
        assertNotNull(preview.protectedReason());
        assertThrows(ObjectInUseException.class, () -> SafeDeletionService.deleteTeacher(db, id));
        assertEquals(1, count("teachers", "id", id));
        assertEquals(1, scalarText("SELECT COUNT(*) FROM admins WHERE username=?", email));
    }

    @SuppressWarnings("unchecked")
    @Test void unusedTeacherDeleteRemovesOnlyExactAccountNodesAndInvalidatesTeacherCache() throws Exception {
        String email = "a2-" + id + "@example.invalid";
        var idField = Teacher.class.getDeclaredField("teachers"); idField.setAccessible(true);
        var emailField = Teacher.class.getDeclaredField("teachersByEmail"); emailField.setAccessible(true);
        var byId = (java.util.Map<Integer, Teacher>) idField.get(null);
        var byEmail = (java.util.Map<String, Teacher>) emailField.get(null);
        byId.put(id, new Teacher(id, "A2", "Teacher", email, "unused"));
        byEmail.put(email, byId.get(id));
        db.writeTransaction(c -> {
            exec(c, "CREATE TABLE permissions(name TEXT PRIMARY KEY)");
            exec(c, "CREATE TABLE permnodes(permission TEXT,username TEXT,active INTEGER,PRIMARY KEY(permission,username))");
            exec(c, "CREATE TABLE user_roles(username TEXT,role TEXT,active INTEGER,PRIMARY KEY(username,role))");
            exec(c, "INSERT INTO permissions(name) VALUES('synthetic')");
            exec(c, "INSERT INTO permnodes(permission,username,active) VALUES('synthetic',?,1),('synthetic','other@example.invalid',1)", email);
            exec(c, "INSERT INTO user_roles(username,role,active) VALUES(?,'teacher',1),('other@example.invalid','teacher',1)", email);
            return null;
        });
        assertTrue(SafeDeletionService.teacherPreflight(db, id).deletable());
        assertEquals(email, SafeDeletionService.deleteTeacher(db, id));
        assertEquals(0, count("teachers", "id", id));
        assertEquals(0, scalarText("SELECT COUNT(*) FROM permnodes WHERE username=?", email));
        assertEquals(1, scalarText("SELECT COUNT(*) FROM permnodes WHERE username='other@example.invalid'", null));
        assertEquals(1, scalarText("SELECT COUNT(*) FROM user_roles WHERE username='other@example.invalid'", null));
        assertFalse(byId.containsKey(id));
        assertFalse(byEmail.containsKey(email));
        assertDatabaseIntegrity();
    }

    private long scalarText(String sql, String value) throws SQLException {
        try (PreparedStatement statement = db.getSQLConnection().prepareStatement(sql)) {
            if (sql.contains("?")) statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) { assertTrue(result.next()); return result.getLong(1); }
        }
    }

    private void assertDatabaseIntegrity() throws SQLException {
        try (Statement statement = db.getSQLConnection().createStatement(); ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
            assertTrue(result.next()); assertEquals("ok", result.getString(1));
        }
        try (Statement statement = db.getSQLConnection().createStatement(); ResultSet result = statement.executeQuery("PRAGMA foreign_key_check")) {
            assertFalse(result.next());
        }
    }
}
