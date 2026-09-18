package de.igslandstuhl.database.server.webserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.api.PreConditions;
import de.igslandstuhl.database.server.webserver.requests.PostRequest;
import de.igslandstuhl.database.server.webserver.sessions.Session;
import de.igslandstuhl.database.server.webserver.sessions.SessionManager;
import de.igslandstuhl.database.server.webserver.sessions.SessionStorage;
import de.igslandstuhl.database.server.webserver.handlers.SessionValidationResult;

public class SessionManagerTest {
    private static final String LOCALHOST = "127.0.0.1";
    SessionManager sessionManager;
    PostRequest sessionRequest;
    PostRequest requestWithoutSession;
    @BeforeEach
    void setup() {
        sessionManager = new SessionManager(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        sessionRequest = new PostRequest("POST /student-data HTTP/1.1\r\n" + //
                        "Cookie: test=test;session=" + UUID.randomUUID().toString() + ";other=value", null, LOCALHOST, true);
        requestWithoutSession = new PostRequest("POST /login HTTP/1.1", null, LOCALHOST, true);
    }
    @Test
    void testAddSessionUser() throws SQLException {
        Session session = sessionManager.getSession(sessionRequest);
        sessionManager.addSessionUser(session, "adminUser");
        PreConditions.setupDatabase();
        PreConditions.addSampleAdmin();
        assertNotNull(sessionManager.getSessionUser(session));
        assertNotNull(sessionManager.getSessionUser(sessionRequest));
        assertEquals("adminUser", sessionManager.getSessionUser(session).getUsername());
        assertTrue(sessionManager.getSessionUser(session).isAdmin());
    }
    @Test
    void testGetSession() {
        Session session1 = sessionManager.getSession(sessionRequest);
        Session session2 = sessionManager.getSession(requestWithoutSession);
        assertNotNull(session1);assertNotNull(session2);
        assertNotEquals(session1, session2);

        PostRequest sessionRequest2 = new PostRequest("POST /student-data HTTP/1.1\r\n" + //
                        "Cookie: " + session1.createSessionCookie().toString(), null, LOCALHOST, true);
        Session session3 = sessionManager.getSession(sessionRequest2);
        assertEquals(session1, session3);
    }

    @Test
    void validateSessionIsNullSafeForUserAgentAndIp() {
        PostRequest initial = new PostRequest("POST /login HTTP/1.1", null, null, true);
        Session session = sessionManager.getSession(initial);
        String cookie = session.createSessionCookie().toString();
        PostRequest same = new PostRequest("POST /dashboard HTTP/1.1\r\nCookie: " + cookie, null, null, true);
        assertEquals(SessionValidationResult.OK, sessionManager.validateSession(same));

        PostRequest changedAgent = new PostRequest("POST /dashboard HTTP/1.1\r\nUser-Agent: test\r\nCookie: " + cookie, null, null, true);
        assertEquals(SessionValidationResult.INVALID_SESSION, sessionManager.validateSession(changedAgent));

        PostRequest changedIp = new PostRequest("POST /dashboard HTTP/1.1\r\nCookie: " + cookie, null, "127.0.0.1", true);
        assertEquals(SessionValidationResult.INVALID_SESSION, sessionManager.validateSession(changedIp));
    }

    @Test
    void invalidateUserSessionsRemovesTargetSessionsAndKeepsAdminAndOtherUsers() throws Exception {
        Session target = session();
        Session secondTarget = session();
        Session other = session();
        Session admin = session();

        sessionManager.addSessionUser(target, "student@example.test");
        sessionUsers().set(secondTarget, new String("student@example.test"));
        sessionManager.addSessionUser(other, "other@example.test");
        sessionManager.addSessionUser(admin, "adminUser");
        lastActivity().set(target, Instant.now());
        lastActivity().set(secondTarget, Instant.now());
        requestCount().set(target, 7);
        requestCount().set(secondTarget, 8);

        sessionManager.invalidateUserSessions(new String("student@example.test"));

        assertNull(sessionManager.getSession(target.getUUID()));
        assertNull(sessionManager.getSession(secondTarget.getUUID()));
        assertNotNull(sessionManager.getSession(other.getUUID()));
        assertNotNull(sessionManager.getSession(admin.getUUID()));
        assertNull(sessionUsers().get(target));
        assertNull(sessionUsers().get(secondTarget));
        assertNull(lastActivity().get(target));
        assertNull(lastActivity().get(secondTarget));
        assertNull(requestCount().get(target));
        assertNull(requestCount().get(secondTarget));
        assertEquals("other@example.test", sessionUsers().get(other));
        assertEquals("adminUser", sessionUsers().get(admin));
    }

    private Session session() {
        return sessionManager.getSession(new PostRequest("POST /login HTTP/1.1\r\nCookie: session=" + UUID.randomUUID(), "", LOCALHOST, true));
    }

    @SuppressWarnings("unchecked")
    private SessionStorage<String> sessionUsers() throws ReflectiveOperationException {
        return (SessionStorage<String>) field("sessionUsers").get(sessionManager);
    }

    @SuppressWarnings("unchecked")
    private SessionStorage<Instant> lastActivity() throws ReflectiveOperationException {
        return (SessionStorage<Instant>) field("lastActivity").get(sessionManager);
    }

    @SuppressWarnings("unchecked")
    private SessionStorage<Integer> requestCount() throws ReflectiveOperationException {
        return (SessionStorage<Integer>) field("requestCount").get(sessionManager);
    }

    private Field field(String name) throws ReflectiveOperationException {
        Field field = SessionManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
