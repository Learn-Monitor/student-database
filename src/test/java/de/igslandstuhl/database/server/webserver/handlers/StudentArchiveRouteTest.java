package de.igslandstuhl.database.server.webserver.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.api.Admin;
import de.igslandstuhl.database.api.GraduationLevel;
import de.igslandstuhl.database.api.PreConditions;
import de.igslandstuhl.database.api.SchoolClass;
import de.igslandstuhl.database.api.Student;
import de.igslandstuhl.database.api.Task;
import de.igslandstuhl.database.api.TaskLevel;
import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.webserver.ContentType;
import de.igslandstuhl.database.server.webserver.Cookie;
import de.igslandstuhl.database.server.webserver.Status;
import de.igslandstuhl.database.server.webserver.WebPath;
import de.igslandstuhl.database.server.webserver.requests.RequestType;
import de.igslandstuhl.database.server.webserver.handlers.get.SQLRequestHandler;
import de.igslandstuhl.database.server.webserver.requests.PostRequest;
import de.igslandstuhl.database.server.webserver.responses.HttpResponse;
import de.igslandstuhl.database.server.webserver.sessions.Session;

class StudentArchiveRouteTest {
    private static final AtomicInteger SEQUENCE = new AtomicInteger(-1_210_000_000);
    private static final String ADMIN = "archive-admin";
    private static final String ADMIN_PASSWORD = "archive-admin-pass";

    @BeforeAll
    static void setup() throws Exception {
        PreConditions.setupDatabase();
        Admin.create(ADMIN, ADMIN_PASSWORD);
        PostRequestHandler.registerHandlers();
        SQLRequestHandler.register();
        WebPath.registerPaths();
    }

    @Test
    void nonAdminCannotArchiveStudent() throws Exception {
        Student student = fixtureStudent("forbidden", "password");
        Cookie studentCookie = login(student.getEmail(), "password");

        HttpResponse response = post("/archive-student", "{\"id\":" + student.getId() + "}", studentCookie);

        assertEquals(Status.FORBIDDEN, response.getStatus());
        assertTrue(Student.get(student.getId()).isActive());
    }

    @Test
    void studentHardDeleteEndpointIsNotRegistered() {
        assertNull(Registry.postRequestHandlerRegistry().get("/delete-student"));
        assertNull(Registry.webPathRegistry().get(WebPath.PathInfo.get("/delete-student", RequestType.POST)));
        assertNotNull(Registry.postRequestHandlerRegistry().get("/archive-student"));
        assertNotNull(Registry.postRequestHandlerRegistry().get("/reactivate-student"));
        assertNotNull(Registry.webPathRegistry().get(WebPath.PathInfo.get("/archive-student", RequestType.POST)));
        assertNotNull(Registry.webPathRegistry().get(WebPath.PathInfo.get("/reactivate-student", RequestType.POST)));
    }

    @Test
    void oldStudentHardDeleteEndpointReturnsNotFoundAndDoesNotMutateStudent() throws Exception {
        Student student = fixtureStudent("hard-delete-removed", "password");
        int taskId = student.getId() + 1;
        addTaskstat(student, taskId, Task.STATUS_COMPLETED);
        Cookie adminCookie = sessionCookieFor(ADMIN);

        HttpResponse response = post("/delete-student", "{\"id\":" + student.getId() + "}", adminCookie);

        assertEquals(Status.NOT_FOUND, response.getStatus());
        assertTrue(body(response).contains("Unknown post request path: /delete-student"));
        Student loaded = Student.get(student.getId());
        assertNotNull(loaded);
        assertTrue(loaded.isActive());
        assertEquals(student.getSchoolClass().getId(), loaded.getSchoolClass().getId());
        assertEquals(1, scalar("SELECT COUNT(*) FROM students WHERE id=?", student.getId()));
        assertEquals(Task.STATUS_COMPLETED, scalar("SELECT status FROM taskstats WHERE student=? AND task=?", student.getId(), taskId));
    }

