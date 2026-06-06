package de.igslandstuhl.database.api;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import de.igslandstuhl.database.Application;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLHelper;

/**
 * Represents a task in the student database.
 * Tasks are associated with topics and have different levels of difficulty.
 */
public class Task implements APIObject {
    public static final int STATUS_NOT_STARTED = 0;
    public static final int STATUS_IN_PROGRESS = 1;
    public static final int STATUS_COMPLETED = 2;
    public static final int STATUS_LOCKED = 3;

    /**
     * SQL fields for the Task table.
     * Used for database queries to retrieve task information.
     */
    private static final String[] SQL_FIELDS = {"id", "topic", "name", "niveau"};
    /**
     * A map to cache tasks by their unique identifier.
     * This helps avoid repeated database queries for the same task.
     */
    private static final Map<Integer, Task> tasks = new HashMap<>();
    /**
     * The unique identifier for the task.
     */
    private final int id;
    /**
     * The topic associated with the task.
     * This is the subject area to which the task belongs.
     */
    private final Topic topic;
    /**
     * The name of the task.
     * This is a human-readable name for the task.
     */
    private final String name;
    /**
     * The level of difficulty for the task.
     * This indicates how challenging the task is, such as LEVEL1, LEVEL2, or LEVEL3.
     * It is also used to calculate the task's ratio in relation to the topic.
     */
    private final TaskLevel niveau;

    /**
     * Constructs a new Task.
     *
     * @param id    the unique identifier for the task
     * @param topic the topic associated with the task
     * @param name  the name of the task
     * @param niveau the level of difficulty for the task
     */
    protected Task(int id, Topic topic, String name, TaskLevel niveau) {
        this.id = id;
        this.topic = topic;
        this.name = name;
        this.niveau = niveau;
    }
    
    /**
     * Returns the unique identifier of the task.
     * This is used to identify the task in various operations.
     *
     * @return the unique identifier of the task
     */
    public int getId() {
        return id;
    }
    /**
     * Returns the topic associated with the task.
     * This is the subject area to which the task belongs.
     *
     * @return the topic associated with the task
     */
    public Topic getTopic() {
        return topic;
    }
    /**
     * Returns the name of the task.
     * This is a human-readable name for the task.
     *
     * @return the name of the task
     */
    public String getName() {
        return name;
    }
    /**
     * Returns the level of difficulty for the task.
     * This indicates how challenging the task is, such as LEVEL1, LEVEL2, or LEVEL3.
     *
     * @return the level of difficulty for the task
     */
    public TaskLevel getNiveau() {
        return niveau;
    }
    /**
     * Returns the number of the task in relation to its topic and level.
     * The number is formatted as "topicNumber.level.taskIndex".
     * For example, if the topic number is 1, and this is the first task at level 1, it would return "1.1.1".
     *
     * @return the formatted number of the task
     */
    public String getNumber() {
        switch (niveau) {
            case LEVEL1:
                return topic.getNumber() + ".1." + (topic.getTasksLevel1().indexOf(this) + 1);
            case LEVEL2:
                return topic.getNumber() + ".2." + (topic.getTasksLevel2().indexOf(this) + 1);
            case LEVEL3:
                return topic.getNumber() + ".3." + (topic.getTasksLevel3().indexOf(this) + 1);
            default:
                throw new IllegalStateException("Unknown level: " + niveau);
        }
    }
    /**
     * Returns the ratio of the task in relation to its topic and level.
     * The ratio is calculated based on the topic's ratio and the number of tasks at the same level.
     *
     * @return the ratio of the task
     */
    public double getRatio() {
        return niveau.getRatio() * topic.getRatio() / (100.0 * topic.getTasksByLevel(niveau).size());
    }
    /**
     * Returns the subject associated with the task's topic.
     * This is useful for retrieving the subject area to which the task belongs.
     *
     * @return the subject of the topic, or null if the topic is not set
     */
    public Subject getSubject() {
        if (topic == null) return null;
        return topic.getSubject();
    }

    public void removeFromCache() {
        tasks.remove(id);
    }

