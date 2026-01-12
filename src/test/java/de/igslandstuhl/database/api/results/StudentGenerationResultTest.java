package de.igslandstuhl.database.api.results;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.api.Student;

public class StudentGenerationResultTest {
    StudentGenerationResult studentGenerationResult;
    final String password = "pass123";

    @BeforeEach
    public void setUp() throws SQLException {
        studentGenerationResult = new StudentGenerationResult(Student.registerStudentWithPassword(0, "student", "studenting", "student@school.com", password, null, null), password);
    }

    @Test
    public void testGetters() {
        assertEquals(0, studentGenerationResult.getId());
        assertEquals("student", studentGenerationResult.getFirstName());
        assertEquals("studenting", studentGenerationResult.getLastName());
        assertEquals("student@school.com", studentGenerationResult.getEmail());
        assertEquals(password, studentGenerationResult.getPassword());

        assertEquals(studentGenerationResult.getEntity(), studentGenerationResult.getStudent());
    }
    @Test
    public void testToCSVRow() {
        String expectedCSV = "0,student,studenting,student@school.com,pass123";
        assertEquals(expectedCSV, studentGenerationResult.toCSVRow());
    }
    
}