    @Test
    void adminArchiveListAndReactivateAreIdempotent() throws Exception {
        Student student = fixtureStudent("route-flow", "password");
        Cookie adminCookie = sessionCookieFor(ADMIN);

        HttpResponse archive = post("/archive-student", "{\"id\":" + student.getId() + "}", adminCookie);
        assertEquals(Status.OK, archive.getStatus());
        assertEquals(ContentType.JSON, archive.getContentType());
        assertTrue(body(archive).contains("\"active\":false"));
        assertFalse(Student.get(student.getId()).isActive());
        assertNotNull(Student.get(student.getId()));

        HttpResponse archiveAgain = post("/archive-student", "{\"id\":" + student.getId() + "}", adminCookie);
        assertEquals(Status.OK, archiveAgain.getStatus());
        assertTrue(body(archiveAgain).contains("\"active\":false"));

        String archived = Server.getInstance().getSQLResource(ADMIN, "archived-students");
        String active = Server.getInstance().getSQLResource(ADMIN, "students");
        assertTrue(archived.contains(student.getEmail()));
        assertFalse(active.contains(student.getEmail()));

        HttpResponse reactivate = post("/reactivate-student", "{\"id\":" + student.getId() + "}", adminCookie);
        assertEquals(Status.OK, reactivate.getStatus());
        assertTrue(body(reactivate).contains("\"active\":true"));
        assertTrue(Student.get(student.getId()).isActive());

        HttpResponse reactivateAgain = post("/reactivate-student", "{\"id\":" + student.getId() + "}", adminCookie);
        assertEquals(Status.OK, reactivateAgain.getStatus());
        assertTrue(body(reactivateAgain).contains("\"active\":true"));

        archived = Server.getInstance().getSQLResource(ADMIN, "archived-students");
        active = Server.getInstance().getSQLResource(ADMIN, "students");
        assertFalse(archived.contains(student.getEmail()));
        assertTrue(active.contains(student.getEmail()));
    }

    @Test
    void unknownStudentIdReturnsControlledError() throws Exception {
        HttpResponse response = post("/archive-student", "{\"id\":2147483647}", sessionCookieFor(ADMIN));

        assertEquals(Status.BAD_REQUEST, response.getStatus());
        assertTrue(body(response).contains("Schüler nicht gefunden"));
    }

    @Test
    void archiveInvalidatesStudentSessionAndReactivationRequiresNewLogin() throws Exception {
        Student student = fixtureStudent("session-e2e", "correct-password");
        Student other = fixtureStudent("other-session", "other-password");
        Cookie adminCookie = sessionCookieFor(ADMIN);
        Cookie studentCookie = login(student.getEmail(), "correct-password");
        Cookie otherCookie = login(other.getEmail(), "other-password");

        assertTrue(userFor(studentCookie).isStudent());
        assertTrue(userFor(otherCookie).isStudent());

        assertEquals(Status.OK, post("/archive-student", "{\"id\":" + student.getId() + "}", adminCookie).getStatus());

        assertSame(User.ANONYMOUS, userFor(studentCookie));
        assertTrue(userFor(adminCookie).isAdmin());
        assertEquals(other.getEmail(), userFor(otherCookie).getUsername());
        assertEquals(Status.UNAUTHORIZED, post("/login", loginBody(student.getEmail(), "correct-password"), null).getStatus());

        assertEquals(Status.OK, post("/reactivate-student", "{\"id\":" + student.getId() + "}", adminCookie).getStatus());

        assertSame(User.ANONYMOUS, userFor(studentCookie));
        Cookie newStudentCookie = login(student.getEmail(), "correct-password");
        assertEquals(student.getEmail(), userFor(newStudentCookie).getUsername());
    }

