package de.igslandstuhl.database.api;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SubjectTest {
    @BeforeAll
    public static void setupServer() throws SQLException {
        PreConditions.setupDatabase();
        PreConditions.addSampleClass(); // Ensure a class exists for testing
    }
    @Test
    public void addSubject() throws SQLException {
        String name = "Mathematik " + System.nanoTime();
        Subject added = Subject.addSubject(name);
        Subject subject = Subject.get(name);
        assertNotNull(subject);
        assertEquals(added, subject);
    }
    @Test
    public void addSubjectToGrade() throws SQLException {
        String classLabel = "5subject-" + System.nanoTime();
        SchoolClass schoolClass = SchoolClass.addClass(classLabel, 5);
        Subject subject = Subject.addSubject("Mathematik " + classLabel);
        subject.addToGrade(5);
        assertTrue(
            schoolClass.getSubjects().stream().anyMatch(candidate -> candidate.getId() == subject.getId()),
            "Subject not found in SchoolClass"
        );
    }
}
