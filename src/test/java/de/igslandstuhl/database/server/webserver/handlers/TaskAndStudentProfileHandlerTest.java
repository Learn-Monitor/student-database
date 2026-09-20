package de.igslandstuhl.database.server.webserver.handlers;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.api.GraduationLevel;
import de.igslandstuhl.database.api.Admin;
import de.igslandstuhl.database.api.SchoolClass;
import de.igslandstuhl.database.api.Student;
import de.igslandstuhl.database.api.Subject;
import de.igslandstuhl.database.api.SubjectRequest;
import de.igslandstuhl.database.api.Task;
import de.igslandstuhl.database.api.TaskLevel;
import de.igslandstuhl.database.api.Teacher;
import de.igslandstuhl.database.api.Topic;
import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.api.curriculum.Curriculum;
import de.igslandstuhl.database.api.curriculum.CurriculumEnrollment;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLiteConnection;
import de.igslandstuhl.database.server.webserver.Status;
import de.igslandstuhl.database.server.webserver.requests.APIPostRequest;
import de.igslandstuhl.database.server.webserver.requests.HttpHeader;
import de.igslandstuhl.database.server.webserver.responses.PostResponse;

class TaskAndStudentProfileHandlerTest {
    private static final AtomicInteger SEQUENCE = new AtomicInteger(60000);
    private SQLiteConnection db;
    private Curriculum curriculum;
    private int id;
    private int topic;
    private int task;
    private Curriculum.Scope scope;
    private Curriculum.Actor teacher;

    @BeforeEach
    void setup() throws Exception {
        db = Server.getInstance().getConnection();
        db.createTables();
        curriculum = new Curriculum(db);
        id = SEQUENCE.getAndAdd(20);
        topic = id;
        db.writeTransaction(c -> {
            exec(c, "INSERT INTO subjects(id,name) VALUES(?,?)", id, "Subject-" + id);
            exec(c, "INSERT INTO school_years(id,label,week_count,current_week,start_date,end_date,current_semester) VALUES(?,?,39,1,'2020-01-01','2099-12-31',?)", id, "Year-" + id, id);
            exec(c, "INSERT INTO semesters(id,label,position,school_year) VALUES(?,?,1,?)", id, "Semester-" + id, id);
            exec(c, "INSERT INTO semesters(id,label,position,school_year) VALUES(?,?,2,?)", id + 1, "Semester-" + (id + 1), id);
            exec(c, "INSERT INTO classes(id,label,grade,active) VALUES(?,?,5,1)", id, "Class-" + id);
            exec(c, "INSERT INTO classes(id,label,grade,active) VALUES(?,?,5,1)", id + 1, "Other-" + id);
            exec(c, "INSERT INTO teachers(id,first_name,last_name,email,password) VALUES(?,'Managed','Teacher',?,'unused')", id, "teacher" + id + "@example.invalid");
            exec(c, "INSERT INTO teachers(id,first_name,last_name,email,password) VALUES(?,'Other','Teacher',?,'unused')", id + 1, "other" + id + "@example.invalid");
            exec(c, "INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level) VALUES(?,'Synthetic','Student',?,'unused',?,1)", id, "student" + id + "@example.invalid", id);
            exec(c, "INSERT INTO topics(id,name,subject,grade,number,semester) VALUES(?,?,?,5,1,?)", topic, "Topic-" + id, id, id);
            return null;
        });
        task = curriculum.createCentralTask(topic, "Central", TaskLevel.LEVEL1, 5);
        teacher = new Curriculum.Actor(false, id);
        scope = new Curriculum.Scope(id, id, id, id);
        db.writeTransaction(c -> {
            exec(c, "INSERT INTO curriculum_class_teachers(semester,class,subject,teacher) VALUES(?,?,?,?)", id, id, id, id);
            exec(c, "INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade) VALUES(?,?,?,?,?,5)", id, id, id, id, id);
            return null;
        });
        new CurriculumEnrollment(curriculum).release(teacher, scope, topic, null, true);
    }

