package de.igslandstuhl.database.server.webserver.sessions;

import java.util.HashMap;
import java.util.Map;

import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.webserver.requests.HttpRequest;

public class SessionStorage<T> {
    private final Map<Session, T> intern = new HashMap<>();

    public T get(Session session) {
        return intern.get(session);
    }
    public T get(HttpRequest request) {
        return intern.get(Server.getInstance().getWebServer().getSessionManager().getSession(request));
    }
    public void set(Session session, T object) {
        intern.put(session, object);
    }
    public void set(HttpRequest request, T object) {
        intern.put(Server.getInstance().getWebServer().getSessionManager().getSession(request), object);
    }
    public void remove(Session session) {
        intern.remove(session);
    }
    public void remove(Session session, T value) {
        intern.remove(session,value);
    }
    public void remove(T value) {
        intern.keySet().stream().filter((key) -> intern.get(key) == value)
        .toList().forEach((k) -> remove(k));
    }
    public boolean contains(T value) {
        return intern.values().contains(value);
    }
    public void clear() {
        intern.clear();
    }
}