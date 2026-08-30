package de.igslandstuhl.database.api;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class UnscheduledTaskTest {
    @BeforeAll
    public static void setupServer() throws SQLException {
        PreConditions.setupDatabase();
        PreConditions.addSampleSubject();
        PreConditions.addSampleSchoolYear();
        PreConditions.addSampleClass();
    }

    @Test
    public void addUnscheduledTask() throws SQLException {
        SchoolClass sc = SchoolClass.get(1);
        Subject subject = Subject.get(1);
        UnscheduledTask added = UnscheduledTask.addUnscheduledTask("ExtraWork", sc, subject, 5);
        assertNotNull(added);
        UnscheduledTask loaded = UnscheduledTask.get(added.getId());
        assertNotNull(loaded);
        assertEquals("ExtraWork", loaded.getName());
        assertEquals(sc, loaded.getSchoolClass());
        assertEquals(subject, loaded.getSubject());
        assertEquals(5, loaded.getMaxTokens());
    }

    @Test
    public void getUnscheduledTasksBySubject() throws SQLException {
        SchoolClass sc = SchoolClass.get(1);
        Subject subject = Subject.get(1);
        UnscheduledTask.addUnscheduledTask("Another", sc, subject, 2);
        List<UnscheduledTask> list = UnscheduledTask.getUnscheduledTasksBySubject(subject);
        assertFalse(list.isEmpty());
        assertTrue(list.stream().anyMatch(t -> t.getName().equalsIgnoreCase("Another")));
    }

    @Test
    public void getUnscheduledTasksByName() throws SQLException {
        SchoolClass sc = SchoolClass.get(1);
        Subject subject = Subject.get(1);
        UnscheduledTask.addUnscheduledTask("UniqueName", sc, subject, 1);
        List<UnscheduledTask> list = UnscheduledTask.getUnscheduledTasksByName("UniqueName");
        assertFalse(list.isEmpty());
        assertEquals("UniqueName", list.get(0).getName());
    }
}
