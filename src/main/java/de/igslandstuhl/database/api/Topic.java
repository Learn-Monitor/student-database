package de.igslandstuhl.database.api;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.igslandstuhl.database.Application;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLHelper;

/**
 * Represents a topic in the student database.
 * Topics are associated with subjects and contain tasks of varying difficulty levels.
 */
public class Topic implements APIObject {
    /**
     * SQL fields for the Topic table.
     * Used for database queries to retrieve topic information.
     */
    private static final String[] SQL_FIELDS = {"id", "name", "subject", "ratio", "grade", "number"};
    /**
     * A map to cache topics by their unique identifier.
     * This helps avoid repeated database queries for the same topic.
     */
    private static final Map<Integer, Topic> topics = new HashMap<>();

    /**
     * Unique identifier for the topic.
     */
    private final int id;
    /**
     * Name of the topic.
     */
    private final String name;
    /**
     * Subject associated with the topic.
     */
    private final Subject subject;
    /**
     * Ratio of the topic.
     */
    private final int ratio;
    /**
     * Grade level of the topic.
     */
    private final int grade;
    /**
     * Number of the topic.
     */
    private final int number;

    /**
     * List of tasks associated with the topic.
     * This list is populated when tasks are requested for the first time.
     */
    private List<Task> tasks = new ArrayList<>();
    /**
     * Lists of tasks with level 1.
     */
    private List<Task> tasksLevel1 = new ArrayList<>();
    /**
     * Lists of tasks with level 2.
     */
    private List<Task> tasksLevel2 = new ArrayList<>();
    /**
     * Lists of tasks with level 3.
     */
    private List<Task> tasksLevel3 = new ArrayList<>();

    /**
     * Constructs a Topic object with the specified parameters.
     *
     * @param id      the unique identifier for the topic
     * @param name    the name of the topic
     * @param subject the subject associated with the topic
     * @param ratio   the ratio of the topic
     * @param grade   the grade level of the topic
     * @param number  the number of the topic
     */
    public Topic(int id, String name, Subject subject, int ratio, int grade, int number) {
        this.id = id;
        this.name = name;
        this.subject = subject;
        this.ratio = ratio;
        this.grade = grade;
        this.number = number;
    }

    /**
     * Creates a Topic object from SQL query result fields.
     * This method is used to convert database query results into a Topic object.
     *
     * @param fields the fields retrieved from the database
     * @return a Topic object constructed from the provided fields
     */
    private static Topic fromSQLFields(String[] fields) {
        int id = Integer.parseInt(fields[0]);
        String name = fields[1];
        Subject subject = Subject.get(Integer.parseInt(fields[2]));
        int ratio = Integer.parseInt(fields[3]);
        int grade = Integer.parseInt(fields[4]);
        int number = Integer.parseInt(fields[5]);
        return new Topic(id, name, subject, ratio, grade, number);
    }
    /**
     * Retrieves a Topic by its unique identifier.
     * If the topic is cached, it returns the cached version.
     * Otherwise, it queries the database for the topic.
     *
     * @param id the unique identifier of the topic
     * @return the Topic object if found, or null if not found
     */
    public static Topic get(int id) {
        if (topics.keySet().contains(id)) return topics.get(id);
        try {
            Topic topic = Server.getInstance().processSingleRequest(Topic::fromSQLFields, "get_topic_by_id", SQL_FIELDS, String.valueOf(id));
            topics.put(id, topic);
            return topic;
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get Topic with id {} from database", id, e);
            return null;
        }
    }

