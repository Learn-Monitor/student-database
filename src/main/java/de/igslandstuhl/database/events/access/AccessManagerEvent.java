package de.igslandstuhl.database.events.access;

import java.util.Optional;

import de.igslandstuhl.database.events.Event;
import de.igslandstuhl.database.events.EventType;

public class AccessManagerEvent extends Event {
    public static final EventType<AccessManagerEvent> TYPE = new EventType<>("AccessManagerEvent");

    private final AccessState accessState;

    private Optional<AccessState> changedAccessState = Optional.empty();

    public AccessManagerEvent(AccessState accessState) {
        this.accessState = accessState;
    }

    public AccessState getAccessState() {
        return accessState;
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

    public static AccessManagerEvent unauthorized() {
        return new AccessManagerEvent(AccessState.UNAUTHORIZED);
    }
    public static AccessManagerEvent authorized() {
        return new AccessManagerEvent(AccessState.AUTHORIZED);
    }
    public static AccessManagerEvent restricted() {
        return new AccessManagerEvent(AccessState.RESTRICTED);
    }
    public static AccessManagerEvent pending() {
        return new AccessManagerEvent(AccessState.PENDING);
    }
}
