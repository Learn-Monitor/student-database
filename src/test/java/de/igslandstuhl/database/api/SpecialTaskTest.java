package de.igslandstuhl.database.api;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SpecialTaskTest {
    @BeforeAll
    public static void setupServer() throws SQLException {
        PreConditions.setupDatabase();
        PreConditions.addSampleSubject();
    }
    @Test
    public void addSpecialTask() throws SQLException {
        Subject subject = Subject.get(1);
        SpecialTask.addSpecialTask("Nansteinaufgabe", subject, 2);
        SpecialTask task = SpecialTask.get(1);
        assertNotNull(task);
        assertEquals("Nansteinaufgabe", task.getName());
        assertEquals(2, task.getTokens());
        assertEquals(subject, task.getSubject());
    }
    @Test
    public void addSpecialTaskToStudent() throws SQLException {
        PreConditions.addSampleSpecialTask();
        PreConditions.addSampleStudent();
        SpecialTask task = SpecialTask.get(1);
        Student student = Student.get(0);
        student.assignCompletedSpecialTask(task);
        assertTrue(student.getCompletedTasks().contains(task));
    }
}
