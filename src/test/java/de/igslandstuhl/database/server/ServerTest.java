package de.igslandstuhl.database.server;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.api.GraduationLevel;
import de.igslandstuhl.database.api.SchoolClass;
import de.igslandstuhl.database.api.Student;

public class ServerTest {
    Server server;

    @BeforeEach
    public void setupServer() throws SQLException {
        server = Server.getInstance();
    }

    @Test
    public void testSingletonInstance() {
        Server s2 = Server.getInstance();
        assertSame(server, s2);
    }

    @Test
    public void testDatabaseConnection() throws SQLException {
        assertNotNull(server.getConnection());
        Connection sqlConn = server.getConnection().getSQLConnection();
        assertNotNull(sqlConn);
        assertFalse(sqlConn.isClosed());
    }

    @Test
    public void testCreateTables() throws SQLException {
        // Should not throw
        server.getConnection().createTables();
    }

    @Test
    public void testValidUser() throws SQLException {
        server.getConnection().createTables(); // Ensure tables are created before testing user validation
        SchoolClass.addClass("5a", 5);
        Student.registerStudentWithPassword(0, "Max", "Mustermann", "max@muster.mann", "12345", SchoolClass.get(1), GraduationLevel.LEVEL1);
        assertFalse(server.isValidUser("anamethatnostudentwillevergetbecauseitistoolongtoputintotheloginfield", "password"));
        assertFalse(server.isValidUser("", "password"));
        assertFalse(server.isValidUser("   ", "password"));
        assertFalse(server.isValidUser(null, "password"));
        assertFalse(server.isValidUser("max@muster.mann", null));
        assertFalse(server.isValidUser("max@muster.mann", "123456")); // Test student set up by StudentTest
        assertTrue(server.isValidUser("max@muster.mann", "12345"));
    }
}
