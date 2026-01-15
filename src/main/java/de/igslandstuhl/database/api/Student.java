package de.igslandstuhl.database.api;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import de.igslandstuhl.database.api.results.StudentGenerationResult;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLHelper;

/**
 * Represents a student in the system.
 * Inherits from {@link User} and provides student-specific data and logic.
 */
public class Student extends User {
    private static final String[] SQL_FIELDS = new String[] {"id", "first_name", "last_name", "email", "password", "class", "graduation_level"};
    private static final String[] INTERESTING_TASKSTAT_FIELDS = {"task"};
    private static final String[] INTERESTING_SPECIAL_TASK_STAT_FIELDS = {"special_task"};
    private static final Map<Integer, Student> students = new HashMap<>();

    /**
     * The unique ID of the student.
     */
    private final int id;

    /**
     * The first name of the student.
     */
    private final String firstName;

    /**
     * The last name of the student.
     */
    private final String lastName;

    /**
     * The email address of the student.
     */
    private final String email;

    /**
     * The hashed password of the student.
     */
    private final String passwordHash;

    /**
     * The class the student belongs to.
     */
    private final SchoolClass schoolClass;

    /**
     * The graduation level of the student.
     */
    private final GraduationLevel graduationLevel;

    /**
     * The set of tasks currently selected by the student.
     */
    private final Set<Task> selectedTasks = new HashSet<>();

    /**
     * The set of tasks completed by the student.
     */
    private final Set<Task> completedTasks = new HashSet<>();

    /**
     * The set of tasks locked for the student
     */
    private final Set<Task> lockedTasks = new HashSet<>();

    /**
     * The current requests of the student, mapped by subject ID.
     */
    private final Map<Integer, Set<SubjectRequest>> currentRequests = new ConcurrentHashMap<>();

    /**
     * The current topics of the student, mapped by subject.
     */
    private final Map<Subject, Topic> currentTopics = new ConcurrentHashMap<>();

    /**
     * The current room of the student.
     */
    private Room currentRoom = null;

