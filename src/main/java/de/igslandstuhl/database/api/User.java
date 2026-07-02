package de.igslandstuhl.database.api;

import java.sql.SQLException;
import java.util.Random;

import org.apache.commons.codec.digest.DigestUtils;

import de.igslandstuhl.database.utils.CommonUtils;

/**
 * Abstract class representing a user in the system.
 * This class provides methods to check user roles, retrieve password hashes, and convert user data to JSON format.
 */
public abstract class User implements APIObject {
    public static final User ANONYMOUS = new User() {
        @Override
        public boolean isTeacher() {
            return false;
        }
        @Override
        public boolean isStudent() {
            return false;
        }
        @Override
        public boolean isAdmin() {
            return false;
        }
        @Override
        public String getPasswordHash() {
            throw new UnsupportedOperationException("Anonymous user does not have password");
        }
        @Override
        public String toJSON() {
            return "{}";
        }
        @Override
        public User setPassword(String password) {
            throw new UnsupportedOperationException("Anonymous user does not have password");
        }
        @Override
        public String getUsername() {
            return "ANONYMOUS";
        }
    };
    /**
     * Checks if the user is a teacher.
     * This method should be implemented by subclasses to determine if the user is a teacher.
     * @return true if the user is a teacher, false otherwise
     */
    public abstract boolean isTeacher();

    /**
     * Checks if the user is a student.
     * This method should be implemented by subclasses to determine if the user is a student.
     * @return true if the user is a student, false otherwise
     */
    public abstract boolean isStudent();

    /**
     * Checks if the user is an admin.
     * This method should be implemented by subclasses to determine if the user is an admin.
     * @return true if the user is an admin, false otherwise
     */
    public abstract boolean isAdmin();

    /**
     * Returns the password hash of the user.
     * This method should be implemented by subclasses to provide the user's password hash.
     * @return the password hash of the user
     */
    public abstract String getPasswordHash();
    /**
     * Converts the user data to a JSON string.
     * This method should be implemented by subclasses to provide a JSON representation of the user.
     * @return a JSON string representing the user
     */
    public abstract String toJSON();

    /**
     * Sets the password for the user.
     * This method should be implemented by subclasses to update the user's password.
     * @param password the new password
     * @return the updated User object
     * @throws SQLException if an error occurs while updating the password
     */
    public abstract User setPassword(String password) throws SQLException;

    /**
     * Returns the username of the user.
     * This method should be implemented by subclasses to provide the user's username.
     * @return the username of the user
     */
    public abstract String getUsername();

    /**
     * Retrieves a user by their username.
     * This method searches for a user in the database using the provided username.
     * @param username the username of the user to retrieve
     * @return the User object if found, or null if not found
     */
    public static User getUser(String username) {
        if (username == null || username.isEmpty()) {
            return ANONYMOUS;
        }
        username = username.replace("%40", "@");
        Student student = Student.getByEmail(username);
        if (student != null) return student;
        Teacher teacher = Teacher.fromEmail(username);
        if (teacher != null) return teacher;
        Admin admin = Admin.get(username);
        if (admin != null) return admin;
        return null;
    }

    public static String generateRandomPassword(int length, long seed) {
        StringBuilder password = new StringBuilder();
        Random random = new Random(seed);
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-+";
        for (int i = 0; i < length; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }
        return password.toString();
    }

    /**
     * Returns the password hash of the user.
     * This method should be implemented by subclasses to provide the user's password hash.
     * @param password the password to hash
     * @return the password hash of the user
     */
    public static String passHash(String password) {
        return DigestUtils.sha1Hex(password);
    }

    /**
     * Converts the user to a Teacher object if possible.
     * @return the Teacher object if the user is a teacher, or null if not
     */
    public Teacher asTeacher() {
        if (this instanceof Teacher) {
            return (Teacher) this;
        }
        return null;
    }
    /**
     * Converts the user to a Student object if possible.
     * This method checks if the user is an instance of Student and returns it.
     * @return the Student object if the user is a student, or null if not
     */
    public Student asStudent() {
        if (this instanceof Student) {
            return (Student) this;
        }
        return null;
    }
    /**
     * Converts the user to an Admin object if possible.
     * This method checks if the user is an instance of Admin and returns it.
     * @return the Admin object if the user is an admin, or null if not
     */
    public Admin asAdmin() {
        if (this instanceof Admin) {
            return (Admin) this;
        }
        return null;
    }

    public String regeneratePassword() throws SQLException {
        String password = generateRandomPassword(12, CommonUtils.stringToSeed(getPasswordHash()) + System.currentTimeMillis() + new Random().nextInt(1000));
        setPassword(password);
        return password;
    }
}
