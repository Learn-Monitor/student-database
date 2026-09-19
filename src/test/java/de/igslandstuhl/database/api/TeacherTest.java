package de.igslandstuhl.database.api;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.server.Server;

public class TeacherTest {
    @BeforeAll
    public static void setupServer() throws SQLException {
        PreConditions.setupDatabase();
        PreConditions.addSampleClass(); // Ensure a class exists for testing
    }

    @Test
    public void registerAndLoadTeacher() throws SQLException {
        Teacher teacher = Teacher.registerTeacher("Erika", "Mustermann", "erika@schule.de", "pw123");
        assertNotNull(teacher);
        assertEquals("Erika", teacher.getFirstName());
        assertEquals("Mustermann", teacher.getLastName());
        assertEquals("erika@schule.de", teacher.getEmail());

        Teacher loaded = Teacher.fromEmail("erika@schule.de");
        assertNotNull(loaded);
        assertEquals(teacher.getId(), loaded.getId());
    }

    @Test
    public void assignClassToTeacher() throws SQLException {
        Teacher teacher = Teacher.registerTeacher("Max", "Lehrer", "max@schule.de", "pw456");
        SchoolClass schoolClass = SchoolClass.get(1);
        teacher.addClass(schoolClass.getId());

        // Reload teacher to ensure class assignment is persisted
        Teacher loaded = Teacher.fromEmail("max@schule.de");
        assertTrue(loaded.getClassIds().contains(schoolClass.getId()));

        // Check if getMyStudents works (should be empty unless students are added)
        List<Student> students = loaded.getMyStudents();
        assertNotNull(students);
    }

    @Test
    public void teacherIdentityUsesOnlyPersistentId() {
        Teacher first = new Teacher(42, "Name", "A", "login-a@example.test", "hash-a");
        Teacher sameIdDifferentProfile = new Teacher(42, "Name", "B", "login-b@example.test", "hash-b");
        Teacher differentId = new Teacher(43, "Name", "A", "login-a@example.test", "hash-a");

        assertEquals(first, sameIdDifferentProfile);
        assertEquals(first.hashCode(), sameIdDifferentProfile.hashCode());
        assertNotEquals(first, differentId);
    }

    @Test
    public void setPasswordEvictsTeacherEmailCache() throws SQLException {
        String login = uniqueLogin("password-cache");
        Teacher teacher = Teacher.registerTeacher("Cache", "Password", login, "old-password");
        Teacher cached = Teacher.fromEmail(login);
        String oldHash = cached.getPasswordHash();

        Teacher updated = cached.setPassword("new-password");
        Teacher loaded = Teacher.fromEmail(login);

        assertNotSame(cached, loaded);
        assertEquals(updated.getPasswordHash(), loaded.getPasswordHash());
        assertNotEquals(oldHash, loaded.getPasswordHash());
        assertEquals(User.passHash("new-password"), loaded.getPasswordHash());
    }

    @Test
    public void updateProfileEvictsTeacherEmailCacheForNames() throws SQLException {
        String login = uniqueLogin("profile-cache");
        Teacher teacher = Teacher.registerTeacher("Old", "Name", login, "password");

        teacher.updateProfile("New", "Teacher", login, "");
        Teacher loaded = Teacher.fromEmail(login);

        assertNotSame(teacher, loaded);
        assertEquals("New", loaded.getFirstName());
        assertEquals("Teacher", loaded.getLastName());
        assertEquals(login, loaded.getEmail());
        assertEquals(teacher.getPasswordHash(), loaded.getPasswordHash());
    }

    @Test
    public void updateProfileEvictsTeacherEmailCacheForPassword() throws SQLException {
        String login = uniqueLogin("profile-password-cache");
        Teacher teacher = Teacher.registerTeacher("Password", "Profile", login, "old-password");
        String oldHash = teacher.getPasswordHash();

        teacher.updateProfile("Password", "Profile", login, "new-password");
        Teacher loaded = Teacher.fromEmail(login);

        assertNotSame(teacher, loaded);
        assertNotEquals(oldHash, loaded.getPasswordHash());
        assertEquals(User.passHash("new-password"), loaded.getPasswordHash());
    }

    @Test
    public void updateProfileRejectsLoginChangeAtomically() throws SQLException {
        String login = uniqueLogin("login-change");
        Teacher teacher = Teacher.registerTeacher("Stable", "Teacher", login, "old-password");
        String oldHash = teacher.getPasswordHash();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> teacher.updateProfile("Changed", "Name", uniqueLogin("other-login"), "new-password"));

        assertEquals("Loginname kann nicht über updateProfile geändert werden.", error.getMessage());
        Teacher loaded = Teacher.fromEmail(login);
        assertEquals("Stable", loaded.getFirstName());
        assertEquals("Teacher", loaded.getLastName());
        assertEquals(login, loaded.getEmail());
        assertEquals(oldHash, loaded.getPasswordHash());
        assertEquals("Stable", scalarTeacherField(login, "first_name"));
        assertEquals("Teacher", scalarTeacherField(login, "last_name"));
        assertEquals(oldHash, scalarTeacherField(login, "password"));
    }

    private static String uniqueLogin(String prefix) {
        return prefix + "-" + System.nanoTime() + "@schule.test";
    }

    private static String scalarTeacherField(String login, String field) throws SQLException {
        try (var statement = Server.getInstance().getConnection().getSQLConnection()
                .prepareStatement("SELECT " + field + " FROM teachers WHERE email = ?")) {
            statement.setString(1, login);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }
}
