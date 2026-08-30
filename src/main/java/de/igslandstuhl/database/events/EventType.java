package de.igslandstuhl.database.events;

public record EventType<T extends Event>(String name) {
    @SuppressWarnings("unchecked")
    public static <T extends Event> EventType<T> of(T event) {
        return (EventType<T>) event.getType();
    }
}