    @Test
    void centralCompletionIsTeacherConfirmedAndManagedContextBound() throws Exception {
        assertEquals(Status.FORBIDDEN, taskChange(Student.get(id), Task.STATUS_COMPLETED).getStatus());
        assertEquals(0, scalar("SELECT COUNT(*) FROM taskstats WHERE student=? AND task=?", id, task));

        assertEquals(Status.OK, taskChange(Teacher.get(id), Task.STATUS_COMPLETED).getStatus());
        assertEquals(Task.STATUS_COMPLETED, scalar("SELECT status FROM taskstats WHERE student=? AND task=?", id, task));
        assertEquals(5L, curriculum.studentProgress(Student.get(id), id, id).get("totalTokens"));

        Student.get(id).changeTaskStatus(Task.get(task), Task.STATUS_NOT_STARTED);
        assertEquals(Status.FORBIDDEN, taskChange(Teacher.get(id + 1), Task.STATUS_COMPLETED).getStatus());
        assertEquals(Task.STATUS_NOT_STARTED, scalar("SELECT status FROM taskstats WHERE student=? AND task=?", id, task));
    }

    @Test
    void curriculumUnpublishAndLockCannotRemoveConfirmedCentralCoins() throws Exception {
        assertEquals(Status.OK, taskChange(Teacher.get(id), Task.STATUS_COMPLETED).getStatus());
        new CurriculumEnrollment(curriculum).release(teacher, scope, topic, null, false);
        assertEquals(5L, curriculum.studentProgress(Student.get(id), id, id).get("totalTokens"));

        PostResponse response = taskChange(Teacher.get(id), Task.STATUS_LOCKED);
        assertEquals(Status.CONFLICT, response.getStatus());
        assertEquals(Task.STATUS_COMPLETED, scalar("SELECT status FROM taskstats WHERE student=? AND task=?", id, task));
        assertEquals(5L, curriculum.studentProgress(Student.get(id), id, id).get("totalTokens"));
        assertEquals(Status.FORBIDDEN, taskChange(Teacher.get(id + 1), Task.STATUS_LOCKED).getStatus());
        assertEquals(Task.STATUS_COMPLETED, scalar("SELECT status FROM taskstats WHERE student=? AND task=?", id, task));
    }

    @Test
    void unfinishedCentralTaskCanStillBeLocked() throws Exception {
        assertEquals(Status.OK, taskChange(Teacher.get(id), Task.STATUS_LOCKED).getStatus());
        assertEquals(Task.STATUS_LOCKED, scalar("SELECT status FROM taskstats WHERE student=? AND task=?", id, task));
    }

    @Test
    void flexibleThenCentralHttpLeavesOnlyCentralActive() throws Exception {
        var flexible = curriculum.create(teacher, scope, "Flexible", 5);
        new CurriculumEnrollment(curriculum).release(teacher, scope, null, null, null, flexible.id(), true);
        assertEquals(Status.OK, flexibleChange(Student.get(id), "/begin-flexible-task", flexible.id()).getStatus());
        assertEquals("FLEXIBLE", curriculum.activeStage(id, id).type().name());

        assertEquals(Status.OK, taskChange(Student.get(id), Task.STATUS_IN_PROGRESS).getStatus());
        assertEquals("CENTRAL", curriculum.activeStage(id, id).type().name());
        assertEquals(task, curriculum.activeStage(id, id).taskId());
        assertEquals(Task.STATUS_IN_PROGRESS, scalar("SELECT status FROM taskstats WHERE student=? AND task=?", id, task));
        assertEquals(1, scalar("SELECT COUNT(*) FROM student_active_curriculum_stages WHERE student=? AND subject=?", id, id));
    }

    @Test
    void centralThenFlexibleHttpLeavesOnlyFlexibleActive() throws Exception {
        var flexible = curriculum.create(teacher, scope, "Flexible", 5);
        new CurriculumEnrollment(curriculum).release(teacher, scope, null, null, null, flexible.id(), true);
        assertEquals(Status.OK, taskChange(Student.get(id), Task.STATUS_IN_PROGRESS).getStatus());
        assertEquals(Task.STATUS_IN_PROGRESS, scalar("SELECT status FROM taskstats WHERE student=? AND task=?", id, task));

        assertEquals(Status.OK, flexibleChange(Student.get(id), "/begin-flexible-task", flexible.id()).getStatus());
        assertEquals("FLEXIBLE", curriculum.activeStage(id, id).type().name());
        assertEquals(flexible.id(), curriculum.activeStage(id, id).taskId());
        assertEquals(Task.STATUS_NOT_STARTED, scalar("SELECT status FROM taskstats WHERE student=? AND task=?", id, task));
        assertEquals(1, scalar("SELECT COUNT(*) FROM student_active_curriculum_stages WHERE student=? AND subject=?", id, id));
    }

