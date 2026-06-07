package de.igslandstuhl.database.api;

import java.sql.SQLException;

import de.igslandstuhl.database.Application;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLHelper;

public class Admin extends User {
    private static final String[] SQL_FIELDS = { "username", "password_hash" };

    private final String username;
    private final String passwordHash;

    private Admin(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public static Admin create(String username, String password) throws SQLException {
        String passwordHash = passHash(password);
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getAddObjectProcess("admin", username, passwordHash));
        return new Admin(username, passwordHash);
    }

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
    public static Admin get(String username) {
        try {
            return Server.getInstance().processSingleRequest(Admin::fromSQL, "get_admin_by_username", SQL_FIELDS, username);
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to retrieve Admin user '{}' from database", username, e);
            return null;
        }
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
