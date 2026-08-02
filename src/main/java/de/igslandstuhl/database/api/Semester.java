package de.igslandstuhl.database.api;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import de.igslandstuhl.database.Application;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLHelper;

/**
 * Represents a semester (a part of a school year)
 */
public class Semester implements APIObject {
    private static final Map<Integer, Semester> CACHE = new HashMap<>();
    /**
     * SQL fields for the Semester table.
     * Used for database queries to retrieve class information.
     */
    private static final String[] SQL_FIELDS = {"id", "label", "position", "school_year"};
    /**
     * The unique identifier for the semester.
     */
    private final int id;
    /**
     * The label or name of the semester (e.g., "Fall 2023").
     */
    private final String label;
    /**
     * The position of the semester in the academic year (e.g., 1 for the first semester, 2 for the second semester).
     */
    private final int position;
    /**
     * The school year to which the semester belongs.
     */
    private final SchoolYear schoolYear;

    /**
     * Constructs a new Semester object with the specified id, label, and position.
     *
     * @param id       The unique identifier for the semester.
     * @param label    The label or name of the semester.
     * @param position The position of the semester in the academic year.
     * @param schoolYear The school year to which the semester belongs.
     */
    private Semester(int id, String label, int position, SchoolYear schoolYear) {
        this.id = id;
        this.label = label;
        this.position = position;
        this.schoolYear = schoolYear;
    }

    /**
     * Returns the unique identifier for the semester.
     *
     * @return The unique identifier for the semester.
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the label or name of the semester.
     *
     * @return The label or name of the semester.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the position of the semester in the academic year. (e.g., 1 for the first semester, 2 for the second semester).
     *
     * @return The position of the semester in the academic year. 
     */
    public int getPosition() {
        return position;
    }

    /**
     * Deletes this semester from the database.
     * @throws SQLException if a database access error occurs or the SQL statement fails
     */
    public void delete() throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getDeleteObjectProcess("semester", String.valueOf(id)));
    }

    private static Semester fromSQL(String[] fields) {
        int id = Integer.parseInt(fields[0]);
        String label = fields[1];
        int position = Integer.parseInt(fields[2]);
        SchoolYear schoolYear = SchoolYear.get(Integer.parseInt(fields[3]));
        return new Semester(id, label, position, schoolYear);
    }

    /**
     * Retrieves a Semester by its unique identifier from the database.
     * This method queries the database for a semester with the specified ID and returns a Semester object if found.
     * @param id The unique identifier of the semester to retrieve.
     * @return A Semester object representing the semester with the specified ID, or null if not found.
     */
    public static Semester get(int id) {
        if (CACHE.containsKey(id)) {
            return CACHE.get(id);
        }
        try {
            Semester semester = Server.getInstance().processSingleRequest(Semester::fromSQL, "get_semester_by_id", SQL_FIELDS, String.valueOf(id));
            if (semester != null) {
                CACHE.put(id, semester);
            }
            return semester;
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get Semester with id {} from database", id, e);
        }
        return null;
    }
    /**
     * Retrieves a Semester by its label from the database.
     * This method queries the database for a semester with the specified label and returns a Semester object if found.
     * @param label The label of the semester to retrieve.
     * @return A Semester object representing the semester with the specified label, or null if not found.
     */
    public static Semester get(String label) {
        try {
            Semester semester = Server.getInstance().processSingleRequest(Semester::fromSQL, "get_semester_by_label", SQL_FIELDS, label);
            if (semester != null) {
                CACHE.put(semester.getId(), semester);
            }
            return semester;
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get Semester with label {} from database", label, e);
        }
        return null;
    }
    /**
     * Retrieves a list of all semesters associated with a specific school year from the database.
     * @param schoolYear the school year
     * @return a list of Semester objects associated with the school year
     */
    public static List<Semester> getBySchoolYear(SchoolYear schoolYear) {
        List<Integer> ids = new ArrayList<>();
        try {
            Server.getInstance().processRequest(
                fields -> ids.add(Integer.parseInt(fields[0])),
                "get_semesters_by_school_year", new String[] {"id"}, String.valueOf(schoolYear.getId())
            );
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to retrieve a list of all semesters from this school year from the database", e);
        }
        return ids.stream()
            .map(Semester::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public static Semester addSemester(String label, int position, SchoolYear schoolYear) throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getAddObjectProcess("semester", label, String.valueOf(position), String.valueOf(schoolYear.getId())));
        return get(label);
    }

    @Override
    public String toJSON() {
        return String.format("{\"id\": %d, \"label\": \"%s\", \"position\": %d, \"schoolYear\": %s}", id, label, position, schoolYear.toJSON());
    }
    @Override
    public String toString() {
        return toJSON();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        result = prime * result + ((label == null) ? 0 : label.hashCode());
        result = prime * result + position;
        result = prime * result + ((schoolYear == null) ? 0 : schoolYear.hashCode());
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
        Semester other = (Semester) obj;
        if (id != other.id)
            return false;
        if (label == null) {
            if (other.label != null)
                return false;
        } else if (!label.equals(other.label))
            return false;
        if (position != other.position)
            return false;
        if (schoolYear == null) {
            if (other.schoolYear != null)
                return false;
        } else if (!schoolYear.equals(other.schoolYear))
            return false;
        return true;
    }
}