    public void delete() throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getDeleteObjectProcess("task", String.valueOf(id)));
        removeFromCache();
    }

    /**
     * Creates a Task object from SQL query result fields.
     *
     * @param fields the SQL fields retrieved from the database
     * @return a Task object populated with the retrieved data
     */
    private static Task fromSQLFields(String[] fields) {
        int id = Integer.parseInt(fields[0]);
        Topic topic = Topic.get(Integer.parseInt(fields[1]));
        String name = fields[2];
        TaskLevel niveau = TaskLevel.get(Integer.parseInt(fields[3]));
        return new Task(id, topic, name, niveau);
    }
    /**
     * Retrieves a Task by its unique identifier.
     * If the task is cached, it returns the cached version.
     * Otherwise, it queries the database for the task.
     *
     * @param id the unique identifier of the task
     * @return the Task object if found, or null if not found
     */
    public static Task get(int id) {
        if (tasks.keySet().contains(id)) return tasks.get(id);
        try {
            Task task = Server.getInstance().processSingleRequest(Task::fromSQLFields, "get_task_by_id", SQL_FIELDS, String.valueOf(id));
            tasks.put(id, task);
            return task;
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get Task with id {} from database", id, e);
            return null;
        }
    }
    /**
     * Retrieves a list of tasks by their names.
     * * @param name the name of the tasks
     * @return a list of Task objects if found, or an empty list if not found
     */
    public static List<Task> getByName(String name) {
        try {
            String[][] table = Server.getInstance().processRequest("get_tasks_by_name", new String[] {"id"}, name);
            Arrays.stream(table).map(s -> s[0]).map(Integer::parseInt).map(Task::get).forEach((t) -> t.getId()); // Do something because streams are lazy
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get Task with name {} from database", name, e);
            return new ArrayList<>();
        }
        return tasks.values().stream()
                .filter(task -> task.getName().equalsIgnoreCase(name))
                .toList();
    }
    /**
     * Retrieves a list of tasks by their unique identifiers.
     * This method queries the database for each task ID and returns a list of Task objects.
     *
     * @param ids the list of unique identifiers for the tasks
     * @return a list of Task objects corresponding to the provided IDs
     */
    public static List<Task> getTasksByIds(List<Integer> ids) {
        List<Task> tasks = new ArrayList<>();
        for (Integer id : ids) {
            Task task = Task.get(id);
            if (task != null) {
                tasks.add(task);
            }
        }
        return tasks;
    }

    @Override
    public String toString() {
        return "{\"id\": " + id + ", \"topic\": " + topic + ", \"name\": \"" + name + "\", \"niveau\": " + niveau + ", \"number\": \"" + getNumber() + "\", \"ratio\": " + getRatio() + "}";
    }

    /**
     * Adds a new task to the database.
     * This method creates a new task associated with a specific topic and level of difficulty.
     *
     * @param topic the topic to which the task belongs, or null if not associated with any topic
     * @param name  the name of the task
     * @param niveau the level of difficulty for the task
     * @throws SQLException if there is an error accessing the database
     * @return the newly created Task object, or null if the task could not be added
     */
    public static Task addTask(Topic topic, String name, TaskLevel niveau) throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getAddObjectProcess("task", topic == null ? "-1" : String.valueOf(topic.getId()), name, String.valueOf(niveau)));
        return getByName(name).stream()
                //.filter(t -> t.getTopic().equals(topic) && t.getNiveau() == niveau)
                .sorted(Comparator.comparing(Task::getId, Comparator.reverseOrder()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        result = prime * result + ((topic == null) ? 0 : topic.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((niveau == null) ? 0 : niveau.hashCode());
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
        Task other = (Task) obj;
        if (id != other.id)
            return false;
        if (topic == null) {
            if (other.topic != null)
                return false;
        } else if (!topic.equals(other.topic))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (niveau != other.niveau)
            return false;
        return true;
    }
    public static Task fromSerialized(Topic topic, String serialized) throws SQLException {
        String[] parts = serialized.split(Application.TASK_TITLE_DELIMITER);
        String name = parts[0];
        TaskLevel level = TaskLevel.get(Integer.parseInt(parts[1]));
        return addTask(topic, name, level);
    }

    @Override
    public String toJSON() {
        return "{\"id\": " + id + ", \"topic\": " + topic + ", \"name\": \"" + name + "\", \"niveau\": " + niveau + ", \"number\": \"" + getNumber() + "\", \"ratio\": " + getRatio() + "}";
    }
    
}
