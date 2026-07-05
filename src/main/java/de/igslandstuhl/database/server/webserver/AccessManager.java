package de.igslandstuhl.database.server.webserver;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.resources.ResourceLocation;

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
    /**
     * Public spaces and locations that are accessible without authentication.
     * These resources can be accessed by anyone, regardless of their authentication status.
     */
    private final String[] PUBLIC_SPACES;
    /**
     * The user space is restricted to authenticated users.
     */
    private final String USER_SPACE;
    /**
     * The teacher space is restricted to authenticated teachers.
     */
    private final String TEACHER_SPACE;
    /**
     * The admin space is restricted to authenticated admins.
     */
    private final String ADMIN_SPACE;
    /**
     * Public locations that are accessible without authentication.
     * These resources can be accessed by anyone, regardless of their authentication status.
     */
    private final String[] PUBLIC_LOCATIONS;
    /**
    * User locations that are accessible only to authenticated users.
    * These resources require user login for access.
    */
    private final String[] USER_LOCATIONS;
    /**
     * Teacher locations that are accessible only to authenticated teachers.
     * These resources require teacher privileges for access.
     */
    private final String[] TEACHER_LOCATIONS;
    /**
     * Admin locations that are accessible only to authenticated admins.
     * These resources require admin privileges for access.
     */
    private final String[] ADMIN_LOCATIONS;


    @SuppressWarnings("unchecked")
    private AccessManager() {
        LOGGER.info("Setting up AccessManager...");
        ResourceLocation metaLocation = new ResourceLocation("meta", "paths", "spaces.json");
        String userSpace = "user";
        String teacherSpace = "teacher";
        String adminSpace = "admin";

        String[] publicSpaces = {"error", "site", "icons"};
        String[] publicLocations = {"rooms", "subjects"};
        String[] userLocations = {};
        String[] teacherLocations = {};
        String[] adminLocations = {"students", "teachers", "classes"};
        try {
            LOGGER.debug("Trying to read spaces metadata...");
            Map<String, ?> pathData = Server.getInstance().getResourceManager().readJsonResourceAsMap(metaLocation);
            List<String> publicSpacesList = (List<String>) pathData.get("public_spaces");
            List<String> publicLocationsList = (List<String>) pathData.get("public_locations");
            List<String> userLocationsList = (List<String>) pathData.get("user_locations");
            List<String> teacherLocationsList = (List<String>) pathData.get("teacher_locations");
            List<String> adminLocationsList = (List<String>) pathData.get("admin_locations");
            userSpace = (String) pathData.get("user_space");
            teacherSpace = (String) pathData.get("teacher_space");
            adminSpace = (String) pathData.get("admin_space");
            publicSpaces = publicSpacesList.toArray(new String[publicSpacesList.size()]);
            publicLocations = publicLocationsList.toArray(new String[publicLocationsList.size()]);
            userLocations = userLocationsList.toArray(new String[userLocationsList.size()]);
            teacherLocations = teacherLocationsList.toArray(new String[teacherLocationsList.size()]);
            adminLocations = adminLocationsList.toArray(new String[adminLocationsList.size()]);
        } catch (IOException e) {
            LOGGER.error("Could not read spaces metadata!", e);
        } finally {
            USER_SPACE = userSpace;
            TEACHER_SPACE = teacherSpace;
            ADMIN_SPACE = adminSpace;
            PUBLIC_SPACES = publicSpaces;
            PUBLIC_LOCATIONS = publicLocations;
            USER_LOCATIONS = userLocations;
            TEACHER_LOCATIONS = teacherLocations;
            ADMIN_LOCATIONS = adminLocations;
        }


    }
    /**
     * Checks if a user has access to a specific access level
     * @param user the user, can be null to indicate no user logged in
     * @param accessLevel the access level
     * @return true, if the user has access, otherwise false
     */
    public boolean hasAccess(User user, AccessLevel accessLevel) {
        if (accessLevel == AccessLevel.PUBLIC) {
            return true;
        } else if (user == null || user == User.ANONYMOUS) {
            return false;
        } else if (accessLevel == AccessLevel.NONE) {
            return false;
        } else if (accessLevel == AccessLevel.USER) {
            return true;
        } else if (accessLevel == AccessLevel.STUDENT) {
            return user.isStudent();
        } else if (user.isStudent()) {
            return false;
        } else if (accessLevel == AccessLevel.TEACHER) {
            return true;
        } else {
            return user.isAdmin(); // Must be AccessLevel.ADMIN
        }
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
