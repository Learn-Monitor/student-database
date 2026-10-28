package de.igslandstuhl.database.server.webserver.sessions;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.server.webserver.Cookie;
import de.igslandstuhl.database.server.webserver.handlers.SessionValidationResult;
import de.igslandstuhl.database.server.webserver.requests.HttpRequest;

public class SessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionManager.class);
    private final Map<UUID,Session> sessionStore = new HashMap<>();
    /**
     * A map to store session IDs and their associated usernames.
     * This is a simple in-memory session store.
     */
    private SessionStorage<String> sessionUsers = new SessionStorage<>();
    private SessionStorage<String> activeModes = new SessionStorage<>();
    private SessionStorage<Instant> lastActivity = new SessionStorage<>();
    /** Legacy diagnostic counter; it mirrors accepted requests in the current window. */
    private SessionStorage<Integer> requestCount = new SessionStorage<>();
    private final Map<Session,RateWindow> apiRateWindows = new HashMap<>();
    private final Map<Session,RateWindow> loginRateWindows = new HashMap<>();
    private final Object rateLock = new Object();

    /**
     * After this duration, sessions expire (are removed from the session store). It is measured in seconds.
     */
    private final int sessionExpireDuration;
    private final int maximumInactivityDuration;
    private final int anonymousMaxRequests;
    private final int authenticatedMaxRequests;
    private final int loginMaxRequests;
    private final long rateWindowMillis;
    private final LongSupplier clockMillis;

    public static final long DEFAULT_RATE_WINDOW_SECONDS = 60;
    public static final int DEFAULT_LOGIN_MAX_REQUESTS = 10;



    public SessionManager(int sessionExpireDuration, int maximumInactivityDuration, int maxRequests) {
        this(sessionExpireDuration, maximumInactivityDuration, maxRequests, maxRequests);
    }

    public SessionManager(int sessionExpireDuration, int maximumInactivityDuration,
                          int anonymousMaxRequests, int authenticatedMaxRequests) {
        this(sessionExpireDuration, maximumInactivityDuration, anonymousMaxRequests,
                authenticatedMaxRequests, Math.min(anonymousMaxRequests, DEFAULT_LOGIN_MAX_REQUESTS),
                DEFAULT_RATE_WINDOW_SECONDS, System::currentTimeMillis);
    }

    /** Testable constructor; production uses the one-minute fixed window. */
    public SessionManager(int sessionExpireDuration, int maximumInactivityDuration,
                          int anonymousMaxRequests, int authenticatedMaxRequests,
                          int loginMaxRequests, long rateWindowSeconds,
                          LongSupplier clockMillis) {
        this.sessionExpireDuration = sessionExpireDuration;
        this.maximumInactivityDuration = maximumInactivityDuration;
        this.anonymousMaxRequests = anonymousMaxRequests;
        this.authenticatedMaxRequests = authenticatedMaxRequests;
        if (loginMaxRequests < 1 || rateWindowSeconds < 1 || clockMillis == null)
            throw new IllegalArgumentException("Invalid rate-limit configuration");
        this.loginMaxRequests = loginMaxRequests;
        this.rateWindowMillis = Math.multiplyExact(rateWindowSeconds, 1000L);
        this.clockMillis = clockMillis;
        LOGGER.debug("Starting session cleanup job...");
        new Thread(this::cleanSecondsJob, "Session Expiring").start();
    }

    private void removeSession(Session session) {
        UUID uuid = session.getUUID();
        synchronized (sessionStore) {
            sessionStore.remove(uuid);
        }
        synchronized (sessionUsers) {
            sessionUsers.remove(session);
        }
        activeModes.remove(session);
        lastActivity.remove(session);
        requestCount.remove(session);
        synchronized (rateLock) {
            apiRateWindows.remove(session);
            loginRateWindows.remove(session);
        }
    }
    private Instant getOrCreateLastActivity(Session session) {
        Instant instant = lastActivity.get(session);
        return instant == null ? Instant.now() : instant;
    }
    private void cleanSecondsJob() {
        while (true) {
            List<Session> sessions;
            synchronized (sessionStore) { sessions = List.copyOf(sessionStore.values()); }
            sessions.stream()
            .filter((session) -> 
                session.getLoginTime().plusSeconds(sessionExpireDuration).isBefore(Instant.now())
                || getOrCreateLastActivity(session).plusSeconds(maximumInactivityDuration).isBefore(Instant.now())
            )
            .toList().stream() // Convert to list to avoid ConcurrentModificationException
            .forEach(this::removeSession);
            long now=clockMillis.getAsLong();
            synchronized (rateLock) {
                apiRateWindows.entrySet().removeIf(entry -> now-entry.getValue().startedAtMillis() >= rateWindowMillis);
                loginRateWindows.entrySet().removeIf(entry -> now-entry.getValue().startedAtMillis() >= rateWindowMillis);
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                LOGGER.warn("Session manager cleanup job interrupted while sleeping", e);
            }
        }
    }

    public SessionValidationResult validateSession(HttpRequest request) {
        Session session=getSession(request);
        String userAgent = request.getUserAgent();
        if (!Objects.equals(session.getUserAgent(), userAgent)) {
            LOGGER.warn("faked session id (device changed), for user {}" + getSessionUser(request));
            return SessionValidationResult.INVALID_SESSION;
        }
        String ip = request.getIP();
        if (!Objects.equals(session.getIpAddress(), ip)) {
            LOGGER.warn("faked session id (ip address changed) for user {}" + getSessionUser(request));
            return SessionValidationResult.INVALID_SESSION;
        }

        User user = getSessionUser(session);
        lastActivity.set(session, Instant.now());
        boolean loginRequest = "/login".equals(request.getPath().split("\\?", 2)[0]);
        if (!user.isAdmin() || loginRequest) {
            int maxRequests = loginRequest ? loginMaxRequests
                    : (user == User.ANONYMOUS ? anonymousMaxRequests : authenticatedMaxRequests);
            RateDecision decision = consume(session, loginRequest, maxRequests);
            requestCount.set(session, decision.count());
            if (!decision.allowed()) {
                LOGGER.warn("Rate limit for {}: {} requests of maximum {} allowed", user.getUsername(), decision.count(), maxRequests);
                return SessionValidationResult.RATE_LIMITED;
            }
        }

        return SessionValidationResult.OK;
    }

    private RateDecision consume(Session session, boolean login, int limit) {
        long now=clockMillis.getAsLong();
        synchronized (rateLock) {
            Map<Session,RateWindow> windows=login ? loginRateWindows : apiRateWindows;
            RateWindow current=windows.get(session);
            if (current==null || now-current.startedAtMillis() >= rateWindowMillis) {
                current=new RateWindow(now,0); windows.put(session,current);
            }
            if (current.count() >= limit)
                return new RateDecision(false,current.count(),retryAfterSeconds(current,now));
            RateWindow accepted=new RateWindow(current.startedAtMillis(),current.count()+1);
            windows.put(session,accepted);
            return new RateDecision(true,accepted.count(),retryAfterSeconds(accepted,now));
        }
    }

    public int retryAfterSeconds(HttpRequest request) {
        Session session=getSession(request);
        boolean loginRequest="/login".equals(request.getPath().split("\\?", 2)[0]);
        long now=clockMillis.getAsLong();
        synchronized (rateLock) {
            RateWindow window=(loginRequest ? loginRateWindows : apiRateWindows).get(session);
            return window==null ? 1 : retryAfterSeconds(window,now);
        }
    }

    private int retryAfterSeconds(RateWindow window,long now) {
        long remaining=Math.max(1,window.startedAtMillis()+rateWindowMillis-now);
        return (int)Math.min(Integer.MAX_VALUE,(remaining+999)/1000);
    }

    private record RateWindow(long startedAtMillis,int count) {}
    private record RateDecision(boolean allowed,int count,int retryAfterSeconds) {}

    /**
     * Logs out the user that supplied this request. This removes the session.
     * @param request
     */
    public void logout(HttpRequest request) {
        removeSession(getSession(request));
    }

    public void invalidateUserSessions(String username) {
        if (username == null) return;
        List<Session> sessions;
        synchronized (sessionUsers) {
            sessions = sessionUsers.getSessions(username);
        }
        sessions.forEach(this::removeSession);
    }

    public Session getSession(UUID sessionUUID) {
        synchronized (sessionStore) { return sessionStore.get(sessionUUID); }
    }
    public Session getSession(HttpRequest request) {
        // Search for cookie header
        for (Cookie cookie : request.getCookies()) {
            if (cookie.getName().equals("session")) {
                synchronized (sessionStore) {
                    Session session;
                    try {
                        session = sessionStore.get(UUID.fromString(cookie.getValue()));
                    } catch (IllegalArgumentException malformedSessionCookie) {
                        // A stale or malformed cookie must never abort the request thread.
                        continue;
                    }
                    // Check if the session exists
                    if (session != null) {
                        return session;
                    }
                }
            }
        }
        // Session does not exist yet
        synchronized (sessionStore) {
            Session session = new Session(request);
            sessionStore.put(session.getUUID(), session);
            return session;
        }
    }
    public User getSessionUser(Session session) {
        synchronized (sessionUsers) {
            User user = User.getUser(sessionUsers.get(session));
            return user == null ? User.ANONYMOUS : user;
        }
    }

    public User getSessionUser(HttpRequest request) {
        return getSessionUser(getSession(request));
    }
    /**
     * Adds a session to the session store.
     * This method is used to create a new session for a user.
     *
     * @param session The session to be added.
     * @param username  The username associated with the session.
     */
    public void addSessionUser(Session session, String username) {
        // Remove all previous sessions for this user (see #51)
        if (sessionUsers.contains(username)) {
            sessionUsers.remove(username);
        }
        sessionUsers.set(session, username);
        User user = User.getUser(username);
        activeModes.set(session, user != null && user.isTeacher() && user.isAdmin()
                ? "pending" : user != null && user.isTeacher() ? "teacher"
                : user != null && user.isAdmin() ? "admin" : "student");
    }

    /** Returns the effective session mode; dual teacher/admin accounts remain pending until selected. */
    public String getActiveMode(HttpRequest request) {
        Session session = getSession(request);
        String mode = activeModes.get(session);
        User user = getSessionUser(session);
        if (user == null || user == User.ANONYMOUS) return "student";
        boolean teacher = user.isTeacher();
        boolean admin = user.isAdmin();
        if (teacher && admin) {
            if (mode != null) return mode;
            return "pending";
        }
        // A role change can invalidate the mode stored in an existing session.
        // Normalize it immediately instead of leaving the user in an unusable
        // admin/pending state or forcing a new login.
        String expected = teacher ? "teacher" : admin ? "admin" : "student";
        if (!expected.equals(mode)) activeModes.set(session, expected);
        return expected;
    }

    /** Changes mode only for a logged-in account that genuinely has both identities. */
    public boolean setActiveMode(HttpRequest request, String mode) {
        if (!"teacher".equals(mode) && !"admin".equals(mode) && !"student".equals(mode)
                && !"pending".equals(mode)) return false;
        Session session = getSession(request);
        User user = getSessionUser(session);
        if (user == null || user == User.ANONYMOUS) return false;
        if (user.isTeacher() && user.isAdmin()) {
            if ("student".equals(mode)) return false;
            activeModes.set(session, mode);
            return true;
        }
        String expected = user.isAdmin() ? "admin" : user.isTeacher() ? "teacher" : "student";
        if (!expected.equals(mode)) return false;
        activeModes.set(session, mode);
        return true;
    }

    /** Returns a dual-role session to the explicit mode chooser. */
    public boolean resetToModeSelection(HttpRequest request) {
        return setActiveMode(request, "pending");
    }
}
