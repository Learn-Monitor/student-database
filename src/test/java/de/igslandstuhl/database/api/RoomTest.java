package de.igslandstuhl.database.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import de.igslandstuhl.database.server.Server;

public class RoomTest {
    Server server;
    @BeforeEach
    public void setupServer() throws SQLException {
        PreConditions.setupDatabase();
        server = Server.getInstance();
    }
    @Test
    @ResourceLock("ROOM")
    public void addRoom() throws SQLException {
        Room room = Room.addRoom("Gelingensnachweis", 0);
        assertNotNull(room);
        assertEquals("Gelingensnachweis", room.getLabel());
        assertEquals(0, room.getMinimumLevel());
        assertNotNull(Room.getRoom("Gelingensnachweis"));
    }
    @Test
    @ResourceLock("ROOM")
    public void getRoom() throws SQLException {
        Room added = Room.addRoom("Gelingensnachweis", 0);
        Room room = Room.getRoom("Gelingensnachweis");
        assertNotNull(room);
        assertEquals(added, room);
    }
    @Test
    @ResourceLock("ROOM")
    public void addAllRooms() throws SQLException {
        Room.getRooms().clear();
        List<Room> rooms = Room.addAllRooms(
            List.of("5 Einzelarbeitsraum", "5 Teamarbeitsraum", "5 Inputraum groß", "5 Inputraum klein",
                    "5 Gruppenarbeitsraum 1", "5 Gruppenarbeitsraum 2", "5 Gruppenarbeitsraum 3"),
            List.of(0, 0, 0, 0, 0, 0, 0)
        );
        assertEquals(7, rooms.size());
        assertThrows(IllegalArgumentException.class, () -> {
            Room.addAllRooms(
                List.of("5 Einzelarbeitsraum", "5 Teamarbeitsraum", "5 Inputraum groß", "5 Inputraum klein",
                        "5 Gruppenarbeitsraum 1", "5 Gruppenarbeitsraum 2", "5 Gruppenarbeitsraum 3"),
                List.of(0)
            );
        });
    }
    @Test
    @ResourceLock("ROOM")
    public void deleteRooms() throws SQLException {
        Room room = Room.addRoom("Gelingensnachweis", 0);
        assertNotNull(Room.getRoom("Gelingensnachweis"));
        room.delete();
        assertNull(Room.getRoom("Gelingensnachweis"));
    }
    @Test
    @ResourceLock("ROOM")
    public void fetchRooms() throws SQLException {
        Room.fetchAll();
        int roomSizeBefore = Room.getRooms().size();
        if (roomSizeBefore > 0) {
            Room.getRooms().clear();
            Room.fetchAll();
            assertEquals(roomSizeBefore, Room.getRooms().size());
        } else {
            Room testRoom = Room.addRoom("Gelingensnachweis", 0);
            assumeTrue(Room.getRooms().values().contains(testRoom));
            Room.getRooms().clear();
            Room.fetchAll();
            assertEquals(1, Room.getRooms().size());
        }

        Map<String, Room> rooms = Room.getRooms();
        Map<String, Room> roomCopy = new HashMap<>(rooms);
        Room.fetchAllIfNotExists();
        assertEquals(roomCopy, rooms);
        assertEquals(roomCopy, Room.getRooms());

        roomCopy.values().forEach((r) -> {
            try {
                r.delete();
            } catch (SQLException e) {
                assumeTrue(false);
            }
        });
        Room.addAllRooms(List.of("Test1", "Test2"), List.of(0, 0));
        Room.getRooms().clear();
        Room.fetchAllIfNotExists();
        assertEquals(2, Room.getRooms().size());
        Room.fetchAllIfNotExists();
        assertEquals(2, Room.getRooms().size());

        Room nonExistingRoom = Room.getRoom("Not existing");
        assertNull(nonExistingRoom);
        assertEquals(2, Room.getRooms().size());
        assertFalse(Room.getRooms().values().stream().map(Objects::nonNull).anyMatch((b) -> !b));

        Room.getRooms().clear();
        nonExistingRoom = Room.getRoom("Not existing");
        assertNull(nonExistingRoom);
        Room.fetchAllIfNotExists();
        assertEquals(2, Room.getRooms().size());
        assertFalse(Room.getRooms().values().stream().map(Objects::nonNull).anyMatch((b) -> !b));

        Room.fetchAll();
        assertEquals(2, Room.getRooms().size());
        assertFalse(Room.getRooms().values().stream().map(Objects::nonNull).anyMatch((b) -> !b));
    }
    @Test
    @ResourceLock("ROOM")
    public void testSetMinimumLevel() throws SQLException {
        Room room = Room.addRoom("Gelingensnachweis", 1);
        assumeTrue(1 == room.getMinimumLevel());
        room = room.setMinimumLevel(3);
        assertEquals(3, room.getMinimumLevel());
        Room.getRooms().clear();
        Room fetchedRoom = Room.getRoom("Gelingensnachweis");
        assertEquals(3, fetchedRoom.getMinimumLevel());

        assertThrows(IllegalArgumentException.class, () -> 
            fetchedRoom.setMinimumLevel(-1)
        );
        assertThrows(IllegalArgumentException.class, () -> 
            fetchedRoom.setMinimumLevel(Integer.MAX_VALUE - 1)
        );
    }
    @Test
    @ResourceLock("ROOM")
    public void testGenerateRoomsFromCSV() throws SQLException {
        String csv = "TestR1,0\nTestR2,1\nTestR3,2";
        Room.fetchAll();
        int roomSizeBefore = Room.getRooms().size();
        Room[] rooms = Room.generateRoomsFromCSV(csv);
        assertEquals(3, rooms.length);
        assertEquals(roomSizeBefore + 3, Room.getRooms().size());
    }
    @Test
    @ResourceLock("ROOM")
    public void testToJSON() throws SQLException {
        Room room = Room.addRoom("Gelingensnachweis", 0);
        String json = room.toJSON();
        assertNotNull(json);
        assertEquals(json, room.toString());
        Gson gson = new Gson();
        Map<String, Object> map = gson.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
        assertEquals("Gelingensnachweis", map.get("label"));
        assertEquals(0.0, map.get("minimumLevel"));
    }
    @Test
    @ResourceLock("ROOM")
    public void testHashCodeAndEquals() throws SQLException {
        Room room1 = Room.addRoom("Gelingensnachweis", 0);
        Room room2 = Room.getRoom("Gelingensnachweis");
        assertEquals(room1, room2);
        assertEquals(room1.hashCode(), room2.hashCode());

        Room room3 = Room.addRoom("AndererRaum", 1);
        assertNotEquals(room1, room3);
        assertNotEquals(room1.hashCode(), room3.hashCode());
    }
}