    @Test
    void archivedClassStudentCanBeReassignedWithValidProfilePayloadOnly() throws Exception {
        Student.get(id).changeTaskStatus(Task.get(task), Task.STATUS_COMPLETED);
        SchoolClass.get(id).delete();
        assertEquals(SchoolClass.UNASSIGNED_CLASS_ID, scalar("SELECT class FROM students WHERE id=?", id));
        assertEquals(1, scalar("SELECT COUNT(*) FROM taskstats WHERE student=?", id));

        String payload = profilePayload(Map.of("classId", String.valueOf(id + 1), "graduationLevel", "2"));
        assertEquals(Status.FOUND, PostRequestHandler.handleEditStudentProfile(profileRequest(payload)).getStatus());
        assertEquals(id + 1, scalar("SELECT class FROM students WHERE id=?", id));
        assertEquals(2, scalar("SELECT graduation_level FROM students WHERE id=?", id));
        assertEquals(1, scalar("SELECT COUNT(*) FROM students WHERE id=?", id));
        assertEquals(Task.STATUS_COMPLETED, scalar("SELECT status FROM taskstats WHERE student=? AND task=?", id, task));

        String invalidClass = profilePayload(Map.of("classId", String.valueOf(id + 999), "graduationLevel", "2"));
        assertEquals(Status.BAD_REQUEST, PostRequestHandler.handleEditStudentProfile(profileRequest(invalidClass)).getStatus());
        assertEquals(id + 1, scalar("SELECT class FROM students WHERE id=?", id));

        String invalidLevel = profilePayload(Map.of("classId", String.valueOf(id + 1), "graduationLevel", "99"));
        assertEquals(Status.BAD_REQUEST, PostRequestHandler.handleEditStudentProfile(profileRequest(invalidLevel)).getStatus());
        assertEquals(2, scalar("SELECT graduation_level FROM students WHERE id=?", id));
    }

    @Test
    void subjectRequestsPersistMultipleTypesAndRemoveOnlyMatchingSignal() throws Exception {
        assertEquals(Status.OK, subjectRequest(Student.get(id), "{\"subjectId\":" + id + ",\"subjectRequest\":\"hilfe\"}").getStatus());
        assertEquals(1, scalar("SELECT COUNT(*) FROM student_subject_requests WHERE student=? AND subject=? AND semester=? AND request_type='HELP'", id, id, id));

        assertEquals(Status.OK, subjectRequest(Student.get(id), "{\"subjectId\":" + id + ",\"subjectRequest\":\"partner\"}").getStatus());
        assertEquals(Status.OK, subjectRequest(Student.get(id), "{\"subjectId\":" + id + ",\"subjectRequest\":\"hilfe\"}").getStatus());
        assertEquals(2, scalar("SELECT COUNT(*) FROM student_subject_requests WHERE student=? AND subject=? AND semester=?", id, id, id));
        assertEquals(1, scalar("SELECT COUNT(*) FROM student_subject_requests WHERE student=? AND subject=? AND semester=? AND request_type='HELP'", id, id, id));

        int otherSubject = id + 1;
        db.writeTransaction(c -> {
            exec(c, "INSERT INTO subjects(id,name) VALUES(?,?)", otherSubject, "Other-" + id);
            exec(c, "INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade) VALUES(?,?,?,?,?,5)", id, otherSubject, id, id, id);
            return null;
        });
        assertEquals(Status.OK, subjectRequest(Student.get(id), "{\"subjectId\":" + otherSubject + ",\"subjectRequest\":\"betreuung\"}").getStatus());
        assertEquals(Status.OK, subjectRequest(Student.get(id), "{\"subjectId\":" + id + ",\"subjectRequest\":\"hilfe\",\"remove\":true}").getStatus());
        assertEquals(0, scalar("SELECT COUNT(*) FROM student_subject_requests WHERE student=? AND subject=? AND semester=? AND request_type='HELP'", id, id, id));
        assertEquals(1, scalar("SELECT COUNT(*) FROM student_subject_requests WHERE student=? AND subject=? AND semester=? AND request_type='PARTNER'", id, id, id));
        assertEquals(1, scalar("SELECT COUNT(*) FROM student_subject_requests WHERE student=? AND subject=? AND semester=? AND request_type='EXPERIMENT'", id, otherSubject, id));
        assertEquals(Status.OK, subjectRequest(Student.get(id), "{\"subjectId\":" + id + ",\"subjectRequest\":\"hilfe\",\"remove\":true}").getStatus());
    }

