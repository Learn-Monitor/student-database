package de.igslandstuhl.database.server.webserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.events.EventListener;
import de.igslandstuhl.database.events.access.AccessManagerEvent;
import de.igslandstuhl.database.events.access.AccessState;

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
     * @return true, if the user has access, otherwise false
     */
    public boolean hasAccess(User user, AccessLevel accessLevel) {
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
            result = user.isStudent() ? AccessState.AUTHORIZED : AccessState.UNAUTHORIZED;
        } else if (user.isStudent()) {
            result = AccessState.UNAUTHORIZED;
        } else if (accessLevel == AccessLevel.TEACHER) {
            result = AccessState.AUTHORIZED;
        } else {
            result = user.isAdmin() ? AccessState.AUTHORIZED : AccessState.UNAUTHORIZED; // Must be AccessLevel.ADMIN
        }
        // Fire an AccessManagerEvent to allow for external modifications of the access decision
        AccessManagerEvent event = new AccessManagerEvent(result);
        EventListener.fireEvent(event);
        result = event.getChangedAccessState().orElse(result);
        return result == AccessState.AUTHORIZED || result == AccessState.PERMITTED;
    }
    /**
     * Checks if a user has access to a specific web path
     * @param user the username of the user, can be null to indicate no user logged in
     * @param path the web path
     * @return true, if the user has access, otherwise false
     */
    public boolean hasAccess(String user, String path)  {
        return hasAccess(User.getUser(user), path);
    }
    /**
     * Checks if a user has access to a specific web path
     * @param user user, can be null to indicate no user logged in
     * @param path the web path
     * @return true, if the user has access, otherwise false
     */
    public boolean hasAccess(User user, String path) {
        AccessLevel accessLevel = Registry.webPathRegistry().get(path).accessLevel();
        return hasAccess(user, accessLevel);
    }
}
