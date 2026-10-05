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

    @Test
    public void studentDisplayNamesKeepTheCanonicalOrderWithoutPrefixes() {
        assertEquals("Deutsch", Subject.displayName("Deutsch"));
        assertEquals("Englisch", Subject.displayName("Englisch"));
        assertEquals("Mathematik", Subject.displayName("Mathematik"));
        assertEquals("Gesellschaftslehre", Subject.displayName("GL"));
        assertEquals("Naturwissenschaften", Subject.displayName("Nawi"));
        assertEquals("Wahlpflichtfach", Subject.displayName("WPF"));
        assertEquals("Religion/Ethik", Subject.displayName("Reli01"));
        assertEquals("Bildende Kunst", Subject.displayName("BK"));
        assertEquals(1, Subject.displayOrder("Deutsch"));
        assertEquals(7, Subject.displayOrder("Religion/Ethik"));
        assertEquals(10, Subject.displayOrder("Bildende Kunst"));
    }
}