    @Test
    void allSubjectRequestTypesSurviveReloadAndRenderGermanJsonValues() throws Exception {
        for (String type : List.of("hilfe", "partner", "betreuung", "gelingensnachweis"))
            assertEquals(Status.OK, subjectRequest(Student.get(id), "{\"subjectId\":" + id + ",\"subjectRequest\":\"" + type + "\"}").getStatus());
        assertEquals(4, scalar("SELECT COUNT(*) FROM student_subject_requests WHERE student=? AND subject=? AND semester=?", id, id, id));

        Student reloaded = Student.get(id);
        assertEquals(Set.of(SubjectRequest.HELP, SubjectRequest.PARTNER, SubjectRequest.EXPERIMENT, SubjectRequest.EXAM), reloaded.getCurrentRequests(Subject.get(id)));
        String json = reloaded.toJSON();
        assertTrue(json.contains("\"hilfe\""));
        assertTrue(json.contains("\"partner\""));
        assertTrue(json.contains("\"betreuung\""));
        assertTrue(json.contains("\"gelingensnachweis\""));
    }

    @Test
    void subjectRequestEndpointRejectsInvalidActorsPayloadsAndContexts() throws Exception {
        assertEquals(Status.BAD_REQUEST, subjectRequest(Student.get(id), "{\"subjectId\":" + id + ",\"subjectRequest\":\"unbekannt\"}").getStatus());
        int foreignSubject = id + 99;
        db.writeTransaction(c -> { exec(c, "INSERT INTO subjects(id,name) VALUES(?,?)", foreignSubject, "Foreign-" + id); return null; });
        assertEquals(Status.FORBIDDEN, subjectRequest(Student.get(id), "{\"subjectId\":" + foreignSubject + ",\"subjectRequest\":\"hilfe\"}").getStatus());
        assertEquals(Status.FORBIDDEN, subjectRequest(Student.get(id), "{\"subjectId\":" + foreignSubject + ",\"subjectRequest\":\"hilfe\",\"remove\":true}").getStatus());
        assertEquals(Status.BAD_REQUEST, subjectRequest(Student.get(id), "{\"studentId\":" + (id + 1) + ",\"subjectId\":" + id + ",\"subjectRequest\":\"hilfe\"}").getStatus());
        assertEquals(Status.FORBIDDEN, subjectRequest(Teacher.get(id), "{\"subjectId\":" + id + ",\"subjectRequest\":\"hilfe\"}").getStatus());
        assertEquals(Status.FORBIDDEN, subjectRequest(Admin.create("signal-admin-" + id, "synthetic-test-only"), "{\"subjectId\":" + id + ",\"subjectRequest\":\"hilfe\"}").getStatus());
        db.writeTransaction(c -> { exec(c, "UPDATE school_years SET current_semester=NULL"); return null; });
        assertEquals(Status.CONFLICT, subjectRequest(Student.get(id), "{\"subjectId\":" + id + ",\"subjectRequest\":\"hilfe\"}").getStatus());
    }

