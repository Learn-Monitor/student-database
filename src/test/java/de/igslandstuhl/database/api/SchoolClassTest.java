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
        SchoolClass added = SchoolClass.addClass("5a", 5);
        SchoolClass schoolClass = SchoolClass.get(1);
        assertNotNull(schoolClass);
        assertEquals(added, schoolClass);
    }
    @Test
    public void testGettersAndSetters() throws SQLException {
        SchoolClass schoolClass = SchoolClass.addClass("5b", 5);
        assertEquals("5b", schoolClass.getLabel());
        assertEquals(5, schoolClass.getGrade());
        schoolClass = schoolClass.setLabel("5c");
        schoolClass = schoolClass.setGrade(6);
        assertEquals("5c", schoolClass.getLabel());
        assertEquals(6, schoolClass.getGrade());

        assertThrows(IllegalStateException.class, () -> SchoolClass.get("5c").setGrade(-1));
        
    }
}