package de.igslandstuhl.database.events;

/** Fired after a user account is physically deleted and its active sessions are revoked. */
public final class UserAccountDeletedEvent extends Event {
    public static final EventType<UserAccountDeletedEvent> TYPE = new EventType<>("UserAccountDeletedEvent");
    private final String username;

    public UserAccountDeletedEvent(String username) {
        this.username = username;
    }

    public String getUsername() { return username; }

    @Override public EventType<UserAccountDeletedEvent> getType() { return TYPE; }
}
