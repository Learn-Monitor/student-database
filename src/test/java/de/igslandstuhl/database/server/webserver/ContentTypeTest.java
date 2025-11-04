package de.igslandstuhl.database.server.webserver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.server.webserver.handlers.WebResourceHandler;

public class ContentTypeTest {
    private User student;

    @BeforeEach
    public void setupUsers() {
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
    }
    @Test
    void testGetName() {
        assertEquals(ContentType.TEXT_PLAIN.getName(), "text/plain");
        assertEquals(ContentType.HTML.getName(), "text/html");
        assertEquals(ContentType.JAVASCRIPT.getName(), "text/javascript");
        assertEquals(ContentType.CSS.getName(), "text/css");
        assertEquals(ContentType.PNG.getName(), "image/png");
        assertEquals(ContentType.JSON.getName(), "text/json");
    }

    @Test
    void testOfResourceLocation() throws NoWebResourceException {
        ResourceLocation err404 = ResourceLocation.get("html", "error:404");
        assertEquals(ContentType.ofResourceLocation(err404), ContentType.HTML);
        ResourceLocation icon = ResourceLocation.get("imgs", "icons:favicon.ico");
        assertEquals(ContentType.ofResourceLocation(icon), ContentType.PNG);
        ResourceLocation dashboardJs = WebResourceHandler.locationFromPath("/build_dashboard.js", student);
        assertEquals(ContentType.ofResourceLocation(dashboardJs), ContentType.JAVASCRIPT);
        ResourceLocation css = WebResourceHandler.locationFromPath("/style.css", student);
        assertEquals(ContentType.ofResourceLocation(css), ContentType.CSS);
    }
}
