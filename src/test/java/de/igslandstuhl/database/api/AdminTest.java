package de.igslandstuhl.database.api;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

public class AdminTest {
    @BeforeAll
    public static void createDatabase() throws Exception {
        PreConditions.setupDatabase();
    }
    @Test
    public void testCreateAdmin() throws Exception {
        Admin admin = Admin.create("adminUser", "adminPass");
        assert admin != null;
        assert "adminUser".equals(admin.getUsername());
        assert admin.isAdmin();
        assert !admin.isTeacher();
        assert !admin.isStudent();
        Admin fetchedAdmin = Admin.get("adminUser");
        assert fetchedAdmin != null;
        assert "adminUser".equals(fetchedAdmin.getUsername());
        assert fetchedAdmin.isAdmin();
        assert !fetchedAdmin.isTeacher();
        assert !fetchedAdmin.isStudent();
        assert fetchedAdmin.equals(admin);
        assert User.passHash("adminPass").equals(fetchedAdmin.getPasswordHash());
        assert User.passHash("adminPass").equals(admin.getPasswordHash());
        assert admin.equals(fetchedAdmin);
    }
    @Order(2)
    @Test
    public void testToJSON() throws Exception {
        Admin admin = Admin.create("adminUser", "adminPass");
        assertThrows(UnsupportedOperationException.class, admin::toJSON);
    }
    @SuppressWarnings("unlikely-arg-type")
    @Order(3)
    @Test
    public void testEqualsAndHashCode() throws Exception {
        Admin admin1 = Admin.create("adminUser", "adminPass");
        Admin admin2 = Admin.get("adminUser");
        Admin admin2b = Admin.get("adminUser");
        Admin admin3 = Admin.create("anotherAdmin", "anotherPass");
        assert admin1.equals(admin1);
        assert !admin1.equals(null);
        assert !admin1.equals("some string");
        assert admin1.equals(admin2);
        assert admin2.equals(admin2b);
        assert admin1.hashCode() == admin2.hashCode();
        assert !admin1.equals(admin3);
        assert admin1.hashCode() != admin3.hashCode();
    }
    @Order(4)
    @Test
    public void testSetPassword() throws Exception {
        Admin admin = Admin.create("adminUser", "adminPass");
        admin = admin.setPassword("newAdminPass");
        assert User.passHash("newAdminPass").equals(admin.getPasswordHash());
        Admin fetchedAdmin = Admin.get("adminUser");
        assert User.passHash("newAdminPass").equals(fetchedAdmin.getPasswordHash());
    }
    @Order(5)
    @Test
    public void testGetNonExistentAdmin() throws Exception {
        Admin admin = Admin.get("nonExistentAdmin");
        assert admin == null;
    }
    @Order(6)
    @Test
    public void testCreateAdminWithInvalidUsername() {
        assertThrows(IllegalArgumentException.class, () -> {
            Admin.create("", "somePass");
        });
        assertThrows(IllegalArgumentException.class, () -> {
            Admin.create(null, "somePass");
        });
    }
    @Order(7)
    @Test
    public void testCreateAdminWithInvalidPassword() {
        assertThrows(IllegalArgumentException.class, () -> {
            Admin.create("validUser", "");
        });
        assertThrows(IllegalArgumentException.class, () -> {
            Admin.create("validUser", null);
        });
    }
    @Order(8)
    @Test
    public void testSetPasswordWithInvalidPassword() throws Exception {
        Admin admin = Admin.create("adminUser", "adminPass");
        assertThrows(IllegalArgumentException.class, () -> {
            admin.setPassword("");
        });
        assertThrows(IllegalArgumentException.class, () -> {
            admin.setPassword(null);
        });
    }
    @Order(9)
    @Test
    public void testDeleteAdmin() throws Exception {
        Admin admin = Admin.create("adminUser", "adminPass");
        assert admin != null;
        admin.delete();
        Admin fetchedAdmin = Admin.get("adminUser");
        assert fetchedAdmin == null;
    }
}