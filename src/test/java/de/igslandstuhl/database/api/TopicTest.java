package de.igslandstuhl.database.api;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TopicTest {
    @BeforeAll
    public static void setupServer() throws SQLException {
        PreConditions.setupDatabase();
        PreConditions.addSampleSubject();
        PreConditions.addSampleSchoolYear();
    }
    @Test
    public void addTopic() throws SQLException {
        Topic added = Topic.addTopic("Bruchrechnung", Subject.get(1), 5, 1, SchoolYear.getCurrentYear().getCurrentSemester());
        Topic topic = Topic.get(1);
        assertNotNull(topic);
        assertEquals(added, topic);
    }
}
