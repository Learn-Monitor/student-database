package de.igslandstuhl.database.holidays;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class HolidayTest {
    @Test void derivesRhinelandPalatinateSemesterBoundariesFromSummerBreaks() {
        var first=Holiday.deriveSemesterDates(LocalDate.of(2026,8,7),LocalDate.of(2027,6,28),2026,1);
        assertEquals(LocalDate.of(2026,8,10),first.start());
        assertEquals(LocalDate.of(2027,1,29),first.end());
        var second=Holiday.deriveSemesterDates(LocalDate.of(2026,8,7),LocalDate.of(2027,6,28),2026,2);
        assertEquals(LocalDate.of(2027,2,1),second.start());
        assertEquals(LocalDate.of(2027,6,27),second.end());
    }
}
