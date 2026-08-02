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

public class IndividualTask extends Task {
    private static final String[] SQL_FIELDS = {"id", "name", "tokens", "subject_id"};
    private static final Map<Integer, IndividualTask> individualTasks = new HashMap<>();
    /**
     * The subject associated with this special task.
     * This is the subject area to which the special task belongs.
     */
    private final Subject subject;
    /**
     * Constructs a new IndividualTask.
     *
     * @param id    the unique identifier for the special task
     * @param name  the name of the special task
     * @param subject the subject associated with the special task
     * @param tokens the number of tokens for the special task
     */
    public IndividualTask(int id, String name, Subject subject, int tokens) {
        super(id, null, name, TaskLevel.SPECIAL, tokens);
        this.subject = subject;
    }

    /**
     * Returns the ratio of the special task.
     * This method now only returns 0, as ratios are no longer used.
     * @deprecated This method is deprecated and will be removed in future versions. Use getTokens() instead.
     */
    @Override
    @Deprecated
    public double getRatio() {
        return 0;
    }

    @Override
    public String toString() {
        return toJSON();
    }

    @Override
    public String toJSON() {
        return "{" +
                "\"id\":" + getId() +
                ", \"name\": \"" + getName() + '"' +
                ", \"tokens\": " + getTokens() +
                ", \"subject\": \"" + subject.getName() + '"' +
                '}';
    }

    /**
     * Creates a IndividualTask object from SQL query result fields.
     * This method is used to convert the result of a database query into a IndividualTask object.
     *
     * @param sqlResult the result fields from the SQL query
     * @return a IndividualTask object constructed from the SQL fields
     */
    private static IndividualTask fromSQLFields(String[] sqlResult) {
        int id = Integer.parseInt(sqlResult[0]);
        String name = sqlResult[1];
        int tokens = Integer.parseInt(sqlResult[2]);
        Subject subject = Subject.get(Integer.parseInt(sqlResult[3]));
        return new IndividualTask(id, name, subject, tokens);
    }
    /**
     * Retrieves a IndividualTask by its unique identifier.
     * If the task is cached, it returns the cached version.
     * Otherwise, it queries the database for the task.
     *
     * @param id the unique identifier of the special task
     * @return the IndividualTask object if found, or null if not found
     */
    public static IndividualTask get(int id) {
        if (individualTasks.keySet().contains(id)) return individualTasks.get(id);
        try {
            IndividualTask task = Server.getInstance().processSingleRequest(IndividualTask::fromSQLFields, "get_special_task_by_id", SQL_FIELDS, String.valueOf(id));
            individualTasks.put(id, task);
            return task;
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get IndividualTask with id {} from database", id, e);
            return null;
        }
    }
    /**
     * Adds a IndividualTask to the cache from SQL result fields.
     * This method is used to populate the static map of special tasks from database query results.
     *
     * @param fields the SQL fields retrieved from the database
     */
    private static void addToCache(String[] fields) {
        IndividualTask task = fromSQLFields(fields);
        individualTasks.put(task.getId(), task);
    }
    /**
     * Retrieves a list of special tasks by their names.
     * This method queries the database for special tasks matching the given name.
     *
     * @param name the name of the special tasks
     * @return a list of IndividualTask objects if found, or an empty list if not found
     */
    public static List<IndividualTask> getIndividualTasksByName(String name) {
        try {
            Server.getInstance().processRequest(IndividualTask::addToCache, "get_special_tasks_by_name", SQL_FIELDS, name);
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get IndividualTask with name {} from database", name, e);
            return new ArrayList<>();
        }
        return individualTasks.values().stream()
                .filter(task -> task.getName().equalsIgnoreCase(name))
                .toList();
    }
    /**
     * Adds a new special task to the database.
     * This method creates a new task associated with a specific topic and level of difficulty.
     *
     * @param name  the name of the task
     * @param ratio the ratio indicating the proportion of progress achievable at this level
     * @param subject the subject area to which the task belongs
     * @throws SQLException if there is an error accessing the database
     * @return the newly created IndividualTask object, or null if the task could not be added
     */
    public static IndividualTask addIndividualTask(String name, Subject subject, int tokens) throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getAddObjectProcess("special_task", subject == null ? "-1" : name, String.valueOf(tokens), String.valueOf(subject.getId())));
        return getIndividualTasksByName(name).stream()
                .filter(t -> t.getSubject() == subject && t.getTokens() == tokens)
                .sorted(Comparator.comparing(IndividualTask::getId, Comparator.reverseOrder()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Subject getSubject() {
        return subject;
    }
}
