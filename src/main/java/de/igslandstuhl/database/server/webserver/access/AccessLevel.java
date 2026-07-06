package de.igslandstuhl.database.server.webserver.access;

import de.igslandstuhl.database.api.User;

public enum AccessLevel {
    PUBLIC, USER, STUDENT, TEACHER, ADMIN, NONE;

    public boolean hasAccess(User user) {
        if (this == PUBLIC) return true;
        else if (user == null || user == User.ANONYMOUS) return false;
        else if (this == USER || this == STUDENT) return true;
        else if (user.isStudent()) return false;
        else if (this == TEACHER) return true;
        else if (user.isTeacher()) return false;
        else return this == ADMIN;
    }
}
