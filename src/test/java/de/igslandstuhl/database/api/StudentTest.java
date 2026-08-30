package de.igslandstuhl.database.api;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class StudentTest {
    @BeforeAll
    public static void setupServer() throws SQLException {
        PreConditions.setupDatabase();
        PreConditions.addSampleClass();
    }
    @Test
    public void testPuttingStudent() throws SQLException {
        Student added = Student.registerStudentWithPassword(0, "Max", "Mustermann", "max@muster.mann", "12345", SchoolClass.get(1), GraduationLevel.LEVEL1);
        Student student = Student.get(0);
        assertEquals(added, student);
    }
    @Test
    public void testAssignTopicToStudent() throws SQLException {
        PreConditions.addSampleStudent();
        PreConditions.addSampleSchoolYear();
        PreConditions.addSampleSubject();
        PreConditions.addSampleTopic();
        Student student = Student.get(0);
        Topic topic = Topic.get(1);
        assertNotNull(topic);
        student.assignTopic(topic);
        assertEquals(topic, student.getCurrentTopic(topic.getSubject()));
    }
}