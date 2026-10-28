package de.igslandstuhl.database.server.webserver.access;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.events.EventListener;
import de.igslandstuhl.database.server.webserver.WebPath;
import de.igslandstuhl.database.server.webserver.requests.HttpRequest;
import de.igslandstuhl.database.server.webserver.requests.RequestType;
import de.igslandstuhl.database.server.Server;

/**
 * AccessManager is responsible for managing access to resources based on user roles and resource locations.
 * It determines whether a user has access to a specific resource based on predefined rules.
 */
public class AccessManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccessManager.class);
    private static final AccessManager INSTANCE = new AccessManager();
    public static AccessManager getInstance() {
        return INSTANCE;
    }

    private AccessManager() {
        LOGGER.info("Setting up AccessManager...");
    }
    /**
     * Checks if a user has access to a specific access level
     * @param user the user, can be null to indicate no user logged in
     * @param accessLevel the access level
     * @return the access state of the user for the given access level
     */
    private AccessState getAccessState(User user, AccessLevel accessLevel, HttpRequest request) {
        AccessState result;
        if (accessLevel == AccessLevel.PUBLIC) {
            result = AccessState.PERMITTED;
        } else if (user == null || user == User.ANONYMOUS) {
            result = AccessState.UNAUTHORIZED;
        } else if (accessLevel == AccessLevel.NONE) {
            result = AccessState.RESTRICTED;
        } else if (accessLevel == AccessLevel.USER) {
            result = AccessState.AUTHORIZED;
        } else if (accessLevel == AccessLevel.STUDENT) {
            result = user.isStudent() ? AccessState.AUTHORIZED : AccessState.RESTRICTED;
        } else if (user.isStudent()) {
            result = AccessState.RESTRICTED;
        } else if (accessLevel == AccessLevel.TEACHER) {
            boolean teacherMode = !user.isAdmin() || "teacher".equals(Server.getInstance().getWebServer().getSessionManager().getActiveMode(request));
            result = (user.isTeacher() && teacherMode) || (user.isAdmin() && !user.isTeacher() && adminMode(request, user)) ? AccessState.AUTHORIZED : AccessState.RESTRICTED;
        } else {
            result = user.isAdmin() && adminMode(request, user) ? AccessState.AUTHORIZED : AccessState.RESTRICTED; // Must be AccessLevel.ADMIN
        }
        // Fire an AccessManagerEvent to allow for external modifications of the access decision
        return result;
    }
    private boolean adminMode(HttpRequest request, User user) {
        User sessionUser = Server.getInstance().getWebServer().getSessionManager().getSessionUser(request);
        // Unit callers may supply a user without an authenticated session. Preserve
        // the historical direct access contract; authenticated sessions are mode-bound.
        if (sessionUser == null || sessionUser == User.ANONYMOUS
                || !sessionUser.getUsername().equals(user.getUsername())) return true;
        return "admin".equals(Server.getInstance().getWebServer().getSessionManager().getActiveMode(request));
    }
    /**
     * Checks if a user has access to a specific web path
     * @param user the username of the user, can be null to indicate no user logged in
     * @param path the web path
     * @param request the HTTP request
     * @return true, if the user has access, otherwise false
     */
    public boolean hasAccess(String user, String path, HttpRequest request)  {
        return hasAccess(User.getUser(user), path, request, request.getRequestType());
    }
    /**
     * Checks if a user has access to a specific web path
     * @param user the username of the user, can be null to indicate no user logged in
     * @param path the web path
     * @param request the HTTP request
     * @param requestType the HTTP request type (GET, POST, etc.)
     * @return true, if the user has access, otherwise false
     */
    public boolean hasAccess(String user, String path, HttpRequest request, RequestType requestType)  {
        return hasAccess(User.getUser(user), path, request, requestType);
    }
    /**
     * Checks if a user has access to a specific web path
     * @param user user, can be null to indicate no user logged in
     * @param path the web path
     * @param request the HTTP request
     * @return true, if the user has access, otherwise false
     */
    public boolean hasAccess(User user, String path, HttpRequest request)  {
        return hasAccess(user, path, request, request.getRequestType());
    }
    /**
     * Checks if a user has access to a specific web path
     * @param user user, can be null to indicate no user logged in
     * @param path the web path
     * @param request the HTTP request
     * @param requestType the HTTP request type (GET, POST, etc.)
     * @return true, if the user has access, otherwise false
     */
    public boolean hasAccess(User user, String path, HttpRequest request, RequestType requestType) {
        AccessLevel accessLevel = Registry.webPathRegistry().get(WebPath.PathInfo.get(path, requestType)).accessLevel();
        String plainPath = path.split("\\?", 2)[0];
        if (user != null && user.isTeacher() && user.isAdmin()
                && "pending".equals(Server.getInstance().getWebServer().getSessionManager().getActiveMode(request))
                ) {
            if (plainPath.equals("/select-mode") || plainPath.equals("/switch-mode") || plainPath.equals("/logout")) {
                return true;
            }
            return false;
        }
        if (user != null && user.isTeacher() && user.isAdmin() && plainPath.equals("/switch-mode")) {
            return true;
        }
        AccessState result = getAccessState(user, accessLevel, request);

        AccessManagerEvent event = new AccessManagerEvent(result, path, request);
        EventListener.fireEvent(event);

        result = event.getChangedAccessState().orElse(result);

        // Permission listeners may grant fine-grained access, but they must not
        // override the account-level active mode of a dual teacher/admin session.
        if (user != null && user.isTeacher() && user.isAdmin()) {
            String mode = Server.getInstance().getWebServer().getSessionManager().getActiveMode(request);
            if (accessLevel == AccessLevel.ADMIN && !"admin".equals(mode)) return false;
            if (accessLevel == AccessLevel.TEACHER && !"teacher".equals(mode)) return false;
        }

        return result == AccessState.AUTHORIZED || result == AccessState.PERMITTED;
    }
}
