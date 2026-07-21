package de.igslandstuhl.database.server.webserver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.server.webserver.access.AccessManager;

public class AccessManagerTest {
    private User teacher;
    private User student;
    private User admin;
    private User anonymous;

    @BeforeAll
    public static void setup() throws IOException {
        WebPath.registerPaths();
    }

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
    public void testIconAccess() {
        assertTrue(AccessManager.getInstance().hasAccess(anonymous, "/favicon.ico", null));
        assertTrue(AccessManager.getInstance().hasAccess(student, "/favicon.ico", null));
        assertTrue(AccessManager.getInstance().hasAccess(teacher, "/favicon.ico", null));
        assertTrue(AccessManager.getInstance().hasAccess(admin, "/favicon.ico", null));
    }
    @Test
    public void testLoginAccess() {
        assertTrue(AccessManager.getInstance().hasAccess(anonymous, "/login", null));
        assertTrue(AccessManager.getInstance().hasAccess(student, "/login", null));
        assertTrue(AccessManager.getInstance().hasAccess(teacher, "/login", null));
        assertTrue(AccessManager.getInstance().hasAccess(admin, "/login", null));
    }
    @Test
    public void testDashboardAccess() {
        assertFalse(AccessManager.getInstance().hasAccess(anonymous, "/dashboard", null));
        assertTrue(AccessManager.getInstance().hasAccess(student, "/dashboard", null));
        assertTrue(AccessManager.getInstance().hasAccess(teacher, "/dashboard", null));
        assertTrue(AccessManager.getInstance().hasAccess(admin, "/dashboard", null));
    }
    @Test
    public void testStudentManagementAccess() {
        assertFalse(AccessManager.getInstance().hasAccess(anonymous, "/student", null));
        assertFalse(AccessManager.getInstance().hasAccess(student, "/student", null));
        assertTrue(AccessManager.getInstance().hasAccess(teacher, "/student", null));
        assertTrue(AccessManager.getInstance().hasAccess(admin, "/student", null));
    }
    @Test
    public void testTeacherManagementAccess() {
        assertFalse(AccessManager.getInstance().hasAccess(anonymous, "/teacher", null));
        assertFalse(AccessManager.getInstance().hasAccess(student, "/teacher", null));
        assertFalse(AccessManager.getInstance().hasAccess(teacher, "/teacher", null));
        assertTrue(AccessManager.getInstance().hasAccess(admin, "/teacher", null));
    }
}
