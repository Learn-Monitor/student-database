package de.igslandstuhl.database.server.webserver.access;

import java.util.Optional;

import de.igslandstuhl.database.events.Event;
import de.igslandstuhl.database.events.EventType;
import de.igslandstuhl.database.server.webserver.requests.HttpRequest;

public class AccessManagerEvent extends Event {
    public static final EventType<AccessManagerEvent> TYPE = new EventType<>("AccessManagerEvent");

    private final AccessState accessState;

    private final String path;

    private final HttpRequest request;

    private Optional<AccessState> changedAccessState = Optional.empty();

    public AccessManagerEvent(AccessState accessState, String path, HttpRequest request) {
        this.accessState = accessState;
        this.path = path;
        this.request = request;
    }

    public AccessState getAccessState() {
        return accessState;
    }
    public String getPath() {
        return path;
    }
    public HttpRequest getRequest() {
        return request;
    }
    public Optional<AccessState> getChangedAccessState() {
        return changedAccessState;
    }
    @Override
    public EventType<? extends Event> getType() {
        return TYPE;
    }

    public void changeAccessState(AccessState newAccessState) {
        this.changedAccessState = Optional.of(newAccessState);
        this.cancel();
    }

    public static AccessManagerEvent unauthorized(String path, HttpRequest request) {
        return new AccessManagerEvent(AccessState.UNAUTHORIZED, path, request);
    }
    public static AccessManagerEvent authorized(String path, HttpRequest request) {
        return new AccessManagerEvent(AccessState.AUTHORIZED, path, request);
    }
    public static AccessManagerEvent restricted(String path, HttpRequest request) {
        return new AccessManagerEvent(AccessState.RESTRICTED, path, request);
    }
    public static AccessManagerEvent pending(String path, HttpRequest request) {
        return new AccessManagerEvent(AccessState.PENDING, path, request);
    }
}