    @Test
    void subjectRequestsAreIsolatedBySemesterAndNotChangedByStageTransitions() throws Exception {
        assertEquals(Status.OK, subjectRequest(Student.get(id), "{\"subjectId\":" + id + ",\"subjectRequest\":\"hilfe\"}").getStatus());
        assertTrue(Student.get(id).getCurrentRequests(Subject.get(id)).contains(SubjectRequest.HELP));

        db.writeTransaction(c -> {
            exec(c, "INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade) VALUES(?,?,?,?,?,5)", id, id, id + 1, id, id);
            exec(c, "UPDATE school_years SET current_semester=? WHERE id=?", id + 1, id);
            return null;
        });
        assertFalse(Student.get(id).getCurrentRequests(Subject.get(id)).contains(SubjectRequest.HELP));
        assertEquals(Status.OK, subjectRequest(Student.get(id), "{\"subjectId\":" + id + ",\"subjectRequest\":\"partner\"}").getStatus());
        assertEquals(1, scalar("SELECT COUNT(*) FROM student_subject_requests WHERE student=? AND subject=? AND semester=? AND request_type='HELP'", id, id, id));
        assertEquals(1, scalar("SELECT COUNT(*) FROM student_subject_requests WHERE student=? AND subject=? AND semester=? AND request_type='PARTNER'", id, id, id + 1));

        db.writeTransaction(c -> { exec(c, "UPDATE school_years SET current_semester=? WHERE id=?", id, id); return null; });
        var flexible = curriculum.create(teacher, scope, "Flexible signal", 5);
        new CurriculumEnrollment(curriculum).release(teacher, scope, null, null, null, flexible.id(), true);
        assertEquals(Status.OK, taskChange(Student.get(id), Task.STATUS_IN_PROGRESS).getStatus());
        assertEquals(Status.OK, flexibleChange(Student.get(id), "/begin-flexible-task", flexible.id()).getStatus());
        assertEquals(Status.OK, flexibleChange(Student.get(id), "/cancel-flexible-task", flexible.id()).getStatus());
        new CurriculumEnrollment(curriculum).release(teacher, scope, null, task, false);
        assertEquals(1, scalar("SELECT COUNT(*) FROM student_subject_requests WHERE student=? AND subject=? AND semester=? AND request_type='HELP'", id, id, id));
    }

    private PostResponse taskChange(User user, int status) throws Exception {
        return PostRequestHandler.handleTaskChange(apiRequest(user, "{\"studentId\":" + id + ",\"taskId\":" + task + "}"), status);
    }

    private PostResponse flexibleChange(User user, String path, int taskId) {
        String body = "{\"taskId\":" + taskId + "}";
        return CurriculumRequestHandler.handle(new APIPostRequest(new HttpHeader("POST " + path + " HTTP/1.1\r\nContent-Type: application/json\r\nContent-Length: " + body.length() + "\r\n"), body, "127.0.0.1", true) {
            @Override public User getUser() { return user; }
        });
    }

    private APIPostRequest apiRequest(User user, String body) {
        return new APIPostRequest(new HttpHeader("POST /complete-task HTTP/1.1\r\nContent-Type: application/json\r\nContent-Length: " + body.length() + "\r\n"), body, "127.0.0.1", true) {
            @Override public User getUser() { return user; }
            @Override public Student getCurrentStudent() { return user.isStudent() ? user.asStudent() : Student.get(getInt("studentId")); }
        };
    }

    private APIPostRequest profileRequest(String body) {
        return new APIPostRequest(new HttpHeader("POST /edit-student-profile HTTP/1.1\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: " + body.length() + "\r\n"), body, "127.0.0.1", true);
    }

    private PostResponse subjectRequest(User user, String body) throws Exception {
        return PostRequestHandler.handleSubjectRequest(new APIPostRequest(new HttpHeader("POST /subject-request HTTP/1.1\r\nContent-Type: application/json\r\nContent-Length: " + body.length() + "\r\n"), body, "127.0.0.1", true) {
            @Override public User getUser() { return user; }
        });
    }

    private String profilePayload(Map<String, String> overrides) {
        String classId = overrides.getOrDefault("classId", String.valueOf(id + 1));
        String level = overrides.getOrDefault("graduationLevel", String.valueOf(GraduationLevel.LEVEL1.getLevel()));
        return "id=" + id + "&firstName=Reassigned&lastName=Student&email=reassigned" + id
                + "%40example.invalid&password=&classId=" + classId + "&graduationLevel=" + level;
    }

    private static void exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            statement.executeUpdate();
        }
    }

    private long scalar(String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = db.getSQLConnection().prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }
}