    /**
     * Constructs a new Student.
     *
     * @param id The student ID.
     * @param firstName The first name.
     * @param lastName The last name.
     * @param email The email address.
     * @param passwordHash The hashed password.
     * @param schoolClass The school class.
     * @param graduationLevel The graduation level.
     */
    private Student(int id, String firstName, String lastName, String email, String passwordHash, SchoolClass schoolClass,
            GraduationLevel graduationLevel) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.schoolClass = schoolClass;
        this.graduationLevel = graduationLevel;
    }

    /**
     * Creates a Student instance from SQL data.
     *
     * @param fields The fields retrieved from the database.
     * @return A new Student instance.
     */
    private static Student fromSQL(String[] fields) {
        int id = Integer.parseInt(fields[0]);
        String firstName = fields[1];
        String lastName = fields[2];
        String email = fields[3];
        String password = fields[4];
        SchoolClass schoolClass = SchoolClass.get(Integer.parseInt(fields[5]));
        GraduationLevel graduationLevel = GraduationLevel.of(Integer.parseInt(fields[6]));
        Student student = new Student(id, firstName, lastName, email, password, schoolClass, graduationLevel);
        student.loadCurrentTopics();
        return student;
    }

    private void fetchTasks() throws SQLException {
        Server.getInstance().processRequest((t) -> {
            Task task = Task.get(Integer.parseInt(t[0]));
            if (task != null) selectedTasks.add(task);
        }, "get_selected_tasks_by_student", INTERESTING_TASKSTAT_FIELDS, String.valueOf(id));

        Server.getInstance().processRequest((t) -> {
            Task task = Task.get(Integer.parseInt(t[0]));
            if (task != null) completedTasks.add(task);
        }, "get_completed_tasks_by_student", INTERESTING_TASKSTAT_FIELDS, String.valueOf(id));

        Server.getInstance().processRequest((t) -> {
            Task task = Task.get(Integer.parseInt(t[0]));
            if (task != null) lockedTasks.add(task);
        }, "get_locked_tasks_by_student", INTERESTING_TASKSTAT_FIELDS, String.valueOf(id));

        Server.getInstance().processRequest((t) -> {
            SpecialTask st = SpecialTask.get(Integer.parseInt(t[0]));
            if (st != null) completedTasks.add(st);
        }, "get_completed_special_tasks_by_student", INTERESTING_SPECIAL_TASK_STAT_FIELDS, String.valueOf(id));

        // Defensive cleanup: ensure no nulls remained
        selectedTasks.removeIf(Objects::isNull);
        completedTasks.removeIf(Objects::isNull);
        lockedTasks.removeIf(Objects::isNull);
    }

    /**
     * Retrieves a Student by its unique identifier from the database.
     *
     * @param id the unique identifier of the student
     * @return a Student object if found, or null if not found
     */
    public static Student get(int id) {
        if (students.keySet().contains(id)) return students.get(id);
        try {
            Student student = Server.getInstance().processSingleRequest(Student::fromSQL, "get_student_by_id", SQL_FIELDS, String.valueOf(id));
            students.put(id, student);
            student.fetchTasks();
            return student;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * Retrieves a Student by its email address from the database.
     *
     * @param email the email address of the student
     * @return a Student object if found, or null if not found
     */
    public static Student getByEmail(String email) {
        try {
            Student student = Server.getInstance().processSingleRequest(Student::fromSQL, "get_student_by_email", SQL_FIELDS, email);
            if (student == null) return null;
            if (students.containsKey(student.getId())) return students.get(student.getId());
            students.put(student.getId(), student);
            student.fetchTasks();
            return student;
        } catch (NullPointerException e) {
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * Retrieves all students from the database.
     * This method queries the database for all students and returns a list of Student objects.
     *
     * @return a list of all students
     */
    public static List<Student> getAll() {
        List<Integer> studentIDs = new ArrayList<>();
        try {
            Server.getInstance().processRequest(
                fields -> {
                    studentIDs.add(Integer.parseInt(fields[0]));
                },
                "get_all_students", SQL_FIELDS
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return studentIDs.stream()
            .map(Student::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public static List<Student> getByRoom(Room room) {
        return students.values().stream().filter((s) -> room.equals(s.getCurrentRoom())).toList();
    }

    /**
     * Registers a new student with a password.
     * This method creates a new student in the database and returns the created Student object.
     * 
     * @param id the unique identifier for the student
     * @param firstName the first name of the student
     * @param lastName the last name of the student
     * @param email the email address of the student
     * @param password the password for the student
     * @param schoolClass the school class of the student
     * @param graduationLevel the graduation level of the student
     * @return the created Student object
     * @throws SQLException if there is an error creating the student
     */
    public static Student registerStudentWithPassword(int id, String firstName, String lastName, String email, String password, SchoolClass schoolClass, GraduationLevel graduationLevel) throws SQLException {
        Student student = new Student(id, firstName, lastName, email, User.passHash(password), schoolClass, graduationLevel);
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getAddObjectProcess("student", String.valueOf(id), firstName, lastName, email, User.passHash(password), schoolClass != null ? String.valueOf(schoolClass.getId()) : "-1", String.valueOf(graduationLevel.getLevel())));
        students.put(id, student);
        return student;
    }

    public static String[] generatePasswords(int count, int length) {
        String[] passwords = new String[count];
        for (int i = 0; i < count; i++) {
            int time = (int) System.currentTimeMillis();
            passwords[i] = generateRandomPassword(length, i + time + new Random().nextInt(1000));
            try {
                Thread.sleep(new Random().nextInt(1,10)); // Sleep to ensure different seeds
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return passwords;
    }
    public static StudentGenerationResult generateStudentWithPassword(int id, String firstName, String lastName, String email, SchoolClass schoolClass, GraduationLevel graduationLevel) throws SQLException {
        String password = generateRandomPassword(12, id + (int) System.currentTimeMillis());
        Student student = registerStudentWithPassword(id, firstName, lastName, email, password, schoolClass, graduationLevel);
        return new StudentGenerationResult(student, password);
    }
    public static StudentGenerationResult[] generateStudentsWithPasswords(int[] ids, String[] firstNames, String[] lastNames, String[] emails, String[] schoolClassNames, GraduationLevel[] graduationLevels) throws SQLException {
        if (ids.length != firstNames.length || ids.length != lastNames.length || ids.length != emails.length || ids.length != schoolClassNames.length || ids.length != graduationLevels.length) {
            throw new IllegalArgumentException("All input arrays must have the same length");
        }
        StudentGenerationResult[] results = new StudentGenerationResult[ids.length];
        String[] passwords = generatePasswords(ids.length, 12);
        for (int i = 0; i < ids.length; i++) {
            results[i] = new StudentGenerationResult(registerStudentWithPassword(ids[i], firstNames[i], lastNames[i], emails[i], passwords[i], SchoolClass.getOrCreate(schoolClassNames[i]), graduationLevels[i]), passwords[i]);
        }
        return results;
    }
    public static StudentGenerationResult[] generateStudentsFromCSV(String csv) throws SQLException {
        String[] lines = csv.split("\n");
        List<Integer> ids = new ArrayList<>();
        List<String> firstNames = new ArrayList<>();
        List<String> lastNames = new ArrayList<>();
        List<String> emails = new ArrayList<>();
        List<String> schoolClassNames = new ArrayList<>();
        List<GraduationLevel> graduationLevels = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String[] fields = lines[i].split(",");
            if (fields.length != 6) {
                throw new IllegalArgumentException("Invalid CSV format");
            }
            int id = Integer.parseInt(fields[0].trim());
            String firstName = fields[1].trim();
            String lastName = fields[2].trim();
            String email = fields[5].trim();
            String schoolClassName = fields[3].trim();
            GraduationLevel graduationLevel = GraduationLevel.initialValue();
            ids.add(id);
            firstNames.add(firstName);
            lastNames.add(lastName);
            emails.add(email);
            schoolClassNames.add(schoolClassName);
            graduationLevels.add(graduationLevel);
        }
        int[] idArray = ids.stream().mapToInt(Integer::intValue).toArray();
        String[] firstNameArray = new String[firstNames.size()];
        String[] lastNameArray = new String[lastNames.size()];
        String[] emailArray = new String[emails.size()];
        String[] schoolClassNameArray = new String[schoolClassNames.size()];
        GraduationLevel[] graduationLevelArray = new GraduationLevel[graduationLevels.size()];
        firstNames.toArray(firstNameArray);
        lastNames.toArray(lastNameArray);
        emails.toArray(emailArray);
        schoolClassNames.toArray(schoolClassNameArray);
        graduationLevels.toArray(graduationLevelArray);
        return generateStudentsWithPasswords(idArray, firstNameArray, lastNameArray, emailArray, schoolClassNameArray, graduationLevelArray);
    }

    /**
     * Returns the student's ID.
     * @return the ID
     */
    public int getId() { return id; }

    /**
     * Returns the student's first name.
     * @return the first name
     */
    public String getFirstName() { return firstName; }

    /**
     * Returns the student's last name.
     * @return the last name
     */
    public String getLastName() { return lastName; }

    /**
     * Returns the student's email address.
     * @return the email
     */
    public String getEmail() { return email; }

    @Override
    public String getUsername() {
        return getEmail();
    }

    /**
     * Returns the student's graduation level.
     * @return the graduation level
     */
    public GraduationLevel getGraduationLevel() { return graduationLevel; }

    /**
     * Returns the student's current room.
     * @return the current room
     */
    public Room getCurrentRoom() { return currentRoom; }

    /**
     * Sets the student's current room.
     * @param currentRoom the new room
     */
    public void setCurrentRoom(Room currentRoom) { this.currentRoom = currentRoom; }

    /**
     * Returns the set of selected tasks.
     * @return selected tasks
     */
    public Set<Task> getSelectedTasks() { return new HashSet<>(selectedTasks); }

    /**
     * Returns the set of completed tasks.
     * @return completed tasks
     */
    public Set<Task> getCompletedTasks() { return new HashSet<>(completedTasks); }

    /**
     * Returns the set of locked tasks.
     * @return locked tasks
     */
    public Set<Task> getLockedTasks() { return new HashSet<>(lockedTasks); }

    /**
     * Returns the current requests.
     * @return current requests
     */
    public Map<Integer, Set<SubjectRequest>> getCurrentRequests() { return currentRequests; }

    public Set<SubjectRequest> getCurrentRequests(Subject subject) {
        Set<SubjectRequest> subjectRequests = currentRequests.get(subject.getId());
        return subjectRequests == null ? Set.of() : subjectRequests;
    }

    /**
     * Returns the student's password hash.
     * @return password hash
     */
    public String getPasswordHash() { return passwordHash; }

    /**
     * Returns the student's school class.
     * @return school class
     */
    public SchoolClass getSchoolClass() { return schoolClass; }

    /**
     * Checks if the student has completed a specific task.
     * @param task the task to check
     * @return true if the student has completed the task, false otherwise
     */
    public boolean hasCompletedTask(Task task) {
        return completedTasks.contains(task);
    }

    /**
     * Adds a subject request for this student.
     * @param subjectId the subject ID
     * @param type the request type
     * @deprecated Use addSubjectRequest(Subject, SubjectRequest) instead
     */
    public void addSubjectRequest(int subjectId, String type) {
        currentRequests.computeIfPresent(subjectId, (key, value) -> {
            value.add(SubjectRequest.fromGermanTranslation(type));
            return value;
        });
        currentRequests.computeIfAbsent(subjectId, key -> new HashSet<>()).add(SubjectRequest.fromGermanTranslation(type));
    }
    /**
     * Adds a subject request for this student.
     * @param subject the corresponding subject
     * @param subjectRequest the request
     */
    public void addSubjectRequest(Subject subject, SubjectRequest subjectRequest) {
        addSubjectRequest(subject.getId(), subjectRequest.getGermanTranslation());
    }
    /**
     * Removes a subject request for this student.
     * @param subjectId the subject ID
     * @param type the request type to remove
     * @deprecated Use removeSubjectRequest(Subject, SubjectRequest) instead
     */
    public void removeSubjectRequest(int subjectId, String type) {
        currentRequests.computeIfPresent(subjectId, (key, value) -> {
            value.remove(SubjectRequest.fromGermanTranslation(type));
            return value;
        });
    }
    /**
     * Removes a subject request from this student.
     * @param subject the corresponding subject
     * @param subjectRequest the request
     */
    public void removeSubjectRequest(Subject subject, SubjectRequest subjectRequest) {
        removeSubjectRequest(subject.getId(), subjectRequest.getGermanTranslation());
    }

    public void beginTask(Task task) throws SQLException {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        // Update in DB
        Server.getInstance().getConnection().executeVoidProcessSecure(
            SQLHelper.getAddObjectProcess("taskstat",
                String.valueOf(id),
                String.valueOf(task.getId()),
                "1" // 1 indicates the task is in progress
            )
        );
        // Update in memory
        selectedTasks.add(task);
    }
    public void changeTaskStatus(Task task, int newStatus) throws SQLException {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        // Update in memory
        if (newStatus == Task.STATUS_COMPLETED) {
            selectedTasks.remove(task);
            lockedTasks.remove(task);
            completedTasks.add(task);
        } else if (newStatus == Task.STATUS_IN_PROGRESS) {
            completedTasks.remove(task);
            lockedTasks.remove(task);
            selectedTasks.add(task);
        } else if (newStatus == Task.STATUS_NOT_STARTED) {
            selectedTasks.remove(task);
            lockedTasks.add(task);
            completedTasks.remove(task);
        } else if (newStatus == Task.STATUS_LOCKED) {
            selectedTasks.remove(task);
            completedTasks.remove(task);
            lockedTasks.add(task);
        } else {
            throw new IllegalArgumentException("Invalid task status: " + newStatus);
        }
        // Update in DB
        Server.getInstance().getConnection().executeVoidProcessSecure(
            SQLHelper.getAddObjectProcess("taskstat",
                String.valueOf(id),
                String.valueOf(task.getId()),
                String.valueOf(newStatus)
            )
        );
    }
    public Student changeGraduationLevel(int graduationLevel) throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getUpdateObjectProcess("graduation_level", String.valueOf(graduationLevel), String.valueOf(id)));
        students.remove(id);
        return get(id);
    }

    /**
     * Removes a subject request for this student.
     * @param subjectId the subject ID
     */
    public void clearSubjectRequest(int subjectId) {
        currentRequests.remove(subjectId);
    }

    public void delete() throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getDeleteObjectProcess("student", String.valueOf(id)));
        students.remove(id);
    }

    @Override
    public String toString() {
        return toJSON();
    }
    @Override
    public boolean isTeacher() {
        return false;
    }
    @Override
    public boolean isStudent() {
        return true;
    }
    @Override
    public boolean isAdmin() {
        return false;
    }

    @Override
    public String toJSON() {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n")
        .append("\"id\": ").append(id).append(",\n")
        .append("\"firstName\": \"").append(firstName).append("\",\n")
        .append("\"lastName\": \"").append(lastName).append("\",\n")
        .append("\"email\": \"").append(email).append("\",\n")
        .append("\"schoolClass\": ").append(String.valueOf(schoolClass)).append(",\n")
        .append("\"graduationLevel\": ").append(graduationLevel.getLevel()).append(",\n")
        .append("\"selectedTasks\": ").append(selectedTasks).append(",\n")
        .append("\"completedTasks\": ").append(completedTasks).append(",\n")
        .append("\"lockedTasks\": ").append(lockedTasks).append(",\n")
        .append("\"currentRoom\": ").append(String.valueOf(currentRoom)).append(",\n")
        .append("\"currentRequests\": {").append(currentRequests.entrySet().stream()
            .map(entry -> "\"" + entry.getKey() + "\": " + entry.getValue().stream().map((r) -> '"' + r.getGermanTranslation() + '"').toList())
            .reduce((a, b) -> a + ", " + b).orElse("")).append("},\n")
        .append("\"currentProgress\": {");
        for (Map.Entry<Subject, Topic> entry : currentTopics.entrySet()) {
            builder.append("\"").append(entry.getKey().getName()).append("\": ");
            builder.append("{\"topic\": ").append(entry.getValue().getId()).append(", ");
            builder.append("\"progress\": ").append(getCurrentProgress(entry.getKey())).append("}, ");
        }
        if (!currentTopics.isEmpty()) {
            builder.setLength(builder.length() - 2); // Remove trailing comma and space
        }
        builder.append("},\n");
        builder.append("\"predictedProgress\": {");
        for (Map.Entry<Subject, Topic> entry : currentTopics.entrySet()) {
            builder.append("\"").append(entry.getKey().getName()).append("\": ");
            builder.append("{\"topic\": ").append(entry.getValue().getId()).append(", ");
            builder.append("\"predictedProgress\": ").append(getPredictedProgress(entry.getKey())).append("}, ");
        }
        if (!currentTopics.isEmpty()) {
            builder.setLength(builder.length() - 2); // Remove trailing comma and space
        }
        builder.append("}\n");
        builder.append("}");
        return builder.toString();
    }

    private void loadCurrentTopics() {
        currentTopics.clear();
        try {
            List<Integer> subjectIds = new ArrayList<>();
            List<Integer> topicIds = new ArrayList<>();
            Server.getInstance().processRequest(
                fields -> {
                    subjectIds.add(Integer.parseInt(fields[1]));
                    topicIds.add(Integer.parseInt(fields[2]));
                },
                "get_current_topics_by_student",
                new String[] {"student_id", "subject_id", "topic_id"},
                String.valueOf(id)
            );
            for (int i = 0; i < subjectIds.size(); i++) {
                Subject subject = Subject.get(subjectIds.get(i));
                Topic topic = Topic.get(topicIds.get(i));
                if (subject != null && topic != null) {
                    currentTopics.put(subject, topic);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets the current topic for a given subject.
     * @param subject the subject
     * @param topic the topic to set
     * @throws SQLException if an error occurs while updating the database
     */
    public void setCurrentTopic(Subject subject, Topic topic) throws SQLException {
        // Update in DB
        Server.getInstance().getConnection().executeVoidProcessSecure(
            SQLHelper.getAddObjectProcess("topic_to_student",
                String.valueOf(id),
                String.valueOf(topic.getId()),
                String.valueOf(topic.getSubject().getId())
            )
        );
        // Update in memory
        currentTopics.put(subject, topic);
    }

    /**
     * Assigns a topic to the student.
     * @param topic the topic to assign
     * @throws SQLException if an error occurs while updating the database
     */
    public void assignTopic(Topic topic) throws SQLException {
        if (topic == null) {
            throw new IllegalArgumentException("Topic cannot be null");
        }
        Subject subject = topic.getSubject();
        if (subject == null) {
            throw new IllegalArgumentException("Topic must have a subject");
        }
        setCurrentTopic(subject, topic);
    }

    /**
     * Returns the current topics of the student, mapped by subject.
     * @return a map of subjects to their current topics
     */
    public Map<Subject, Topic> getCurrentTopics() {
        // Optionally reload from DB if needed
        return new HashMap<>(currentTopics);
    }

    /**
     * Returns the current topic for a given subject.
     * @param subject the subject to get the current topic for
     * @return the current topic, or null if not set
     */
    public Topic getCurrentTopic(Subject subject) {
        return currentTopics.get(subject);
    }

    /**
     * Returns the current progress of the student for a given subject.
     * @param subject the subject to get the current progress for
     * @return the current progress as a percentage (0-1)
     */
    public double getCurrentProgress(Subject subject) {
        return completedTasks.stream()
            .filter(Objects::nonNull)
            .filter(task -> task.getSubject() != null && task.getSubject().equals(subject))
            .mapToDouble(Task::getRatio)
            .sum();
    }
    /**
     * Returns the currently achieved grade for a given subject based on the current progress.
     * @param subject the subject to evaluate
     * @return the grade as an integer (1-6)
     */
    public int getCurrentlyAchievedGrade(Subject subject) {
        double progress = getCurrentProgress(subject);
        if (progress >= 0.85) {
            return 1; // Excellent
        } else if (progress >= 0.70) {
            return 2; // Good
        } else if (progress >= 0.55) {
            return 3; // Satisfactory
        } else if (progress >= 0.40) {
            return 4; // Sufficient
        } else if (progress >= 0.20) {
            return 5; // Deficient
        } else {
            return 6; // Insufficient
        }
    }
    /**
     * Predicts the student's progress for a given subject based on the current week of the school year.
     * @param subject the subject to predict progress for
     * @return the predicted progress as a percentage (0-100)
     */
    public double getPredictedProgress(Subject subject) {
        double progress = getCurrentProgress(subject);
        SchoolYear currentYear = SchoolYear.getCurrentYear();
        if (currentYear == null) {
            return 0; // No current year available
        }
        double specials = completedTasks.stream().filter((t) -> (t instanceof SpecialTask)).mapToDouble(Task::getRatio).sum();
        return Math.min(progress * currentYear.getWeekCount() / currentYear.getCurrentWeek(), 1 + specials);
    }
    /**
     * Predicts the student's grade for a given subject based on the predicted progress.
     * @param subject the subject to predict the grade for
     * @return the predicted grade as an integer (1-6)
     */
    public int getPredictedGrade(Subject subject) {
        double predictedProgress = getPredictedProgress(subject);
        if (predictedProgress >= 0.85) {
            return 1; // Excellent
        } else if (predictedProgress >= 0.70) {
            return 2; // Good
        } else if (predictedProgress >= 0.55) {
            return 3; // Satisfactory
        } else if (predictedProgress >= 0.40) {
            return 4; // Sufficient
        } else if (predictedProgress >= 0.20) {
            return 5; // Deficient
        } else {
            return 6; // Insufficient
        }
    }

    /**
     * Checks if the student has a specific teacher.
     * @param teacher the teacher to check
     * @return true if the student has the teacher, false otherwise
     */
    public boolean hasTeacher(Teacher teacher) {
        return teacher != null && schoolClass != null && teacher.getClassIds().contains(schoolClass.getId());
    }
    public boolean isActionRequired() {
        return currentRequests.entrySet().stream().anyMatch((set) -> !set.getValue().isEmpty());
    }

    public void assignCompletedSpecialTask(SpecialTask task) throws SQLException {
        if (task == null) {
            throw new IllegalArgumentException("Special task cannot be null");
        }
        // Update in DB
        Server.getInstance().getConnection().executeVoidProcessSecure(
            SQLHelper.getAddObjectProcess("special_task_to_student",
                String.valueOf(id),
                String.valueOf(task.getId())
            )
        );
        // Update in memory
        completedTasks.add(task);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        result = prime * result + ((firstName == null) ? 0 : firstName.hashCode());
        result = prime * result + ((lastName == null) ? 0 : lastName.hashCode());
        result = prime * result + ((email == null) ? 0 : email.hashCode());
        result = prime * result + ((schoolClass == null) ? 0 : schoolClass.hashCode());
        result = prime * result + graduationLevel.getLevel();
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
        Student other = (Student) obj;
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
        if (schoolClass == null) {
            if (other.schoolClass != null)
                return false;
        } else if (!schoolClass.equals(other.schoolClass))
            return false;
        if (graduationLevel != other.graduationLevel)
            return false;
        return true;
    }

    @Override
    public Student setPassword(String password) throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getUpdateObjectProcess("password_hash_for_student", passHash(password), String.valueOf(getId())));
        students.remove(id);
        return get(id);
    }

    /**
     * Generates a CSV string containing the student's results.
     * @return a CSV string of the student's results
     */
    public String getResultsCSV() {
        StringBuilder csvBuilder = new StringBuilder();
        csvBuilder.append("Subject,Current Progress (%),Predicted Progress (%),Currently Achieved Grade,Predicted Grade\n");
        for (Subject subject : getSchoolClass().getSubjects()) {
            double currentProgress = getCurrentProgress(subject) * 100;
            double predictedProgress = getPredictedProgress(subject) * 100;
            int currentGrade = getCurrentlyAchievedGrade(subject);
            int predictedGrade = getPredictedGrade(subject);
            csvBuilder.append(String.format(Locale.US, "%s,%.2f,%.2f,%d,%d\n",
                    subject.getName(),
                    currentProgress,
                    predictedProgress,
                    currentGrade,
                    predictedGrade));
        }
        return csvBuilder.toString();
    }
    /**
     * Generates a CSV string containing all students' results.
     * @return a CSV string of all students' results
     */
    public static String getAllResultsCSV() {
        StringBuilder csvBuilder = new StringBuilder();
        csvBuilder.append("Student ID,First Name,Last Name,Email,Subject,Current Progress (%),Predicted Progress (%),Currently Achieved Grade,Predicted Grade\n");
        for (Student student : getAll()) {
            for (Subject subject : student.getSchoolClass().getSubjects()) {
                double currentProgress = student.getCurrentProgress(subject) * 100;
                double predictedProgress = student.getPredictedProgress(subject) * 100;
                int currentGrade = student.getCurrentlyAchievedGrade(subject);
                int predictedGrade = student.getPredictedGrade(subject);
                csvBuilder.append(String.format(Locale.US, "%d,%s,%s,%s,%s,%.2f,%.2f,%d,%d\n",
                        student.getId(),
                        student.getFirstName(),
                        student.getLastName(),
                        student.getEmail(),
                        subject.getName(),
                        currentProgress,
                        predictedProgress,
                        currentGrade,
                        predictedGrade));
            }
        }
        return csvBuilder.toString();
    }
}
