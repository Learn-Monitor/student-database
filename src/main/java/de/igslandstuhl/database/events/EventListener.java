package de.igslandstuhl.database.events;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import de.igslandstuhl.database.Registry;

public abstract class EventListener<T extends Event> {
    private static final Registry<EventType<?>, Set<EventListener<?>>> listeners = new Registry<>();
    
    private final ListenerPriority priority;

    public EventListener(ListenerPriority priority) {
        this.priority = priority;
    }
    public abstract void onEvent(T event);
    public abstract EventType<T> getEventType();

    public ListenerPriority getPriority() {
        return priority;
    }
    public void register() {
        listeners.get(getEventType()).add(this);
    }

    public static void register(EventType<?> type) {
        listeners.register(type, new HashSet<>());
    }

    @SuppressWarnings("unchecked")
    private static <T extends Event> Set<EventListener<T>> getListeners(EventType<T> type) {
        return (Set<EventListener<T>>) (Set<?>) listeners.get(type);
    }
    public static <T extends Event> void fireEvent(T event) {
        Set<EventListener<T>> eventListeners = getListeners(EventType.of(event));
        if (eventListeners != null) {
            new LinkedList<>(eventListeners).stream()
                .sorted((a, b) -> a.getPriority().compareTo(b.getPriority()))
                .forEach(listener -> {
                    if (!event.isCancelled()) {
                        listener.onEvent(event);
                        if (event.isCancelled() && listener.getPriority() == ListenerPriority.MONITOR) {
                            throw new IllegalStateException("Event was cancelled but a monitor listener was called.");
                        }
                    }
                });
        }
    }
}
