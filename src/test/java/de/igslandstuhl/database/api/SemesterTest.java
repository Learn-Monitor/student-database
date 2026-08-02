package de.igslandstuhl.database.api;

import java.sql.SQLException;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

public class SemesterTest {
    @BeforeAll
    public static void setupDatabase() throws SQLException {
        PreConditions.setupDatabase();
        PreConditions.addSampleSchoolYear();
    }
    @Test
    public void testSemesterCreation() throws SQLException {
        // Test the creation of a Semester object
        SchoolYear schoolYear = SchoolYear.get(1);
        Semester semester = Semester.addSemester("Fall 2023", 1, schoolYear);
        assert semester.getLabel().equals("Fall 2023");
        assert semester.getPosition() == 1;
        assert semester.getSchoolYear().equals(schoolYear);
    }
    @Test
    public void testSemesterDeletion() throws SQLException {
        // Test the deletion of a Semester object
        SchoolYear schoolYear = SchoolYear.get(1);
        Semester semester = Semester.addSemester("Spring 2024", 2, schoolYear);
        int semesterId = semester.getId();
        semester.delete();
        assert Semester.get(semesterId) == null;
    }
    @Test
    public void testNonexistentSemesterRetrieval() throws SQLException {
        // Test retrieving a non-existent Semester object
        assert Semester.get(9999) == null; // Assuming 9999 is an ID that does not exist in the database
    }
    @Test
    public void testCacheBehavior() throws SQLException {
        // Test the caching behavior of the Semester class
        SchoolYear schoolYear = SchoolYear.get(1);
        Semester semester1 = Semester.addSemester("Fall 2023", 1, schoolYear);
        Semester semester2 = Semester.get(semester1.getId());
        assert semester1 == semester2; // They should be the same instance due to caching
    }
    @Test
    public void testGetBySchoolYear() throws SQLException {
        // Test retrieving semesters by school year
        SchoolYear schoolYear = SchoolYear.get(1);
        Semester.addSemester("Fall 2023", 1, schoolYear);
        Semester.addSemester("Spring 2024", 2, schoolYear);
        assert Semester.getBySchoolYear(schoolYear).size() >= 2; // There should be at least 2 semesters in the database for this school year
    }
    @Test
    public void testToJSON() throws SQLException {
        // Test the toJSON method of the Semester class
        SchoolYear schoolYear = SchoolYear.get(1);
        Semester semester = Semester.addSemester("Fall 2023", 1, schoolYear);
        String json = semester.toJSON();
        @SuppressWarnings("unchecked")
        Map<String, Object> jsonMap = new Gson().fromJson(json, Map.class);
        assert ((Number) jsonMap.get("id")).intValue() == semester.getId();
        assert ((Number) jsonMap.get("position")).intValue() == 1;
        assert jsonMap.get("schoolYear") instanceof Map; // The schoolYear should be represented as a nested JSON object
        assert jsonMap.get("label").equals("Fall 2023");

        assert json.equals(semester.toString()); // toJSON and toString should produce the same output
    }
    @Test
    public void testEqualsAndHashCode() throws SQLException {
        // Test the equals and hashCode methods of the Semester class
        SchoolYear schoolYear = SchoolYear.get(1);
        Semester semester1 = Semester.addSemester("Fall 2023", 1, schoolYear);
        Semester semester2 = Semester.get(semester1.getId());
        assert semester1.equals(semester2); // They should be equal
        assert semester1.hashCode() == semester2.hashCode(); // Their hash codes should be the same
        // Note: The semesters should already be the same object due to caching, but this test ensures that equals and hashCode are implemented correctly.
        assert !semester1.equals(null);
        assert !semester1.equals(new Object());
    }
}
