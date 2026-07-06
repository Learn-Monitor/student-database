package de.igslandstuhl.database.events;

@FunctionalInterface
public interface EventFilter<T extends Event> {
    public boolean filter(T event);
    @SafeVarargs
    public static <T extends Event> EventFilter<T> linked(EventFilter<T>... filters) {
        return event -> {
            for (EventFilter<T> filter : filters) {
                if (!filter.filter(event)) {
                    return false;
                }
            }
            return true;
        };
    }
}
