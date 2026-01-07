package de.igslandstuhl.database.server.webserver.handlers;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.server.resources.ResourceLocation;

/**
 * Handles the mapping of web resource paths to resource locations.
 * This class provides methods to determine if a path is a SQL web resource,
 * and to convert paths into ResourceLocation objects.
 */
public final class WebResourceHandler {
    private WebResourceHandler(){}

    private static boolean isSQLWebResource(String path) {
        return Registry.webPathRegistry().get(path) != null &&
               Registry.webPathRegistry().get(path).namespaces().contains("sql");
    }

    private static boolean inUserOnlySpace(String path) {
        return Registry.webPathRegistry().get(path) != null &&
               Registry.webPathRegistry().get(path).namespaces().contains("user");
    }
    private static boolean inTeacherOnlySpace(String path) {
        return Registry.webPathRegistry().get(path) != null &&
               Registry.webPathRegistry().get(path).namespaces().contains("teacher");
    }
    private static boolean inAdminOnlySpace(String path) {
        return Registry.webPathRegistry().get(path) != null &&
               Registry.webPathRegistry().get(path).namespaces().contains("admin");
    }
    private static String getDefaultNamespace(String path) {
        return Registry.webPathRegistry().get(path) != null ? Registry.webPathRegistry().get(path).namespaces().get(0) : "site";
    }

    public static ResourceLocation locationFromPath(String path, User user) {
        if (path.isEmpty()) path = "/";
        if (path == null || Registry.webPathRegistry().get(path) == null) {
            return new ResourceLocation("site", "errors", "404.html");
        }
        if (isSQLWebResource(path)) {
            return new ResourceLocation("virtual", "sql", path.replaceFirst("/", ""));
        }
        
        String context = Registry.webPathRegistry().get(path).context();

        if (user == null) user = User.ANONYMOUS;

        String namespace;        
        if (inAdminOnlySpace(path) && (user == User.ANONYMOUS || user.isAdmin())) {
            namespace = "admin";
        } else if (inTeacherOnlySpace(path) && (user == User.ANONYMOUS || user.isTeacher() || user.isAdmin())) {
            namespace = "teacher";
        } else if (inUserOnlySpace(path)) {
            namespace = "user";
        } else if (inAdminOnlySpace(path)) {
            namespace = "admin";
        } else if (inTeacherOnlySpace(path)) {
            namespace = "teacher";
        } else {
            namespace = getDefaultNamespace(path);
        }
        String resource = path.replaceFirst("/", "");
        if (resource.isBlank()) {
            resource = "index.html";
        } else if (!resource.contains(".")) {
            resource += ".html";
        }

        return new ResourceLocation(context, namespace, resource);
    }
}
