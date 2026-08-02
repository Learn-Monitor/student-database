package de.igslandstuhl.database.api;

import java.sql.SQLException;
import java.time.LocalDate;

import de.igslandstuhl.database.server.Server;

public class PreConditions {
    public static void setupDatabase() throws SQLException {
        Server server = Server.getInstance();
        server.getConnection().createTables();
    }
    public static void addSampleStudent() throws SQLException {
        Student.registerStudentWithPassword(0, "Max", "Mustermann", "max@muster.mann", "12345", SchoolClass.get(1), GraduationLevel.LEVEL1);
    }
    public static void addSampleSubject() throws SQLException {
        Subject.addSubject("Mathematik");
    }
    public static void addSampleTopic() throws SQLException {
        Topic.addTopic("Bruchrechnung", Subject.get(1), 100, 5, 1, SchoolYear.getCurrentYear().getCurrentSemester());
    }
    public static void addSampleTask() throws SQLException {
        Task.addTask(Topic.get(1), "Addition", TaskLevel.LEVEL1, 3);
    }
    public static void addSampleSpecialTask() throws SQLException {
        Subject subject = Subject.get(1);
        SpecialTask.addSpecialTask("Nansteinaufgabe", subject, 2);
    }
    public static void addSampleClass() throws SQLException {
        SchoolClass.addClass("5a", 5);
    }
    public static void addSampleAdmin() throws SQLException {
        Admin.create("adminUser", "adminPass");
    }
    public static void addSampleSchoolYear() throws SQLException {
        SchoolYear schoolYear = SchoolYear.addSchoolYear("Precondition-Year", 39, 39, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(11));
        Semester semester = Semester.addSemester("Test-Semester", 1, schoolYear);
        schoolYear.setCurrentSemester(semester);
    }
}