package de.igslandstuhl.database.api;

/**
 * An interface representing any object that is part of the student-database API
 */
public interface APIObject {
    /**
     * Returns a JSON representation of the object.
     * @return a JSON representation of the object.
     */
    public String toJSON();
}
