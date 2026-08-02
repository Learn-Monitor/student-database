package de.igslandstuhl.database.api;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SchoolYearTest {

    @BeforeAll
    public static void setupDatabase() throws SQLException {
        PreConditions.setupDatabase();
    }

    @Test
    public void addAndGetSchoolYear() throws SQLException {
        SchoolYear year = SchoolYear.addSchoolYear("2024/2025", 40, 1);
        assertNotNull(year);
        assertEquals("2024/2025", year.getLabel());
        assertEquals(40, year.getWeekCount());
        assertEquals(1, year.getCurrentWeek());

        SchoolYear loaded = SchoolYear.get(year.getId());
        assertNotNull(loaded);
        assertEquals(year, loaded);
    }

    @Test
    public void getAllSchoolYears() throws SQLException {
        SchoolYear added1 = SchoolYear.addSchoolYear("2023/2024", 39, 39);
        SchoolYear added2 = SchoolYear.addSchoolYear("2024/2025", 40, 1);
        List<SchoolYear> years = SchoolYear.getAll();
        assertTrue(years.size() >= 2);
        assertTrue(years.contains(added1));
        assertTrue(years.contains(added2));
    }

    @Test
    public void updateCurrentWeek() throws SQLException {
        SchoolYear year = SchoolYear.addSchoolYear("2025/2026", 41, 1);
        year.setCurrentWeek(10);
        SchoolYear loaded = SchoolYear.get(year.getId());
        assertEquals(10, loaded.getCurrentWeek());
    }

    @Test
    public void getCurrentYear() throws SQLException {
        SchoolYear.addSchoolYear("2022/2023", 38, 38);
        SchoolYear.addSchoolYear("2023/2024", 39, 39);
        SchoolYear.addSchoolYear("2024/2025", 40, 10);
        SchoolYear current = SchoolYear.getCurrentYear();
        assertNotNull(current);
        // Should be the year with the lowest current_week
        assertEquals(10, current.getCurrentWeek());
    }

    @Test
    public void addSchoolYearWithDates() throws SQLException {
        LocalDate start = LocalDate.of(2005, 8, 1);
        LocalDate end = LocalDate.of(2006, 7, 15);
        SchoolYear year = SchoolYear.addSchoolYear("0001/0002-dates", 40, 1, start, end);
        assertNotNull(year);
        assertEquals(start, year.getStartDate());
        assertEquals(end, year.getEndDate());

        SchoolYear loaded = SchoolYear.get(year.getId());
        assertEquals(start, loaded.getStartDate());
        assertEquals(end, loaded.getEndDate());
    }

    @Test
    public void schoolYearWithoutDatesHasNullDates() throws SQLException {
        SchoolYear year = SchoolYear.addSchoolYear("0001/0002-nulldates", 40, 1);
        assertNull(year.getStartDate());
        assertNull(year.getEndDate());
    }
}