package de.igslandstuhl.database.api.results;

import org.junit.jupiter.api.*;

import de.igslandstuhl.database.api.Teacher;

import static org.junit.jupiter.api.Assertions.*;

public class TeacherGenerationResultTest {
    TeacherGenerationResult teacherGenerationResult;
    final String password = "pass123";

    @BeforeEach
    public void setUp() {
        teacherGenerationResult = new TeacherGenerationResult(new Teacher(0, "test", "teacher", "test@teach.er", password), password);
    }

    @Test
    public void testGetters() {
        assertEquals(0, teacherGenerationResult.getId());
        assertEquals("test", teacherGenerationResult.getFirstName());
        assertEquals("teacher", teacherGenerationResult.getLastName());
        assertEquals("test@teach.er", teacherGenerationResult.getEmail());
        assertEquals(password, teacherGenerationResult.getPassword());

        assertEquals(teacherGenerationResult.getEntity(), teacherGenerationResult.getTeacher());
    }
    @Test
    public void testToCSVRow() {
        String expectedCSV = "0,test,teacher,test@teach.er,pass123";
        assertEquals(expectedCSV, teacherGenerationResult.toCSVRow());
    }
}