package de.igslandstuhl.database.api;

import java.sql.SQLException;
import java.util.*;

import de.igslandstuhl.database.Application;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLHelper;

/**
 * Represents a school year with its associated properties and methods to manage it.
 * Provides functionality to retrieve, add, and update school years in the database.
 */
public class SchoolYear implements APIObject {
    /**
     * SQL fields for the SchoolYear table.
     * Used for database queries to retrieve school year information.
     */
    private static final String[] SQL_FIELDS = {"id", "label", "week_count", "current_week"};
    /**
     * A map to cache school years by their unique identifier.
     * This helps avoid repeated database queries for the same school year.
     */
    private static final Map<Integer, SchoolYear> years = new HashMap<>();

    /**
     * The unique identifier for the school year.
     */
    private final int id;
    /**
     * The label of the school year, which is a human-readable name.
     */
    private final String label;
    /**
     * The total number of weeks in the school year.
     */
    private final int weekCount;
    /**
     * The current week of the school year.
     * This is used to track the progress within the school year.
     */
    private int currentWeek;

    /**
     * Constructs a new SchoolYear.
     *
     * @param id          the unique identifier for the school year
     * @param label       the label of the school year
     * @param weekCount   the total number of weeks in the school year
     * @param currentWeek the current week of the school year
     */
    public SchoolYear(int id, String label, int weekCount, int currentWeek) {
        this.id = id;
        this.label = label;
        this.weekCount = weekCount;
        this.currentWeek = currentWeek;
    }

    /**
     * Returns the unique identifier of the school year.
     * This is used to identify the school year in various operations.
     *
     * @return the id of the school year
     */
    public int getId() { return id; }
    /**
     * Returns the label of the school year.
     * This is a human-readable name for the school year.
     *
     * @return the label of the school year
     */
    public String getLabel() { return label; }
    /**
     * Returns the total number of weeks in the school year.
     * This is used to determine the duration of the school year.
     *
     * @return the number of weeks in the school year
     */
    public int getWeekCount() { return weekCount; }
    /**
     * Returns the current week of the school year.
     * This is used to track the progress within the school year.
     *
     * @return the current week of the school year
     */
    public int getCurrentWeek() { return currentWeek; }
    /**
     * Sets the current week of the school year.
     * This updates the current week in the database and in the object.
     *
     * @param week the new current week to set
     * @throws SQLException if there is an error accessing the database
     */
    public void setCurrentWeek(int week) throws SQLException {
        this.currentWeek = week;
        Server.getInstance().getConnection().executeVoidProcessSecure(
            "UPDATE school_years SET current_week = " + week + " WHERE id = " + id
        );
    }

    /**
     * Creates a SchoolYear object from SQL query result fields.
     *
     * @param fields the SQL fields retrieved from the database
     * @return a SchoolYear object populated with the retrieved data
     */
    public static SchoolYear fromSQL(String[] fields) {
        int id = Integer.parseInt(fields[0]);
        String label = fields[1];
        int weekCount = Integer.parseInt(fields[2]);
        int currentWeek = Integer.parseInt(fields[3]);
        return new SchoolYear(id, label, weekCount, currentWeek);
    }
    /**
     * Retrieves a SchoolYear by its unique identifier.
     * If the school year is cached, it returns the cached version.
     * Otherwise, it queries the database for the school year.
     *
     * @param id the unique identifier of the school year
     * @return the SchoolYear object if found, or null if not found
     */
    public static SchoolYear get(int id) {
        if (years.containsKey(id)) return years.get(id);
        try {
            SchoolYear year = Server.getInstance().processSingleRequest(SchoolYear::fromSQL, "get_school_year_by_id", SQL_FIELDS, String.valueOf(id));
            if (year != null) years.put(id, year);
            return year;
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get SchoolYear with id {} from database", id, e);
            return null;
        }
    }
    public static SchoolYear get(String label) {
        try {
            return Server.getInstance().processSingleRequest((fields) -> {
                return get(Integer.parseInt(fields[0]));
            }, "get_school_year_by_label", new String[] {"id"}, label);
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to get SchoolYear with label '{}' from database", label, e);
            return null;
        }
    }
    /**
     * Retrieves all school years from the database.
     * This method queries the database and populates the cache with all school years.
     *
     * @return a list of all SchoolYear objects
     */
    public static List<SchoolYear> getAll() {
        List<SchoolYear> all = new ArrayList<>();
        try {
            Server.getInstance().processRequest(
                fields -> {
                    SchoolYear year = SchoolYear.fromSQL(fields);
                    years.put(year.getId(), year);
                    all.add(year);
                },
                "get_all_school_years", SQL_FIELDS
            );
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to retrieve a list of all school years from the database", e);
        }
        return all;
    }
    /**
     * Retrieves the current school year from the database.
     * This method queries the database for the school year that is currently active.
     *
     * @return the current SchoolYear object, or null if not found
     */
    public static SchoolYear getCurrentYear() {
        try {
            SchoolYear year = Server.getInstance().processSingleRequest(
                SchoolYear::fromSQL,
                "get_current_school_year",
                SQL_FIELDS
            );
            if (year != null) years.put(year.getId(), year);
            return year;
        } catch (SQLException e) {
            Application.LOGGER_API.error("Failed to load current school year from database", e);
            return null;
        }
    }

    /**
     * Adds a new school year to the database.
     * This method creates a new school year with the specified label, week count, and current week.
     *
     * @param label       the label of the new school year
     * @param weekCount   the total number of weeks in the new school year
     * @param currentWeek the current week of the new school year
     * @return the newly created SchoolYear object
     * @throws SQLException if there is an error accessing the database
     */
    public static SchoolYear addSchoolYear(String label, int weekCount, int currentWeek) throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(
            SQLHelper.getAddObjectProcess(
                "school_year",
                label,
                String.valueOf(weekCount),
                String.valueOf(currentWeek)
            )
        );
        // Fetch the newly created year
        return Server.getInstance().processSingleRequest(SchoolYear::fromSQL, "get_school_year_by_label", SQL_FIELDS, label);
    }

    public void delete() throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getDeleteObjectProcess("school_year", String.valueOf(id)));
    }

    @Override
    public String toString() {
        return "{\"id\":" + id + ",\"label\":\"" + label + "\",\"weekCount\":" + weekCount + ",\"currentWeek\":" + currentWeek + "}";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        result = prime * result + ((label == null) ? 0 : label.hashCode());
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
        SchoolYear other = (SchoolYear) obj;
        if (id != other.id)
            return false;
        if (label == null) {
            if (other.label != null)
                return false;
        } else if (!label.equals(other.label))
            return false;
        return true;
    }

    @Override
    public String toJSON() {
        return "{\"id\":" + id + ",\"label\":\"" + label + "\",\"weekCount\":" + weekCount + ",\"currentWeek\":" + currentWeek + "}";
    }
}