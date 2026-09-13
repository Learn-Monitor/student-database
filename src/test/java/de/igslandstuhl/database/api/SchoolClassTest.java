package de.igslandstuhl.database.api;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SchoolClassTest {
    @BeforeAll
    public static void setupServer() throws SQLException {
        PreConditions.setupDatabase();
    }
    @Test
    public void addClass() throws SQLException {
        String label = "5a-" + System.nanoTime();
        SchoolClass added = SchoolClass.addClass(label, 5);
        SchoolClass schoolClass = SchoolClass.get(label);
        assertNotNull(schoolClass);
        assertEquals(added, schoolClass);
    }
}
