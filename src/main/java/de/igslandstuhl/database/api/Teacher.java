package de.igslandstuhl.database.api;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import de.igslandstuhl.database.Application;
import de.igslandstuhl.database.api.results.TeacherGenerationResult;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLHelper;

/**
 * Represents a teacher in the student database.
 * Teachers can teach multiple classes and have a unique ID, first name, last name, email, and password hash.
 */
public class Teacher extends User {
    /**
     * SQL fields for the Teacher table.
     * Used for database queries to retrieve teacher information.
     */
    private static final String[] SQL_FIELDS = new String[] {"id", "first_name", "last_name", "email", "password"};
    /**
     * SQL fields for the teacher-to-class relationship.
     * Used to retrieve classes associated with a teacher.
     */
    private static final String[] CLASS_FIELDS = new String[] {"class_id"};
    /**
     * A map to cache teachers by their unique identifier.
     * This helps avoid repeated database queries for the same teacher.
     */
    private static final Map<Integer, Teacher> teachers = new HashMap<>();
    /**
     * A map to cache teachers by their email address.
     * This allows quick retrieval of a teacher by their email.
     */
    private static final Map<String, Teacher> teachersByEmail = new HashMap<>();

    /**
     * The unique identifier for the teacher.
     */
    private int id;
    /**
     * The first name of the teacher.
     */
    private String firstName;
    /**
     * The last name of the teacher.
     */
    private String lastName;
    /**
     * The email address of the teacher.
     */
    private String email;
    /**
     * The password hash for the teacher's account.
     */
    private String passwordHash;
    /**
     * The set of class IDs that this teacher is associated with.
     */
    private Set<Integer> classIds = new HashSet<>(); // IDs of classes this teacher teaches

    /**
     * Constructs a new Teacher.
     *
     * @param id          the unique identifier for the teacher
     * @param firstName   the first name of the teacher
     * @param lastName    the last name of the teacher
     * @param email       the email address of the teacher
     * @param passwordHash the hashed password for the teacher's account
     */
    public Teacher(int id, String firstName, String lastName, String email, String passwordHash) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    /**
     * Returns the unique identifier of the teacher.
     * This is used to identify the teacher in various operations.
     *
     * @return the unique identifier of the teacher
     */
    public int getId() { return id; }
    /**
     * Returns the first name of the teacher.
     *
     * @return the first name of the teacher
     */
    public String getFirstName() { return firstName; }
    /**
     * Returns the last name of the teacher.
     *
     * @return the last name of the teacher
     */
    public String getLastName() { return lastName; }
    /**
     * Returns the email address of the teacher.
     *
     * @return the email address of the teacher
     */
    public String getEmail() { return email; }
    @Override
    public String getUsername() {
        return getEmail();
    }
    /**
     * Returns the password hash of the teacher.
     * This is used for authentication purposes.
     *
     * @return the password hash of the teacher
     */
    @Override
    public String getPasswordHash() { return passwordHash; }

    /**
     * Returns the set of class IDs that this teacher is associated with.
     * This allows retrieval of all classes taught by the teacher.
     *
     * @return a set of class IDs
     */
    public Set<Integer> getClassIds() { return new HashSet<>(classIds); }
    /**
     * Adds a class ID to the set of classes this teacher teaches.
     * This method is used to associate a new class with the teacher.
     *
     * @param classId the unique identifier of the class to add
     */
    public void addClassId(int classId) { classIds.add(classId); }

    @Override
    public boolean isTeacher() { return true; }
    @Override
    public boolean isStudent() { return false; }
    @Override
    public boolean isAdmin() { return false; }

    @Override
    public String toJSON() {
        return String.format(
            "{\"id\":%d,\"firstName\":\"%s\",\"lastName\":\"%s\",\"email\":\"%s\",\"classIds\":%s}",
            id, firstName, lastName, email, classIds.toString()
        );
    }
    @Override
    public String toString() {
        return toJSON();
    }

