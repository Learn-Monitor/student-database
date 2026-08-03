package de.igslandstuhl.database.api;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class IndividualTaskTest {
    @BeforeAll
    public static void setupServer() throws SQLException {
        PreConditions.setupDatabase();
        PreConditions.addSampleSubject();
    }
    @Test
    public void addIndividualTask() throws SQLException {
        Subject subject = Subject.get(1);
        IndividualTask.addIndividualTask("Nansteinaufgabe", subject, 2);
        IndividualTask task = IndividualTask.get(1);
        assertNotNull(task);
        assertEquals("Nansteinaufgabe", task.getName());
        assertEquals(2, task.getTokens());
        assertEquals(subject, task.getSubject());
    }
    @Test
    public void addIndividualTaskToStudent() throws SQLException {
        PreConditions.addSampleIndividualTask();
        PreConditions.addSampleStudent();
        IndividualTask task = IndividualTask.get(1);
        Student student = Student.get(0);
        student.assignCompletedIndividualTask(task);
        assertTrue(student.getCompletedTasks().contains(task));
    }
}
