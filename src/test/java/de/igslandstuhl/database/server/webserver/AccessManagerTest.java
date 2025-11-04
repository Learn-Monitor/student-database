package de.igslandstuhl.database.server.webserver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.server.webserver.handlers.WebResourceHandler;

public class AccessManagerTest {
    private User teacher;
    private User student;
    private User admin;
    private User anonymous;

    @BeforeEach
    public void setupUsers() {
        admin = new User() {
            @Override
            public boolean isTeacher() {
                return false;
            }
            @Override
            public boolean isStudent() {
                return false;
            }
            @Override
            public boolean isAdmin() {
                return true;
            }
            @Override
            public String getPasswordHash() {
                throw new IllegalStateException("Access manager should not query admin password");
            }
            @Override
            public String toJSON() {
                throw new IllegalStateException("Access manager should not query admin json");
            }
            @Override
            public User setPassword(String password) throws SQLException {
                throw new IllegalStateException("Access manager should not change admin password");
            }
            @Override
            public String getUsername() {
                return "example@admin.de";
            }
        };
        teacher = new User() {
            @Override
            public boolean isTeacher() {
                return true;
            }
            @Override
            public boolean isStudent() {
                return false;
            }
            @Override
            public boolean isAdmin() {
                return false;
            }
            @Override
            public String getPasswordHash() {
                throw new IllegalStateException("Access manager should not query teacher password");
            }
            @Override
            public String toJSON() {
                throw new IllegalStateException("Access manager should not query teacher json");
            }
            @Override
            public User setPassword(String password) throws SQLException {
                throw new IllegalStateException("Access manager should not change teacher password");
            }
            @Override
            public String getUsername() {
                return "example@teacher.de";
            }
        };
        student = new User() {
            @Override
            public boolean isTeacher() {
                return false;
            }
            @Override
            public boolean isStudent() {
                return true;
            }
            @Override
            public boolean isAdmin() {
                return false;
            }
            @Override
            public String getPasswordHash() {
                throw new IllegalStateException("Access manager should not query student password");
            }
            @Override
            public String toJSON() {
                throw new IllegalStateException("Access manager should not query student json");
            }
            @Override
            public User setPassword(String password) throws SQLException {
                throw new IllegalStateException("Access manager should not change student password");
            }
            @Override
            public String getUsername() {
                return "example@student.de";
            }
        };
        anonymous = User.ANONYMOUS;
    }
    @Test
    public void testErrorMessageAccess() {
        assertTrue(AccessManager.hasAccess(anonymous, ResourceLocation.get("html", "error:404")));
        assertTrue(AccessManager.hasAccess(student, ResourceLocation.get("html", "error:404")));
        assertTrue(AccessManager.hasAccess(teacher, ResourceLocation.get("html", "error:404")));
        assertTrue(AccessManager.hasAccess(admin, ResourceLocation.get("html", "error:404")));
    }
    @Test
    public void testIconAccess() {
        assertTrue(AccessManager.hasAccess(anonymous, ResourceLocation.get("imgs", "icons:favicon.ico")));
        assertTrue(AccessManager.hasAccess(student, ResourceLocation.get("imgs", "icons:favicon.ico")));
        assertTrue(AccessManager.hasAccess(teacher, ResourceLocation.get("imgs", "icons:favicon.ico")));
        assertTrue(AccessManager.hasAccess(admin, ResourceLocation.get("imgs", "icons:favicon.ico")));
    }
    @Test
    public void testLoginAccess() {
        assertTrue(AccessManager.hasAccess(anonymous, ResourceLocation.get("html", "site:login")));
        assertTrue(AccessManager.hasAccess(student, ResourceLocation.get("html", "site:login")));
        assertTrue(AccessManager.hasAccess(teacher, ResourceLocation.get("html", "site:login")));
        assertTrue(AccessManager.hasAccess(admin, ResourceLocation.get("html", "site:login")));
    }
    @Test
    public void testDashboardAccess() {
        assertFalse(AccessManager.hasAccess(anonymous, WebResourceHandler.locationFromPath("/dashboard", anonymous)));
        assertTrue(AccessManager.hasAccess(student, WebResourceHandler.locationFromPath("/dashboard", student)));
        assertTrue(AccessManager.hasAccess(teacher, WebResourceHandler.locationFromPath("/dashboard", teacher)));
        assertTrue(AccessManager.hasAccess(admin, WebResourceHandler.locationFromPath("/dashboard", admin)));
    }
    @Test
    public void testStudentManagementAccess() {
        assertFalse(AccessManager.hasAccess(anonymous, WebResourceHandler.locationFromPath("/student", anonymous)));
        assertFalse(AccessManager.hasAccess(student, WebResourceHandler.locationFromPath("/student", student)));
        assertTrue(AccessManager.hasAccess(teacher, WebResourceHandler.locationFromPath("/student", teacher)));
        assertTrue(AccessManager.hasAccess(admin, WebResourceHandler.locationFromPath("/student", admin)));
    }
    @Test
    public void testTeacherManagementAccess() {
        assertFalse(AccessManager.hasAccess(anonymous, WebResourceHandler.locationFromPath("/teacher", anonymous)));
        assertFalse(AccessManager.hasAccess(student, WebResourceHandler.locationFromPath("/teacher", student)));
        assertFalse(AccessManager.hasAccess(teacher, WebResourceHandler.locationFromPath("/teacher", teacher)));
        assertTrue(AccessManager.hasAccess(admin, WebResourceHandler.locationFromPath("/teacher", admin)));
    }
}
