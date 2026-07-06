package de.igslandstuhl.database.server.webserver.access;

import java.util.Optional;

import de.igslandstuhl.database.events.Event;
import de.igslandstuhl.database.events.EventType;

public class AccessManagerEvent extends Event {
    public static final EventType<AccessManagerEvent> TYPE = new EventType<>("AccessManagerEvent");

    private final AccessState accessState;

    private final String path;

    private Optional<AccessState> changedAccessState = Optional.empty();

    public AccessManagerEvent(AccessState accessState, String path) {
        this.accessState = accessState;
        this.path = path;
    }

    public AccessState getAccessState() {
        return accessState;
    }
    public String getPath() {
        return path;
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

    public static AccessManagerEvent unauthorized(String path) {
        return new AccessManagerEvent(AccessState.UNAUTHORIZED, path);
    }
    public static AccessManagerEvent authorized(String path) {
        return new AccessManagerEvent(AccessState.AUTHORIZED, path);
    }
    public static AccessManagerEvent restricted(String path) {
        return new AccessManagerEvent(AccessState.RESTRICTED, path);
    }
    public static AccessManagerEvent pending(String path) {
        return new AccessManagerEvent(AccessState.PENDING, path);
    }
}
