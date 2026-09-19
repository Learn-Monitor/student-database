package de.igslandstuhl.database.server.webserver.handlers;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.api.Admin;
import de.igslandstuhl.database.api.PreConditions;
import de.igslandstuhl.database.api.Teacher;
import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.webserver.Cookie;
import de.igslandstuhl.database.server.webserver.Status;
import de.igslandstuhl.database.server.webserver.requests.APIPostRequest;
import de.igslandstuhl.database.server.webserver.requests.HttpHeader;
import de.igslandstuhl.database.server.webserver.requests.PostRequest;
import de.igslandstuhl.database.server.webserver.responses.HttpResponse;
import de.igslandstuhl.database.server.webserver.responses.PostResponse;
import de.igslandstuhl.database.server.webserver.sessions.Session;

class TeacherProfileHandlerTest {
    private static final String ADMIN = "teacher-profile-admin-" + System.nanoTime();

    @BeforeAll
    static void setup() throws SQLException {
        PreConditions.setupDatabase();
        Admin.create(ADMIN, "teacher-profile-admin-pass");
    }

    @Test
    void nameOnlyUpdateKeepsPasswordAndTeacherSession() throws Exception {
        Teacher teacher = fixtureTeacher("name-only", "old-password");
        String oldHash = teacher.getPasswordHash();
        Cookie teacherCookie = sessionCookieFor(teacher.getEmail());
        Cookie adminCookie = sessionCookieFor(ADMIN);

        PostResponse response = editProfile(payload(teacher, "New", "Name", teacher.getEmail(), ""));

        assertEquals(Status.FOUND, response.getStatus());
        assertTrue(raw(response).contains("Location: /manage_teachers"));
        Teacher loaded = Teacher.fromEmail(teacher.getEmail());
        assertEquals("New", loaded.getFirstName());
        assertEquals("Name", loaded.getLastName());
        assertEquals(oldHash, loaded.getPasswordHash());
        assertEquals(teacher.getEmail(), userFor(teacherCookie).getUsername());
        assertTrue(userFor(adminCookie).isAdmin());
    }

    @Test
    void passwordUpdateInvalidatesOnlyTeacherSessions() throws Exception {
        Teacher teacher = fixtureTeacher("password-change", "old-password");
        String oldHash = teacher.getPasswordHash();
        Cookie teacherCookie = sessionCookieFor(teacher.getEmail());
        Cookie adminCookie = sessionCookieFor(ADMIN);

        PostResponse response = editProfile(payload(teacher, "Pass", "Changed", teacher.getEmail(), "new-password"));

        assertEquals(Status.FOUND, response.getStatus());
        assertTrue(raw(response).contains("Location: /manage_teachers"));
        Teacher loaded = Teacher.fromEmail(teacher.getEmail());
        assertNotEquals(oldHash, loaded.getPasswordHash());
        assertEquals(User.passHash("new-password"), loaded.getPasswordHash());
        assertSame(User.ANONYMOUS, userFor(teacherCookie));
        assertTrue(userFor(adminCookie).isAdmin());
    }

    @Test
    void manipulatedLoginReturnsBadRequestWithoutMutationOrSessionInvalidation() throws Exception {
        Teacher teacher = fixtureTeacher("login-change", "old-password");
        String oldHash = teacher.getPasswordHash();
        Cookie teacherCookie = sessionCookieFor(teacher.getEmail());

        PostResponse response = editProfile(payload(teacher, "Changed", "Name", uniqueLogin("other"), "new-password"));

        assertEquals(Status.BAD_REQUEST, response.getStatus());
        assertTrue(raw(response).contains("Loginname kann hier nicht geändert werden."));
        Teacher loaded = Teacher.fromEmail(teacher.getEmail());
        assertEquals("First", loaded.getFirstName());
        assertEquals("Teacher", loaded.getLastName());
        assertEquals(oldHash, loaded.getPasswordHash());
        assertEquals("First", teacherField(teacher.getEmail(), "first_name"));
        assertEquals("Teacher", teacherField(teacher.getEmail(), "last_name"));
        assertEquals(oldHash, teacherField(teacher.getEmail(), "password"));
        assertEquals(teacher.getEmail(), userFor(teacherCookie).getUsername());
    }

    @Test
    void missingAndInvalidTeacherIdReturnControlledBadRequest() {
        assertEquals(Status.BAD_REQUEST,
                editProfile("firstName=No&lastName=Id&email=no-id%40example.test&password=").getStatus());
        assertEquals(Status.BAD_REQUEST,
                editProfile("id=abc&firstName=Bad&lastName=Id&email=bad-id%40example.test&password=").getStatus());
        assertEquals(Status.BAD_REQUEST,
                editProfile("id=2147483647&firstName=Missing&lastName=Teacher&email=missing%40example.test&password=").getStatus());
    }

    @Test
    void emptyRequiredFieldsReturnBadRequest() throws Exception {
        Teacher teacher = fixtureTeacher("empty-fields", "password");

        assertEquals(Status.BAD_REQUEST, editProfile(payload(teacher, "", "Teacher", teacher.getEmail(), "")).getStatus());
        assertEquals(Status.BAD_REQUEST, editProfile(payload(teacher, "First", "", teacher.getEmail(), "")).getStatus());
        assertEquals(Status.BAD_REQUEST, editProfile(payload(teacher, "First", "Teacher", "", "")).getStatus());
    }

    private static Teacher fixtureTeacher(String suffix, String password) throws SQLException {
        return Teacher.registerTeacher("First", "Teacher", uniqueLogin(suffix), password);
    }

    private static String uniqueLogin(String suffix) {
        return "teacher-profile-" + suffix + "-" + System.nanoTime() + "@example.test";
    }

    private static PostResponse editProfile(String body) {
        return PostRequestHandler.handleEditTeacherProfile(new APIPostRequest(
                new HttpHeader("POST /edit-teacher-profile HTTP/1.1\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                        + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"),
                body,
                "127.0.0.1",
                true));
    }

    private static String payload(Teacher teacher, String firstName, String lastName, String email, String password) {
        return "id=" + teacher.getId()
                + "&firstName=" + firstName
                + "&lastName=" + lastName
                + "&email=" + email.replace("@", "%40")
                + "&password=" + password;
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

    private static String teacherField(String login, String field) throws SQLException {
        try (var statement = Server.getInstance().getConnection().getSQLConnection()
                .prepareStatement("SELECT " + field + " FROM teachers WHERE email = ?")) {
            statement.setString(1, login);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static String raw(HttpResponse response) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.respond(new PrintStream(out, true, StandardCharsets.UTF_8));
        return out.toString(StandardCharsets.UTF_8);
    }
}
