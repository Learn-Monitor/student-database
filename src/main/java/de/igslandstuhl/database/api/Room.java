package de.igslandstuhl.database.api;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLHelper;

/**
 * Represents a room in the system.
 * Each room has a label and a minimum level required for students to access it.
 */
public class Room implements APIObject {
    private static final String[] SQL_FIELDS = {"label", "minimum_level"};
    /**
     * A map to store all rooms, keyed by their label.
     * This allows for quick access to room information without repeated database queries.
     */
    private static final Map<String, Room> rooms = new HashMap<>();
    /**
     * The label of the room, which is a unique identifier.
     */
    private final String label;
    /**
     * The minimum level required for students to access this room.
     * This is used to determine if a student is eligible to enter the room.
     */
    private final int minimumLevel;

    /**
     * Constructs a new Room.
     * @param label the label of the room
     * @param minimumLevel the minimum level required to access the room
     */
    private Room(String label, int minimumLevel) {
        this.label = label;
        this.minimumLevel = minimumLevel;
    }
    /**
     * Creates a Room instance from SQL result fields.
     * This method is used to convert the result of a database query into a Room object.
     * @param sqlResult the result fields from the SQL query
     * @return a Room object constructed from the SQL fields
     * @see Room#SQL_FIELDS
     */
    private static Room fromSQLFields(String[] sqlResult) {
        String label = sqlResult[0];
        int minimumLevel = Integer.parseInt(sqlResult[1]);
        return new Room(label, minimumLevel);
    }
    /**
     * Fetches all rooms from the database and populates the static map.
     * This method retrieves all room records and stores them in the `rooms` map for quick access.
     * @throws SQLException if there is an error accessing the database
     */
    public static void fetchAll() throws SQLException {
        rooms.clear();
        Server.getInstance().processRequest((fields) -> {
            Room room = fromSQLFields(fields);
            rooms.put(room.getLabel(), room);
        }, "get_all_rooms", SQL_FIELDS);
    }
    /**
     * Checks if the rooms map is empty and fetches all rooms if it is.
     * This method ensures that the rooms are loaded into memory only once,
     * preventing unnecessary database queries in subsequent calls.
     * @throws SQLException if there is an error accessing the database
     */
    public static void fetchAllIfNotExists() throws SQLException {
        if (rooms.size() == 0) {
            fetchAll();
        }
    }

    /**
     * Returns a map of all rooms.
     * This method ensures that all rooms are fetched from the database if they haven't been loaded yet.
     * @return a map of room labels to Room objects
     * @throws IllegalStateException if there is an error fetching rooms from the database
     */
    public static Map<String, Room> getRooms() {
        try {
            fetchAllIfNotExists();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not fetch rooms", e);
        }
        return rooms;
    }
    /**
     * Retrieves a room by its label.
     * This method checks if the room is already in the static map; if not, it fetches it from the database.
     * @param label the label of the room to retrieve
     * @return the Room object corresponding to the label, or null if not found
     */
    public static Room getRoom(String label) {
        if (rooms.keySet().contains(label)) {
            return rooms.get(label);
        } else {
            try {
                Room room = Server.getInstance().processSingleRequest(Room::fromSQLFields, "get_room_by_label", SQL_FIELDS, label);
                if (room == null) {
                    return null;
                }
                rooms.put(label, room);
                return room;
            } catch (SQLException e) {
                e.printStackTrace();
                return null;
            }
        }
    }
    /**
     * Adds a new room to the database and the static map.
     * This method creates a new Room object, inserts it into the database,
     * and adds it to the `rooms` map for future access.
     * @param label the label of the new room
     * @param minimumLevel the minimum level required to access the new room
     * @return the newly created Room object
     * @throws SQLException if there is an error inserting the room into the database
     */
    public static Room addRoom(String label, int minimumLevel) throws SQLException {
        Room room = new Room(label, minimumLevel);
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getAddObjectProcess("room", label, String.valueOf(minimumLevel)));
        rooms.put(label, room);
        return room;
    }
    public void delete() throws SQLException {
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getDeleteObjectProcess("room", getLabel()));
        rooms.remove(getLabel());
    }
    /**
     * Returns the label of the room.
     * This is used to identify the room in various operations.
     * @return the label of the room
     */
    public String getLabel() {
        return label;
    }
    /**
     * Returns the minimum level required to access the room.
     * This is used to determine if a student meets the requirements to enter the room.
     * @return the minimum level required for access
     */
    public int getMinimumLevel() {
        return minimumLevel;
    }

    public Room setMinimumLevel(int level) throws SQLException {
        if (level < 0 || level > 3) throw new IllegalArgumentException("Level " + level + " out of range");
        Server.getInstance().getConnection().executeVoidProcessSecure(SQLHelper.getUpdateObjectProcess("level_of_room", getLabel(), String.valueOf(level)));
        rooms.remove(getLabel());
        return getRoom(getLabel());
    }

    @Override
    public String toString() {
        return "{\"label\": \""+label+ "\", \"minimumLevel\": \"" + minimumLevel + "\"}";
    }
    /**
     * Adds multiple rooms to the database and the static map.
     * This method allows for batch creation of rooms, ensuring that all rooms are added in a single operation.
     * @param labels a list of room labels
     * @param minimumLevels a list of minimum levels corresponding to each room
     * @return a list of Room objects created
     * @throws SQLException if there is an error inserting any of the rooms into the database
     */
    public static List<Room> addAllRooms(List<String> labels, List<Integer> minimumLevels) throws SQLException {
        if (labels.size() != minimumLevels.size()) {
            throw new IllegalArgumentException("Labels and minimum levels must have the same size");
        }
        List<Room> rooms = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            rooms.add(addRoom(labels.get(i), minimumLevels.get(i)));
        }
        return rooms;
    }
    /**
     * Generates a list of Room objects from a CSV string.
     * This method parses the CSV data and creates Room objects for each entry.
     * @param csv the CSV string containing room data
     * @return an array of Room objects created from the CSV data
     */
    public static Room[] generateRoomsFromCSV(String csv) throws SQLException, IllegalArgumentException {
        String[] lines = csv.split("\n");
        List<String> labels = new ArrayList<>();
        List<Integer> minimumLevels = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String[] parts = lines[i].split(",");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid CSV format for room: " + lines[i]);
            }
            String label = parts[0].trim();
            int minimumLevel;
            try {
                minimumLevel = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid minimum level for room: " + lines[i], e);
            }
            labels.add(label);
            minimumLevels.add(minimumLevel);
        }
        List<Room> rooms = Room.addAllRooms(labels, minimumLevels);
        return rooms.toArray(new Room[rooms.size()]);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((label == null) ? 0 : label.hashCode());
        result = prime * result + minimumLevel;
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
        Room other = (Room) obj;
        if (label == null) {
            if (other.label != null)
                return false;
        } else if (!label.equals(other.label))
            return false;
        if (minimumLevel != other.minimumLevel)
            return false;
        return true;
    }
    @Override
    public String toJSON() {
        return "{\"label\": \""+label+ "\", \"minimumLevel\": \"" + minimumLevel + "\"}";
    }
    
}
