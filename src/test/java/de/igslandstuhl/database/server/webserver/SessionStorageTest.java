package de.igslandstuhl.database.server.webserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.server.webserver.requests.PostRequest;
import de.igslandstuhl.database.server.webserver.sessions.Session;
import de.igslandstuhl.database.server.webserver.sessions.SessionStorage;

class SessionStorageTest {
    @Test
    void removeUsesValueEqualityForDifferentStringInstances() {
        SessionStorage<String> storage = new SessionStorage<>();
        Session target = session();
        Session other = session();
        storage.set(target, new String("same-user"));
        storage.set(other, "other-user");

        storage.remove(new String("same-user"));

        assertNull(storage.get(target));
        assertEquals("other-user", storage.get(other));
    }

    @Test
    void getSessionsFindsEqualValuesAndKeepsOtherUsers() {
        SessionStorage<String> storage = new SessionStorage<>();
        Session first = session();
        Session second = session();
        Session other = session();
        storage.set(first, new String("multi-user"));
        storage.set(second, "multi-user");
        storage.set(other, "other-user");

        assertEquals(2, storage.getSessions(new String("multi-user")).size());
        assertEquals(1, storage.getSessions("other-user").size());
    }

    private static Session session() {
        return new Session(new PostRequest("POST /login HTTP/1.1\r\nCookie: session=" + UUID.randomUUID(), "", "127.0.0.1", true));
    }
}
