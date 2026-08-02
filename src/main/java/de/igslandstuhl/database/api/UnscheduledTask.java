package de.igslandstuhl.database.api;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.igslandstuhl.database.Application;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLHelper;

/**
 * Represents an unscheduled task that can be assigned to students. It extends the Task class and includes additional properties such as the associated school class, subject, maximum tokens, and a mapping of students to their respective token counts.
 * It does not belong to a specific topic, and a task can have a variable number of tokens for each student.
 * The UnscheduledTask was introduced in order to allow for more flexible task management, enabling tasks to be created and assigned without being tied to a specific topic or schedule.
 */
public class UnscheduledTask extends Task {
    private static final String[] SQL_FIELDS = {"id", "name", "class", "subject", "max_tokens"};
    private static final Map<Integer, UnscheduledTask> unscheduledTasks = new HashMap<>();

    private final SchoolClass schoolClass;
    private final Subject subject;
    private final int maxTokens;
    private final Map<Student, Integer> studentTokens = new HashMap<>();

    /**
     * Constructs a new UnscheduledTask with the specified parameters.
     * @param id the unique identifier for the task
     * @param name the name of the task
     * @param schoolClass the school class associated with the task
     * @param subject the subject associated with the task
     * @param maxTokens the maximum number of tokens for the task
     */
    public UnscheduledTask(int id, String name, SchoolClass schoolClass, Subject subject, int maxTokens) {
        super(id, null, name, TaskLevel.SPECIAL, maxTokens);
        this.schoolClass = schoolClass;
        this.subject = subject;
        this.maxTokens = maxTokens;
    }

    /**
     * Gets the school class associated with this unscheduled task.
     * @return the school class object
     */
    public SchoolClass getSchoolClass() {
        return schoolClass;
    }
    /**
     * Gets the subject associated with this unscheduled task.
     * @return the subject object
     */
    public Subject getSubject() {
        return subject;
    }
    /**
     * Gets the maximum number of tokens achievable for this unscheduled task.
     * @return the maximum tokens
     */
    public int getMaxTokens() {
        return maxTokens;
    }
    /**
     * Gets the mapping of students to their respective token counts for this unscheduled task.
     * @return a map of students and their token counts
     */
    public Map<Student, Integer> getStudentTokens() {
        return studentTokens;
    }

    /**
     * Returns the ratio of the unscheduled task.
     * This method now only returns 0, as ratios are no longer used.
     * @deprecated This method is deprecated and will be removed in future versions. Use getTokens() instead.
     */
    @Override
    @Deprecated
    public double getRatio() {
        return 0;
    }

    @Override
    public String toJSON() {
        return String.format("{\"id\":%d,\"name\":\"%s\",\"schoolClassId\":%d,\"subjectId\":%d,\"maxTokens\":%d,\"studentTokens\":%s}",
                getId(), getName(), schoolClass.getId(), subject.getId(), maxTokens, studentTokens.toString());
    }

    /**
     * Creates a UnscheduledTask object from SQL query result fields.
     * This method is used to convert the result of a database query into a UnscheduledTask object.
     *
     * @param sqlResult the result fields from the SQL query
     * @return a UnscheduledTask object constructed from the SQL fields
     */
    private static UnscheduledTask fromSQLFields(String[] sqlResult) {
        int id = Integer.parseInt(sqlResult[0]);
        String name = sqlResult[1];
        SchoolClass schoolClass = SchoolClass.get(Integer.parseInt(sqlResult[2]));
        Subject subject = Subject.get(Integer.parseInt(sqlResult[3]));
        int maxTokens = Integer.parseInt(sqlResult[4]);
        return new UnscheduledTask(id, name, schoolClass, subject, maxTokens);
    }
    /**
     * Retrieves a UnscheduledTask by its unique identifier.
     * If the task is cached, it returns the cached version.
     * Otherwise, it queries the database for the task.
     *
     * @param id the unique identifier of the unscheduled task
     * @return the UnscheduledTask object if found, or null if not found
     */
    public static UnscheduledTask get(int id) {
        if (unscheduledTasks.keySet().contains(id)) return unscheduledTasks.get(id);
        try {
            UnscheduledTask task = Server.getInstance().processSingleRequest(UnscheduledTask::fromSQLFields, "get_unscheduled_task_by_id", SQL_FIELDS, String.valueOf(id));
            unscheduledTasks.put(id, task);
            return task;
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get UnscheduledTask with id {} from database", id, e);
            return null;
        }
    }
    /**
     * Adds a UnscheduledTask to the cache from SQL result fields.
     * This method is used to populate the static map of unscheduled tasks from database query results.
     *
     * @param fields the SQL fields retrieved from the database
     */
    private static void addToCache(String[] fields) {
        UnscheduledTask task = fromSQLFields(fields);
        unscheduledTasks.put(task.getId(), task);
    }
    /**
     * Retrieves a list of unscheduled tasks by their names.
     * This method queries the database for unscheduled tasks matching the given name.
     *
     * @param name the name of the unscheduled tasks
     * @return a list of UnscheduledTask objects if found, or an empty list if not found
     */
    public static List<UnscheduledTask> getUnscheduledTasksByName(String name) {
        try {
            Server.getInstance().processRequest(UnscheduledTask::addToCache, "get_unscheduled_tasks_by_name", SQL_FIELDS, name);
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get UnscheduledTask with name {} from database", name, e);
            return new ArrayList<>();
        }
        return unscheduledTasks.values().stream()
                .filter(task -> task.getName().equalsIgnoreCase(name))
                .toList();
    }
    /**
     * Adds a new unscheduled task to the database.
     * 
     * @param name  the name of the task
     * @param schoolClass the class associated with this task
     * @param subject the subject area to which the task belongs
     * @param maxTokens the maximum amount of tokens achievable in this task
     * @throws SQLException if there is an error accessing the database
     * @return the newly created UnscheduledTask object, or null if the task could not be added
     */
    public static UnscheduledTask addUnscheduledTask(String name, SchoolClass schoolClass, Subject subject, int maxTokens) throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getAddObjectProcess("unscheduled_task", subject == null ? "-1" : name, String.valueOf(subject.getId()), String.valueOf(subject.getId()), String.valueOf(maxTokens)));
        return getUnscheduledTasksByName(name).stream()
                .filter(t -> t.getSubject() == subject && t.getSchoolClass() == schoolClass && t.getMaxTokens() == maxTokens)
                .sorted(Comparator.comparing(UnscheduledTask::getId, Comparator.reverseOrder()))
                .findFirst()
                .orElse(null);
    }
}
