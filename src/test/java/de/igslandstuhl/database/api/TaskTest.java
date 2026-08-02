package de.igslandstuhl.database.api;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TaskTest {
    @BeforeAll
    public static void setupServer() throws SQLException {
        PreConditions.setupDatabase();
        PreConditions.addSampleSubject();
        PreConditions.addSampleSchoolYear();
        PreConditions.addSampleTopic();
    }
    @Test
    public void addTask() throws SQLException {
        Task added = Task.addTask(Topic.get(1), "Addition", TaskLevel.LEVEL1);
        Task task = Task.get(1);
        assertNotNull(task);
        assertEquals(added, task);
    }
}
