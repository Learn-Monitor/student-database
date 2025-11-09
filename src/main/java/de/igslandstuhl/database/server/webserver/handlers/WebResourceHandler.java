package de.igslandstuhl.database.server.webserver.handlers;

import java.util.Arrays;

import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.server.resources.ResourceLocation;

/**
 * Handles the mapping of web resource paths to resource locations.
 * This class provides methods to determine if a path is a SQL web resource,
 * and to convert paths into ResourceLocation objects.
 */
public final class WebResourceHandler {
    private static final String[] SQL_WEB_RESOURCES = {"/mydata", "/rooms", "/mysubjects", "/myclasses", "/teachers", "/students", "/subjects", "/classes"};
    private static String[] userOnlySpace = {"dashboard", "build_dashboard.js", "results", "build_results.js", "partner_search", "build_partner_search.js"};
    private static String[] teacherOnlySpace = {"dashboard", "build_dashboard.js", "student", "build_student.js", "student-results", "build_results.js"};
    private static String[] adminOnlySpace = {"dashboard", "modules", "build_modules.js", "build_dashboard.js", "manage_students", "manage_teachers", "manage_classes", "manage_subjects", "manage_rooms", "student", "student-results", "teacher", "build_teacher.js", "room", "build_room.js", "subject", "build_subject.js", "class", "build_class.js", "teacher-classes", "teacher-subjects", "teacher-students", "teacher-results", "teacher-dashboard", "build_teacher_dashboard.js"};

    private WebResourceHandler(){}

    private static boolean isSQLWebResource(String path) {
        return Arrays.asList(SQL_WEB_RESOURCES).contains(path);
    }

    private static boolean inUserOnlySpace(String path) {
        for (String userOnlyPaths : userOnlySpace) {
            if (path.replaceFirst("/", "").replace(".html", "").equals(userOnlyPaths)) {
                return true;
            }
        }
        return false;
    }
    private static boolean inTeacherOnlySpace(String path) {
        for (String teacherOnlyPaths : teacherOnlySpace) {
            if (path.replaceFirst("/", "").replace(".html", "").equals(teacherOnlyPaths)) {
                return true;
            }
        }
        return false;
    }
    private static boolean inAdminOnlySpace(String path) {
        for (String adminOnlyPaths : adminOnlySpace) {
            if (path.replaceFirst("/", "").replace(".html", "").equals(adminOnlyPaths)) {
                return true;
            }
        }
        return false;
    }

    public static ResourceLocation locationFromPath(String path, User user) {
        if (isSQLWebResource(path)) {
            return new ResourceLocation("virtual", "sql", path.replaceFirst("/", ""));
        }
        if (path.equals("/")) {
            path = "/index.html";
        } else if (path.endsWith(".ico")) {
            path = "/icons" + path;
        }
        String[] parts = path.split("\\.");
        
        String context;
        if (path.endsWith(".ico") || path.endsWith(".png")) {
            context = "imgs";
        } else if (parts.length > 1) {
            context = parts[1];
        } else {
            context = "html";
            path += ".html";
        }

        if (user == null) user = User.ANONYMOUS;

        parts = path.split("/", 3);
        String namespace;
        String resource;
        if (parts.length > 2) {
            namespace = parts[1];
            resource = parts[2];
        } else {
            if (inAdminOnlySpace(parts[1]) && (user == User.ANONYMOUS || user.isAdmin())) {
                namespace = "admin";
            } else if (inTeacherOnlySpace(parts[1]) && (user == User.ANONYMOUS || user.isTeacher() || user.isAdmin())) {
                namespace = "teacher";
            } else if (inUserOnlySpace(parts[1])) {
                namespace = "user";
            } else if (inAdminOnlySpace(parts[1])) {
                namespace = "admin";
            } else if (inTeacherOnlySpace(parts[1])) {
                namespace = "teacher";
            } else {
                namespace = "site";
            }
            resource = parts[1];
        }

        return new ResourceLocation(context, namespace, resource);
    }
}
