package de.igslandstuhl.database.server.webserver.handlers;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.api.GraduationLevel;
import de.igslandstuhl.database.api.SchoolClass;
import de.igslandstuhl.database.api.Student;
import de.igslandstuhl.database.api.Subject;
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
            exec(c, "INSERT INTO school_years(id,label,week_count,current_week) VALUES(?,?,39,1)", id, "Year-" + id);
            exec(c, "INSERT INTO semesters(id,label,position,school_year) VALUES(?,?,1,?)", id, "Semester-" + id, id);
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
