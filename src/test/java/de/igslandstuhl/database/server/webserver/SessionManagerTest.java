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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.api.PreConditions;
import de.igslandstuhl.database.api.Admin;
import de.igslandstuhl.database.api.Teacher;
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
    void dualTeacherAdminSessionStartsInTeacherModeAndCanSwitchExplicitly() throws Exception {
        PreConditions.setupDatabase();
        String login = "dual-mode-" + UUID.randomUUID() + "@example.test";
        Teacher teacher = Teacher.registerTeacher("Dual", "Mode", login, "password");
        Admin.createWithPasswordHash(login, teacher.getPasswordHash());

        SessionManager modes = new SessionManager(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        PostRequest initial = new PostRequest("POST /login HTTP/1.1", null, LOCALHOST, true);
        Session session = modes.getSession(initial);
        modes.addSessionUser(session, login);
        String cookie = session.createSessionCookie().toString();
        PostRequest authenticated = new PostRequest("POST /dashboard HTTP/1.1\r\nCookie: " + cookie, null, LOCALHOST, true);

        assertEquals("pending", modes.getActiveMode(authenticated));
        assertTrue(modes.setActiveMode(authenticated, "teacher"));
        assertTrue(modes.setActiveMode(authenticated, "admin"));
        assertEquals("admin", modes.getActiveMode(authenticated));
        assertTrue(modes.setActiveMode(authenticated, "teacher"));
        assertEquals("teacher", modes.getActiveMode(authenticated));
        assertTrue(!modes.setActiveMode(authenticated, "student"));
    }

    @Test
    void removingAdminRoleNormalizesExistingAdminModeToTeacher() throws Exception {
        PreConditions.setupDatabase();
        String login = "role-removal-" + UUID.randomUUID() + "@example.test";
        Teacher teacher = Teacher.registerTeacher("Role", "Removal", login, "password");
        Admin admin = Admin.createWithPasswordHash(login, teacher.getPasswordHash());
        SessionManager modes = new SessionManager(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        PostRequest initial = new PostRequest("POST /login HTTP/1.1", null, LOCALHOST, true);
        Session session = modes.getSession(initial);
        modes.addSessionUser(session, login);
        String cookie = session.createSessionCookie().toString();
        PostRequest authenticated = new PostRequest("GET /dashboard HTTP/1.1\r\nCookie: " + cookie, null, LOCALHOST, true);

        assertTrue(modes.setActiveMode(authenticated, "admin"));
        assertEquals("admin", modes.getActiveMode(authenticated));
        admin.delete();
        assertEquals("teacher", modes.getActiveMode(authenticated));
    }

    @Test
    void malformedSessionCookieDoesNotThrowAndDualModeStartsPending() throws Exception {
        PreConditions.setupDatabase();
        String login = "pending-mode-" + UUID.randomUUID() + "@example.test";
        Teacher teacher = Teacher.registerTeacher("Pending", "Mode", login, "password");
        Admin.createWithPasswordHash(login, teacher.getPasswordHash());
        SessionManager modes = new SessionManager(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        PostRequest initial = new PostRequest("POST /login HTTP/1.1", null, LOCALHOST, true);
        Session session = modes.getSession(initial);
        modes.addSessionUser(session, login);
        String cookie = session.createSessionCookie().toString();
        PostRequest authenticated = new PostRequest("GET /select-mode HTTP/1.1\r\nCookie: " + cookie, null, LOCALHOST, true);
        assertEquals("pending", modes.getActiveMode(authenticated));
        assertTrue(modes.setActiveMode(authenticated, "teacher"));
        PostRequest malformed = new PostRequest("GET /dashboard HTTP/1.1\r\nCookie: session=not-a-uuid", null, LOCALHOST, true);
        assertNotNull(modes.getSession(malformed));
        assertEquals("student", modes.getActiveMode(malformed));
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
    void anonymousAndAuthenticatedSessionsUseSeparateLimitsAnd429KeepsSession() throws Exception {
        PreConditions.setupDatabase();
        String login = "rate-limit-" + UUID.randomUUID() + "@example.test";
        Teacher teacher = Teacher.registerTeacher("Rate", "Limited", login, "password");
        SessionManager limited = new SessionManager(Integer.MAX_VALUE, Integer.MAX_VALUE, 2, 3);

        PostRequest anonymous = new PostRequest("POST /login HTTP/1.1", null, LOCALHOST, true);
        Session anonymousSession = limited.getSession(anonymous);
        String anonymousCookie = anonymousSession.createSessionCookie().toString();
        PostRequest anonymousRequest = new PostRequest("POST /login HTTP/1.1\r\nCookie: " + anonymousCookie, null, LOCALHOST, true);
        assertEquals(SessionValidationResult.OK, limited.validateSession(anonymousRequest));
        assertEquals(SessionValidationResult.OK, limited.validateSession(anonymousRequest));
        assertEquals(SessionValidationResult.RATE_LIMITED, limited.validateSession(anonymousRequest));

        PostRequest authenticated = new PostRequest("POST /login HTTP/1.1", null, LOCALHOST, true);
        Session authenticatedSession = limited.getSession(authenticated);
        limited.addSessionUser(authenticatedSession, teacher.getUsername());
        String authenticatedCookie = authenticatedSession.createSessionCookie().toString();
        PostRequest authenticatedRequest = new PostRequest("POST /cockpit HTTP/1.1\r\nCookie: " + authenticatedCookie, null, LOCALHOST, true);
        assertEquals(SessionValidationResult.OK, limited.validateSession(authenticatedRequest));
        assertEquals(SessionValidationResult.OK, limited.validateSession(authenticatedRequest));
        assertEquals(SessionValidationResult.OK, limited.validateSession(authenticatedRequest));
        assertEquals(SessionValidationResult.RATE_LIMITED, limited.validateSession(authenticatedRequest));
        assertEquals(teacher.getUsername(), limited.getSessionUser(authenticatedRequest).getUsername());
    }

    @Test
    void fixedWindowDoesNotCountRejectedRequestsAndExpiresDeterministically() {
        AtomicLong now=new AtomicLong(1_000_000L);
        SessionManager limited=new SessionManager(Integer.MAX_VALUE,Integer.MAX_VALUE,2,2,2,60,now::get);
        PostRequest request=new PostRequest("GET /dashboard HTTP/1.1",null,LOCALHOST,true);
        String cookie=limited.getSession(request).createSessionCookie().toString();
        PostRequest withCookie=new PostRequest("GET /dashboard HTTP/1.1\r\nCookie: "+cookie,null,LOCALHOST,true);
        assertEquals(SessionValidationResult.OK,limited.validateSession(withCookie));
        assertEquals(SessionValidationResult.OK,limited.validateSession(withCookie));
        assertEquals(SessionValidationResult.RATE_LIMITED,limited.validateSession(withCookie));
        assertEquals(60,limited.retryAfterSeconds(withCookie));
        assertEquals(SessionValidationResult.RATE_LIMITED,limited.validateSession(withCookie));
        now.addAndGet(59_000L); assertEquals(1,limited.retryAfterSeconds(withCookie));
        now.addAndGet(1_000L); assertEquals(SessionValidationResult.OK,limited.validateSession(withCookie));
    }

    @Test
    void concurrentRequestsConsumeOnlyAvailableSlotsPerSession() throws Exception {
        SessionManager limited=new SessionManager(Integer.MAX_VALUE,Integer.MAX_VALUE,3,3,3,60,() -> 2_000_000L);
        PostRequest seed=new PostRequest("GET /dashboard HTTP/1.1",null,LOCALHOST,true);
        String cookie=limited.getSession(seed).createSessionCookie().toString();
        PostRequest request=new PostRequest("GET /dashboard HTTP/1.1\r\nCookie: "+cookie,null,LOCALHOST,true);
        int workers=20; CountDownLatch start=new CountDownLatch(1); CountDownLatch done=new CountDownLatch(workers); AtomicLong accepted=new AtomicLong();
        for(int i=0;i<workers;i++) new Thread(() -> { try { start.await(); if(limited.validateSession(request)==SessionValidationResult.OK) accepted.incrementAndGet(); } catch(Exception e) { throw new AssertionError(e); } finally { done.countDown(); } }).start();
        start.countDown(); done.await(); assertEquals(3,accepted.get());
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
