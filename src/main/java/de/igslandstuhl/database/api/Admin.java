package de.igslandstuhl.database.api;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.igslandstuhl.database.Application;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLHelper;

/**
 * Represents an administrator user in the system.
 */
public class Admin extends User {
    private static final String[] SQL_FIELDS = { "username", "password_hash" };

    private final String username;
    private final String passwordHash;

    private Admin(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    /**
     * Creates a new Admin user with the specified username and password.
     * @param username the username
     * @param password the password
     * @return the newly created Admin object
     * @throws SQLException if there's an underlying sql error during the creation process
     */
    public static Admin create(String username, String password) throws SQLException {
        String passwordHash = passHash(password);
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getAddObjectProcess("admin", username, passwordHash));
        return new Admin(username, passwordHash);
    }

    /**
     * Deletes this Admin user from the database.
     * @throws SQLException if there's an underlying sql error during the deletion process
     */
    public void delete() throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getDeleteObjectProcess("admin", username));
    }

    @Override
    public boolean isTeacher() {
        return false; // Admins are not teachers
    }
    @Override
    public boolean isStudent() {
        return false; // Admins are not students
    }
    @Override
    public boolean isAdmin() {
        return true; // Admins are admins
    }
    @Override
    public String getPasswordHash() {
        return passwordHash;
    }
    @Override
    public String toJSON() {
        throw new UnsupportedOperationException("Admins are not serializable to JSON");
    }
    @Override
    public String getUsername() {
        return username;
    }

    private static Admin fromSQL(String[] fields) {
        if (fields.length != SQL_FIELDS.length) {
            throw new IllegalArgumentException("Invalid number of fields for Admin");
        }
        return new Admin(fields[0], fields[1]);
    }
    /**
     * Retrieves an Admin user from the database by their username.
     * @param username the username of the admin
     * @return the Admin object if found, or null if not found
     */
    public static Admin get(String username) {
        try {
            return Server.getInstance().processSingleRequest(Admin::fromSQL, "get_admin_by_username", SQL_FIELDS, username);
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to retrieve Admin user '{}' from database", username, e);
            return null;
        }
    }
    /**
     * Retrieves all admins from the database.
     * This method queries the database for all admins and returns a list of Admin objects.
     *
     * @return a list of all students
     */
    public static List<Admin> getAll() {
        List<String> adminUsernames = new ArrayList<>();
        try {
            Server.getInstance().processRequest(
                fields -> {
                    adminUsernames.add(fields[0]);
                },
                "get_all_admins", SQL_FIELDS
            );
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to retrieve student list from database", e);
        }
        return adminUsernames.stream()
            .map(Admin::get)
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((username == null) ? 0 : username.hashCode());
        result = prime * result + ((passwordHash == null) ? 0 : passwordHash.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Admin other = (Admin) obj;
        if (username == null) {
            if (other.username != null)
                return false;
        } else if (!username.equals(other.username))
            return false;
        if (passwordHash == null) {
            if (other.passwordHash != null)
                return false;
        } else if (!passwordHash.equals(other.passwordHash))
            return false;
        return true;
    }

    @Override
    public Admin setPassword(String password) throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getUpdateObjectProcess("password_hash_for_admin", passHash(password), getUsername()));
        return get(username);
    }
}