    /**
     * Retrieves all students taught by this teacher.
     * This method iterates through all classes associated with the teacher
     *
     * @return a list of students taught by this teacher
     */
    public List<Student> getMyStudents() {
        List<Student> students = new ArrayList<>();
        for (int classId : classIds) {
            SchoolClass schoolClass = SchoolClass.get(classId);
            if (schoolClass != null) {
                students.addAll(schoolClass.getStudents());
            }
        }
        return students;
    }

    // --- Static DB logic ---

    /**
     * Creates a Teacher object from SQL query result fields.
     * This method is used to convert database query results into a Teacher object.
     *
     * @param fields the fields retrieved from the database
     * @return a Teacher object constructed from the provided fields
     */
    private static Teacher fromSQL(String[] fields) {
        int id = Integer.parseInt(fields[0]);
        String firstName = fields[1];
        String lastName = fields[2];
        String email = fields[3];
        String passwordHash = fields[4];
        return new Teacher(id, firstName, lastName, email, passwordHash);
    }
    /**
     * Retrieves a Teacher by their unique identifier.
     * If the teacher is cached, it returns the cached version.
     * Otherwise, it queries the database for the teacher.
     *
     * @param id the unique identifier of the teacher
     * @return the Teacher object if found, or null if not found
     */
    public static Teacher get(int id) {
        if (teachers.containsKey(id)) return teachers.get(id);
        try {
            Teacher teacher = Server.getInstance().processSingleRequest(Teacher::fromSQL, "get_teacher_by_id", SQL_FIELDS, String.valueOf(id));
            if (teacher == null) return null;
            teacher.loadClasses();
            teachers.put(id, teacher);
            teachersByEmail.put(teacher.getEmail(), teacher);
            return teacher;
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get Teacher with id {} from database", id, e);
            return null;
        }
    }
    /**
     * Retrieves a Teacher by their email address.
     * If the teacher is cached, it returns the cached version.
     * Otherwise, it queries the database for the teacher.
     *
     * @param email the email address of the teacher
     * @return the Teacher object if found, or null if not found
     */
    public static Teacher fromEmail(String email) {
        if (email == null) return null;
        if (teachersByEmail.containsKey(email)) return teachersByEmail.get(email);
        try {
            Teacher teacher = Server.getInstance().processSingleRequest(Teacher::fromSQL, "get_teacher_by_email", SQL_FIELDS, email);
            if (teacher == null) return null;
            teacher.loadClasses();
            teachers.put(teacher.getId(), teacher);
            teachersByEmail.put(email, teacher);
            return teacher;
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get Teacher with email {} from database", email, e);
            return null;
        }
    }
    /**
     * Retrieves all teachers from the database.
     * This method queries the database for all teachers and returns a list of Teacher objects.
     *
     * @return a list of all teachers
     */
    public static List<Teacher> getAll() {
        List<Integer> teacherIDs = new ArrayList<>();
        try {
            Server.getInstance().processRequest(
                fields -> {
                    teacherIDs.add(Integer.parseInt(fields[0]));
                },
                "get_all_teachers", SQL_FIELDS
            );
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to retrieve teacher list from database", e);
        }
        return teacherIDs.stream()
            .map(Teacher::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public List<Subject> getSubjects() {
        List<Integer> subjectIds = new ArrayList<>();
        try {
            Server.getInstance().processRequest(
                fields -> subjectIds.add(Integer.parseInt(fields[0])),
                "get_subjects_by_teacher", Subject.SQL_FIELDS, String.valueOf(id)
            );
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to retrieve subject list for teacher '{}' from database", this.email, e);
        }
        return subjectIds.stream()
            .map(Subject::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    public void addSubject(Subject subject) throws SQLException {
        if (subject == null || subject.getId() <= 0) return;
        Server.getInstance().getConnection().executeVoidProcessSecure(
            SQLHelper.getAddObjectProcess("subject_to_teacher", String.valueOf(id), String.valueOf(subject.getId()))
        );
    }

    /**
     * Registers a new teacher in the database.
     * This method adds a new teacher with the provided details and returns the created Teacher object.
     *
     * @param firstName the first name of the teacher
     * @param lastName  the last name of the teacher
     * @param email     the email address of the teacher
     * @param password  the password for the teacher's account
     * @return the newly created Teacher object
     * @throws SQLException if there is an error during database operations
     */
    public static Teacher registerTeacher(String firstName, String lastName, String email, String password) throws SQLException {
        String passwordHash = User.passHash(password);
        Server.getInstance().getConnection().executeVoidProcessSecure(
            SQLHelper.getAddObjectProcess("teacher", firstName, lastName, email, passwordHash)
        );
        // Now fetch the teacher from DB to get the ID and cache it
        return fromEmail(email);
    }

    public static TeacherGenerationResult[] generateTeachersFromCSV(String csv) throws SQLException {
        String[] lines = csv.split("\n");
        List<String> firstNames = new ArrayList<>();
        List<String> lastNames = new ArrayList<>();
        List<String> emails = new ArrayList<>();
        List<String> passwords = new ArrayList<>();

        for (String line : lines) {
            String[] fields = line.split(",");
            if (fields.length != 3) {
                throw new IllegalArgumentException("Invalid CSV format for teacher generation.");
            }
            firstNames.add(fields[0]);
            lastNames.add(fields[1]);
            emails.add(fields[2]);
            passwords.add(User.generateRandomPassword(12, new Random().nextInt(10000)));
            try {
                Thread.sleep(new Random().nextInt(100)); // Sleep to ensure unique passwords
            } catch (InterruptedException e) {
                Application.LOGGER_API.error("Thread sleep while generating passwords was interrupted", e);
            }
        }

        TeacherGenerationResult[] results = new TeacherGenerationResult[firstNames.size()];
        for (int i = 0; i < firstNames.size(); i++) {
            Teacher teacher = registerTeacher(firstNames.get(i), lastNames.get(i), emails.get(i), passwords.get(i));
            results[i] = new TeacherGenerationResult(teacher, passwords.get(i));
        }
        return results;
    }

    /**
     * Loads the classes taught by this teacher.
     * This method retrieves all class IDs associated with the teacher from the database.
     * It populates the classIds set with these IDs for further operations.
     */
    private void loadClasses() {
        classIds.clear();
        try {
            Server.getInstance().processRequest(
                fields -> classIds.add(Integer.parseInt(fields[0])),
                "get_teacher_classes", CLASS_FIELDS, String.valueOf(id)
            );
        } catch (SQLException e) {
                Application.LOGGER_API.error("Failed to retrieve class list of teacher '{}' from database", this.email, e);
        }
    }

    /**
     * Adds a class to this teacher.
     *
     * @param classId the ID of the class to add
     * @throws SQLException if there is an error during database operations
     */
    public void addClass(int classId) throws SQLException {
        if (classIds.contains(classId)) return;
        Server.getInstance().getConnection().executeVoidProcessSecure(
            SQLHelper.getAddObjectProcess("teacher_to_class", String.valueOf(id), String.valueOf(classId))
        );
        classIds.add(classId);
    }
    public void addClass(SchoolClass schoolClass) throws SQLException {
        addClass(schoolClass.getId());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        result = prime * result + ((firstName == null) ? 0 : firstName.hashCode());
        result = prime * result + ((lastName == null) ? 0 : lastName.hashCode());
        result = prime * result + ((email == null) ? 0 : email.hashCode());
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
        Teacher other = (Teacher) obj;
        if (id != other.id)
            return false;
        if (firstName == null) {
            if (other.firstName != null)
                return false;
        } else if (!firstName.equals(other.firstName))
            return false;
        if (lastName == null) {
            if (other.lastName != null)
                return false;
        } else if (!lastName.equals(other.lastName))
            return false;
        if (email == null) {
            if (other.email != null)
                return false;
        } else if (!email.equals(other.email))
            return false;
        return true;
    }

    @Override
    public Teacher setPassword(String password) throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getUpdateObjectProcess("password_hash_for_teachers", passHash(password), String.valueOf(id)));
        teachers.remove(id);
        return get(id);
    }
}