    /**
     * Returns the unique identifier of the topic.
     * @return
     */
    public int getId() { return id; }
    /**
     * Returns the name of the topic.
     * @return
     */
    public String getName() { return name; }
    /**
     * Returns the subject associated with the topic.
     * @return
     */
    public Subject getSubject() { return subject; }
    /**
     * Returns the ratio of the topic.
     * @return
     */
    public int getRatio() { return ratio; }
    /**
     * Returns the grade level of the topic.
     * @return
     */
    public int getGrade() { return grade; }
    /**
     * Returns the number of the topic.
     * @return
     */
    public int getNumber() { return number; }
    /**
     * Retrieves all tasks associated with the topic.
     * If the tasks are not loaded yet, it loads them from the database.
     *
     * @return a list of tasks associated with the topic
     */
    public List<Task> getTasks() {
        if (tasks.isEmpty()) {
            loadTasks();
        }
        return tasks;
    }
    /**
     * Retrieves the IDs of all tasks associated with the topic.
     * This method iterates through the tasks and collects their IDs.
     *
     * @return a list of task IDs associated with the topic
     */
    public List<Integer> getTaskIds() {
        List<Integer> taskIds = new ArrayList<>();
        for (Task task : getTasks()) {
            taskIds.add(task.getId());
        }
        return taskIds;
    }
    /**
     * Retrieves tasks associated with the topic at level 1.
     * If the tasks are not loaded yet, it loads them from the database.
     *
     * @return a list of tasks at level 1 associated with the topic
     */
    public List<Task> getTasksLevel1() {
        if (tasks.isEmpty()) {
            loadTasks();
        }
        return tasksLevel1;
    }
    /**
     * Retrieves tasks associated with the topic at level 2.
     * If the tasks are not loaded yet, it loads them from the database.
     *
     * @return a list of tasks at level 2 associated with the topic
     */
    public List<Task> getTasksLevel2() {
        if (tasks.isEmpty()) {
            loadTasks();
        }
        return tasksLevel2;
    }
    /**
     * Retrieves tasks associated with the topic at level 3.
     * If the tasks are not loaded yet, it loads them from the database.
     *
     * @return a list of tasks at level 3 associated with the topic
     */
    public List<Task> getTasksLevel3() {
        if (tasks.isEmpty()) {
            loadTasks();
        }
        return tasksLevel3;
    }
    /**
     * Retrieves tasks associated with the topic at a specific level.
     * If the tasks are not loaded yet, it loads them from the database.
     *
     * @param level the difficulty level of the tasks to retrieve
     * @return a list of tasks at the specified level associated with the topic
     */
    public List<Task> getTasksByLevel(TaskLevel level) {
        if (tasks.isEmpty()) {
            loadTasks();
        }
        switch (level) {
            case LEVEL1:
                return getTasksLevel1();
            case LEVEL2:
                return getTasksLevel2();
            case LEVEL3:
                return getTasksLevel3();
            default:
                throw new IllegalArgumentException("Invalid level: " + level);
        }
    }
    /**
     * Filters tasks by their difficulty level.
     * This method is used to separate tasks into different lists based on their level.
     *
     * @param tasks the list of tasks to filter
     * @param level the difficulty level to filter by
     * @return a list of tasks that match the specified level
     */
    private List<Task> getTasksByLevel(List<Task> tasks, TaskLevel level) {
        return tasks.stream()
            .filter(task -> task.getNiveau() == level)
            .toList();
    }
    /**
     * Loads tasks associated with the topic from the database.
     * This method clears the existing task lists and retrieves tasks from the database.
     * It populates the tasks list and separates them into level-specific lists.
     */
    private void loadTasks() {
        tasks.clear();
        try {
            List<Integer> taskIds = new ArrayList<>();
            Server.getInstance().processRequest(
                fields -> {
                    taskIds.add(Integer.parseInt(fields[0]));
                },
                "get_tasks_by_topic", new String[] {"id"}, String.valueOf(id)
            );
            taskIds.stream().map(Task::get).filter(Objects::nonNull).forEach(t -> tasks.add(t));
            tasksLevel1 = getTasksByLevel(tasks, TaskLevel.LEVEL1);
            tasksLevel2 = getTasksByLevel(tasks, TaskLevel.LEVEL2);
            tasksLevel3 = getTasksByLevel(tasks, TaskLevel.LEVEL3);
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get task list for topic '{}' from database", this.name, e);
        }
    }
    private static void addToCache(String[] fields) {
        Topic topic = fromSQLFields(fields);
        topics.put(topic.getId(), topic);
    }
    /**
     * Retrieves a list of topics by their names.
     * @param name the name of the topics
     * @return a list of Topic objects if found, or an empty list if not found
     */
    public static List<Topic> getByName(String name) {
        try {
            Server.getInstance().processRequest(Topic::addToCache, "get_topics_by_name", SQL_FIELDS, name);
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get Topic with name '{}' from database", name, e);
            return new ArrayList<>();
        }
        return topics.values().stream()
                .filter(topic -> topic.getName().equalsIgnoreCase(name))
                .toList();
    }

