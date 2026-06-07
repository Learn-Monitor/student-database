package de.igslandstuhl.database.api;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import de.igslandstuhl.database.Application;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLHelper;

/**
 * Represents a subject in the student database.
 * Subjects can be added to grades and have associated topics.
 */
public class Subject implements APIObject {
    /**
     * A map to cache subjects by their unique identifier.
     * This helps avoid repeated database queries for the same subject.
     */
    private static final Map<Integer, Subject> subjects = new HashMap<>();
    /**
     * SQL fields for the Subject table.
     * Used for database queries to retrieve subject information.
     */
    static final String[] SQL_FIELDS = {"id", "name"};
    /**
     * The unique identifier for the subject.
     */
    private final int id;
    /**
     * The name of the subject.
     * This is a human-readable name for the subject.
     */
    private final String name;
    
    /**
     * Constructs a new Subject.
     *
     * @param id   the unique identifier for the subject
     * @param name the name of the subject
     */
    Subject(int id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Returns the unique identifier of the subject.
     * This is used to identify the subject in various operations.
     *
     * @return the unique identifier of the subject
     */
    public int getId() {
        return id;
    }
    /**
     * Returns the name of the subject.
     * This is a human-readable name for the subject.
     *
     * @return the name of the subject
     */
    public String getName() {
        return name;
    }

    /**
     * Creates a Subject object from SQL query result fields.
     * This method is used to convert the fields retrieved from the database into a Subject object.
     *
     * @param fields the SQL fields retrieved from the database
     * @return a Subject object populated with the retrieved data
     */
    static Subject fromSQLFields(String[] fields) {
        int id = Integer.parseInt(fields[0]);
        String name = fields[1];
        return new Subject(id, name);
    }
    /**
     * Retrieves a Subject by its unique identifier.
     * If the subject is cached, it returns the cached version.
     * Otherwise, it queries the database for the subject.
     *
     * @param id the unique identifier of the subject
     * @return the Subject object if found, or null if not found
     */
    public static Subject get(int id) {
        if (subjects.keySet().contains(id)) return subjects.get(id);
        try {
            Subject subject = Server.getInstance().processSingleRequest(Subject::fromSQLFields, "get_subject_by_id", SQL_FIELDS, String.valueOf(id));
            subjects.put(id, subject);
            return subject;
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get Subject with id {} from database", id, e);
            return null;
        }
    }
    /**
     * Retrieves a Subject by its name.
     * This method searches the cached subjects first, and if not found, queries the database.
     *
     * @param name the name of the subject
     * @return the Subject object if found, or null if not found
     */
    public static Subject get(String name) {
        for (Subject subject : subjects.values()) {
            if (subject.getName().equals(name)) {
                return subject;
            }
        }
        try {
            Subject subject = Server.getInstance().processSingleRequest(Subject::fromSQLFields, "get_subject_by_name", SQL_FIELDS, name);
            subjects.put(subject.getId(), subject);
            return subject;
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get Subject with name {} from database", name, e);
            return null;
        }
    }
    /**
     * Retrieves all subjects from the database.
     * This method queries the database to get a list of all subjects.
     *
     * @return a list of all Subject objects
     */
    public static List<Subject> getAll() {
        List<Integer> subjectIds = new ArrayList<>();
        try {
            Server.getInstance().processRequest(
                fields -> {
                    subjectIds.add(Integer.parseInt(fields[0]));
                },
                "get_all_subjects",
                SQL_FIELDS
            );
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to retrieve subject list from database", e);
        }
        return subjectIds.stream()
            .map(Subject::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Adds the subject to a specific grade.
     * This method associates the subject with a grade in the database.
     *
     * @param grade the grade to associate the subject with
     * @throws SQLException if there is an error accessing the database
     */
    public void addToGrade(int grade) throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(
            SQLHelper.getAddObjectProcess("subject_to_grade", String.valueOf(grade), String.valueOf(id))
        );
    }
    public void removeFromGrade(int grade) throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(
            SQLHelper.getDeleteObjectProcess("subject_from_grade", String.valueOf(grade), String.valueOf(id))
        );
    }

    @Override
    public String toString() {
        return "{\"id\": " + id + ", \"name\": \"" + name + "\"}";
    }
    /**
     * Adds a new subject to the database.
     * This method creates a new subject with the specified name.
     *
     * @param name the name of the subject to be added
     * @throws SQLException if there is an error accessing the database
     */
    public static Subject addSubject(String name) throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getAddObjectProcess("subject", name));
        return get(name);
    }

    /**
     * Retrieves all topics associated with the subject for a specific grade.
     * This method queries the database to find all topics that belong to the subject for the specified grade.
     *
     * @param grade the grade to retrieve topics for
     * @return a list of topics associated with the subject for the specified grade
     */
    public List<Topic> getTopics(int grade) {
        List<Integer> topicIds = new ArrayList<>();
        try {
            Server.getInstance().processRequest(
                fields -> {
                    topicIds.add(Integer.valueOf(fields[0]));
                },
                "get_topics_by_grade",
                new String[] {"id"},
                String.valueOf(grade),
                String.valueOf(id)
            );
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to retrieve topic list for subject '{}' in grade {} from database", this.name, grade, e);
        }
        return topicIds.stream()
        .map(Topic::get)
        .filter(Objects::nonNull)
        .toList();
    }

    public int[] getGrades() {
        List<Integer> grades = new ArrayList<>();
        try {
            Server.getInstance().processRequest(
            fields -> {
                grades.add(Integer.parseInt(fields[0]));
            },
            "get_grades_by_subject",
            new String[] {"grade"}, 
            String.valueOf(id));
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to retrieve list of available grades for subject '{}' from database", this.name, e);
        }
        return grades.stream().mapToInt(Integer::intValue).toArray();
    }

    public void edit(String name) throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(
            SQLHelper.getAddObjectProcess("subject_with_id", String.valueOf(id), name)
        );
        // Update the cached subject's name if present
        Subject updated = new Subject(id, name);
        subjects.put(id, updated);
    }

    public void delete() throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(
            SQLHelper.getDeleteObjectProcess("subject", String.valueOf(id))
        );
        subjects.remove(id);
        try {
            Arrays.stream(getGrades()).mapToObj(this::getTopics).forEach((l) -> l.forEach((t) -> {
                try {
                    t.delete();
                } catch (SQLException e) {
                    throw new IllegalStateException("Failed to delete topic " + t.getName() + " which is necessary to delete subject " + this.name, e);
                }
            }));
        } catch (IllegalStateException e) {
            if (e.getCause() != null && e.getCause() instanceof SQLException ex) {
                throw ex;
            } else {
                throw e;
            }
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
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
        Subject other = (Subject) obj;
        if (id != other.id)
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }

    @Override
    public String toJSON() {
        return "{\"id\": " + id + ", \"name\": \"" + name + "\"}";
    }
    
}
