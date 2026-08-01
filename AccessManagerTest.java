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
import de.igslandstuhl.database.server.webserver.requests.GetRequest;
import de.igslandstuhl.database.server.webserver.requests.HttpRequest;

public class AccessManagerTest {
    private HttpRequest getRequest = new GetRequest("GET /login HTTP/1.1", "127.0.0.1", false);
    private User teacher;
    private User student;
    private User admin;
    private User anonymous;

    // Replace all request parameters currently set to null with the field getRequest. AI!
    // Replace all request parameters currently set to null with the field getRequest.

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
        assertTrue(AccessManager.getInstance().hasAccess(anonymous, "/favicon.ico", getRequest));
        assertTrue(AccessManager.getInstance().hasAccess(student, "/favicon.ico", getRequest));
        assertTrue(AccessManager.getInstance().hasAccess(teacher, "/favicon.ico", getRequest));
        assertTrue(AccessManager.getInstance().hasAccess(admin, "/favicon.ico", getRequest));
    }
    @Test
    public void testLoginAccess() {
        assertTrue(AccessManager.getInstance().hasAccess(anonymous, "/login", getRequest));
        assertTrue(AccessManager.getInstance().hasAccess(student, "/login", getRequest));
        assertTrue(AccessManager.getInstance().hasAccess(teacher, "/login", getRequest));
        assertTrue(AccessManager.getInstance().hasAccess(admin, "/login", getRequest));
    }
    @Test
    public void testDashboardAccess() {
        assertFalse(AccessManager.getInstance().hasAccess(anonymous, "/dashboard", getRequest));
        assertTrue(AccessManager.getInstance().hasAccess(student, "/dashboard", getRequest));
        assertTrue(AccessManager.getInstance().hasAccess(teacher, "/dashboard", getRequest));
        assertTrue(AccessManager.getInstance().hasAccess(admin, "/dashboard", getRequest));
    }
    @Test
    public void testStudentManagementAccess() {
        assertFalse(AccessManager.getInstance().hasAccess(anonymous, "/student", getRequest));
        assertFalse(AccessManager.getInstance().hasAccess(student, "/student", getRequest));
        assertTrue(AccessManager.getInstance().hasAccess(teacher, "/student", getRequest));
        assertTrue(AccessManager.getInstance().hasAccess(admin, "/student", getRequest));
    }
    @Test
    public void testTeacherManagementAccess() {
        assertFalse(AccessManager.getInstance().hasAccess(anonymous, "/teacher", getRequest));
        assertFalse(AccessManager.getInstance().hasAccess(student, "/teacher", getRequest));
        assertFalse(AccessManager.getInstance().hasAccess(teacher, "/teacher", getRequest));
        assertTrue(AccessManager.getInstance().hasAccess(admin, "/teacher", getRequest));
    }
}