    @Override
    public String toString() {
        return "{\"id\":" + id + ", \"name\": \"" + name + "\", \"subject\": " + subject + ", \"ratio\": " + ratio + ", \"grade\": " + grade
                + ", \"tasks\": " + getTaskIds() + ", \"number\": " + number + "}";
    }
    @Override
    public String toJSON() {
        return "{\"id\":" + id + ", \"name\": \"" + name + "\", \"subject\": " + subject + ", \"ratio\": " + ratio + ", \"grade\": " + grade
                + ", \"tasks\": " + getTaskIds() + ", \"number\": " + number + "}";
    }
    /**
     * Adds a new topic to the database.
     * This method executes a secure SQL process to insert a new topic with the provided parameters.
     * @param name the name of the topic
     * @param subject the subject associated with the topic
     * @param ratio the ratio of the topic
     * @param grade the grade level of the topic
     * @param number the number of the topic
     * @throws SQLException if a database error occurs
     */
    public static Topic addTopic(String name, Subject subject, int ratio, int grade, int number) throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getAddObjectProcess("topic", name, subject == null ? "-1" : String.valueOf(subject.getId()), String.valueOf(ratio), String.valueOf(grade), String.valueOf(number)));
        return getByName(name).stream()
                .filter(t -> t.getSubject() == subject && t.getRatio() == ratio && t.getGrade() == grade && t.getNumber() == number)
                .sorted((t1, t2) -> Integer.compare(t2.getId(), t1.getId())) // Sort by ID in descending order
                .findFirst()
                .orElse(null);
    }

    public void delete() throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getDeleteObjectProcess("topic", String.valueOf(id)));
        topics.remove(id);
        tasks.forEach(t -> {
            try {
                t.delete();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((subject == null) ? 0 : subject.hashCode());
        result = prime * result + ratio;
        result = prime * result + grade;
        result = prime * result + number;
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
        Topic other = (Topic) obj;
        if (id != other.id)
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (subject == null) {
            if (other.subject != null)
                return false;
        } else if (!subject.equals(other.subject))
            return false;
        if (ratio != other.ratio)
            return false;
        if (grade != other.grade)
            return false;
        if (number != other.number)
            return false;
        if (tasks == null) {
            if (other.tasks != null)
                return false;
        } else if (!tasks.equals(other.tasks))
            return false;
        if (tasksLevel1 == null) {
            if (other.tasksLevel1 != null)
                return false;
        } else if (!tasksLevel1.equals(other.tasksLevel1))
            return false;
        if (tasksLevel2 == null) {
            if (other.tasksLevel2 != null)
                return false;
        } else if (!tasksLevel2.equals(other.tasksLevel2))
            return false;
        if (tasksLevel3 == null) {
            if (other.tasksLevel3 != null)
                return false;
        } else if (!tasksLevel3.equals(other.tasksLevel3))
            return false;
        return true;
    }
    public static Topic fromSerialized(String serialized, Subject subject, int grade, int number) throws SerializationException, SQLException {
        Application.LOGGER_API.debug("Reading topic from serialized...");;
        // Gathering general info (topic name and ratio)
        String[] parts = serialized.split(Application.TITLE_DELIMITER);
        String[] generalInfo = parts[0].split(Application.TASK_DELIMITER);
        if (generalInfo.length != 2) {
            throw new SerializationException("Topic is not serialized correctly: '" + serialized + "'. Expected format: 'name" + Application.TASK_DELIMITER + "ratio'");
        }
        String name = generalInfo[0];
        if (name.isEmpty()) {
            throw new SerializationException("Topic name is empty in serialized string: '" + serialized + "'");
        }
        double ratio = Double.parseDouble(generalInfo[1]);
        if (ratio <= 0 || ratio > 1) {
            throw new SerializationException("Topic ratio must be greater than 0 and lower or equal to 1, but was: " + ratio + " in serialized string: '" + serialized + "'");
        }

        // Adding topic to the database, creating a new topic object
        Topic topic = addTopic(name, subject, (int)(ratio * 100), grade, number);

        // Deserializing the tasks, and adding them to the object
        if (parts.length > 1) {
            String[] tasks = parts[1].split(Application.TASK_DELIMITER);
            for (String s : tasks) {
                if (s != "") {
                    Task.fromSerialized(topic, s);
                }
            }

            topic.loadTasks();
            Application.LOGGER_API.debug("Loaded tasks for topic {}: {}", topic.getName(), topic.getTasks());
        }
        return topic;
    }
}
