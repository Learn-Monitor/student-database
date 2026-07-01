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

public class SpecialTask extends Task {
    private static final String[] SQL_FIELDS = {"id", "name", "ratio", "subject_id"};
    private static final Map<Integer, SpecialTask> specialTasks = new HashMap<>();
    /**
     * The ratio associated with this special task.
     * This indicates the proportion of progress that can be achieved at this level.
     */
    private final double ratio;
    /**
     * The subject associated with this special task.
     * This is the subject area to which the special task belongs.
     */
    private final Subject subject;
    /**
     * Constructs a new SpecialTask.
     *
     * @param id    the unique identifier for the special task
     * @param name  the name of the special task
     */
    public SpecialTask(int id, String name, double ratio, Subject subject) {
        super(id, null, name, TaskLevel.SPECIAL);
        this.ratio = ratio;
        this.subject = subject;
    }

    @Override
    public double getRatio() {
        return ratio;
    }

    @Override
    public String toString() {
        return "{" +
                "\"id\":" + getId() +
                ", \"name\": \"" + getName() + '"' +
                ", \"ratio\":" + ratio +
                ", \"subject\": \"" + subject.getName() + '"' +
                '}';
    }

    @Override
    public String toJSON() {
        return "{" +
                "\"id\":" + getId() +
                ", \"name\": \"" + getName() + '"' +
                ", \"ratio\":" + ratio +
                ", \"subject\": \"" + subject.getName() + '"' +
                '}';
    }

    /**
     * Creates a SpecialTask object from SQL query result fields.
     * This method is used to convert the result of a database query into a SpecialTask object.
     *
     * @param sqlResult the result fields from the SQL query
     * @return a SpecialTask object constructed from the SQL fields
     */
    private static SpecialTask fromSQLFields(String[] sqlResult) {
        int id = Integer.parseInt(sqlResult[0]);
        String name = sqlResult[1];
        double ratio = Double.parseDouble(sqlResult[2]);
        Subject subject = Subject.get(Integer.parseInt(sqlResult[3]));
        return new SpecialTask(id, name, ratio, subject);
    }
    /**
     * Retrieves a SpecialTask by its unique identifier.
     * If the task is cached, it returns the cached version.
     * Otherwise, it queries the database for the task.
     *
     * @param id the unique identifier of the special task
     * @return the SpecialTask object if found, or null if not found
     */
    public static SpecialTask get(int id) {
        if (specialTasks.keySet().contains(id)) return specialTasks.get(id);
        try {
            SpecialTask task = Server.getInstance().processSingleRequest(SpecialTask::fromSQLFields, "get_special_task_by_id", SQL_FIELDS, String.valueOf(id));
            specialTasks.put(id, task);
            return task;
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get SpecialTask with id {} from database", id, e);
            return null;
        }
    }
    /**
     * Adds a SpecialTask to the cache from SQL result fields.
     * This method is used to populate the static map of special tasks from database query results.
     *
     * @param fields the SQL fields retrieved from the database
     */
    private static void addToCache(String[] fields) {
        SpecialTask task = fromSQLFields(fields);
        specialTasks.put(task.getId(), task);
    }
    /**
     * Retrieves a list of special tasks by their names.
     * This method queries the database for special tasks matching the given name.
     *
     * @param name the name of the special tasks
     * @return a list of SpecialTask objects if found, or an empty list if not found
     */
    public static List<SpecialTask> getSpecialTasksByName(String name) {
        try {
            Server.getInstance().processRequest(SpecialTask::addToCache, "get_special_tasks_by_name", SQL_FIELDS, name);
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get SpecialTask with name {} from database", name, e);
            return new ArrayList<>();
        }
        return specialTasks.values().stream()
                .filter(task -> task.getName().equalsIgnoreCase(name))
                .toList();
    }
    /**
     * Adds a new special task to the database.
     * This method creates a new task associated with a specific topic and level of difficulty.
     *
     * @param topic the topic to which the task belongs, or null if not associated with any topic
     * @param name  the name of the task
     * @param niveau the level of difficulty for the task
     * @throws SQLException if there is an error accessing the database
     * @return the newly created SpecialTask object, or null if the task could not be added
     */
    public static SpecialTask addSpecialTask(String name, double ratio, Subject subject) throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getAddObjectProcess("special_task", subject == null ? "-1" : name, String.valueOf(ratio), String.valueOf(subject.getId())));
        return getSpecialTasksByName(name).stream()
                .filter(t -> t.getSubject() == subject && t.getRatio() == ratio)
                .sorted(Comparator.comparing(SpecialTask::getId, Comparator.reverseOrder()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Subject getSubject() {
        return subject;
    }
}