    private static Student fixtureStudent(String suffix, String password) throws Exception {
        int id = SEQUENCE.getAndAdd(10);
        SchoolClass schoolClass = createClass(id, "archive-route-" + suffix + "-" + id, 5);
        return Student.registerStudentWithPassword(id, "Archive", "Route", "archive-route-" + suffix + "-" + id + "@example.test", password, schoolClass, GraduationLevel.LEVEL1);
    }

    private static SchoolClass createClass(int id, String label, int grade) throws Exception {
        Server.getInstance().getConnection().writeTransaction(c -> {
            exec(c, "INSERT INTO classes(id,label,grade) VALUES(?,?,?)", id, label, grade);
            return null;
        });
        return SchoolClass.get(id);
    }

    private static void addTaskstat(Student student, int taskId, int status) throws Exception {
        Server.getInstance().getConnection().writeTransaction(c -> {
            exec(c, "INSERT INTO subjects(id,name) VALUES(?,?)", taskId, "Archive Route Subject " + taskId);
            exec(c, "INSERT INTO topics(id,name,subject,grade,number) VALUES(?,?,?,?,1)", taskId, "Archive Route Topic " + taskId, taskId, student.getSchoolClass().getGrade());
            exec(c, "INSERT INTO tasks(id,topic,name,niveau,stage_number,tokens) VALUES(?,?,?,?,1,5)", taskId, taskId, "Archive Route Task " + taskId, TaskLevel.LEVEL1.getNumber());
            exec(c, "INSERT INTO taskstats(student,task,status) VALUES(?,?,?)", student.getId(), taskId, status);
            return null;
        });
    }

    private static void exec(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            statement.executeUpdate();
        }
    }

    private static Cookie login(String username, String password) throws Exception {
        HttpResponse response = post("/login", loginBody(username, password), null);
        assertEquals(Status.FOUND, response.getStatus());
        return responseCookie(response);
    }

    private static String loginBody(String username, String password) {
        return "username=" + username.replace("@", "%40") + "&password=" + password;
    }

    private static Cookie sessionCookieFor(String username) {
        PostRequest request = new PostRequest("POST /login HTTP/1.1", "", "127.0.0.1", true);
        Session session = Server.getInstance().getWebServer().getSessionManager().getSession(request);
        Server.getInstance().getWebServer().getSessionManager().addSessionUser(session, username);
        return session.createSessionCookie();
    }

    private static User userFor(Cookie cookie) {
        PostRequest request = new PostRequest("POST /dashboard HTTP/1.1\r\nCookie: " + cookie, "", "127.0.0.1", true);
        return Server.getInstance().getWebServer().getSessionManager().getSessionUser(request);
    }

    private static HttpResponse post(String path, String body, Cookie cookie) throws Exception {
        String header = "POST " + path + " HTTP/1.1\r\nContent-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n";
        if (cookie != null) header += "Cookie: " + cookie + "\r\n";
        PostRequest request = new PostRequest(header, body, "127.0.0.1", true);
        return PostRequestHandler.getInstance().handlePostRequest(request);
    }

    private static String body(HttpResponse response) {
        String raw = raw(response);
        int split = raw.indexOf("\r\n\r\n");
        return split >= 0 ? raw.substring(split + 4) : raw;
    }

    private static long scalar(String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = Server.getInstance().getConnection().getSQLConnection().prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static Cookie responseCookie(HttpResponse response) {
        String raw = raw(response);
        for (String line : raw.split("\\r?\\n")) {
            if (line.startsWith("Set-Cookie: ")) {
                String value = line.substring("Set-Cookie: ".length()).split(";", 2)[0];
                return Cookie.parse(value)[0];
            }
        }
        throw new AssertionError("Response did not contain Set-Cookie header:\n" + raw);
    }

    private static String raw(HttpResponse response) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.respond(new PrintStream(out, true, StandardCharsets.UTF_8));
        return out.toString(StandardCharsets.UTF_8);
    }
}
