package de.igslandstuhl.database.events;

public abstract class Event {
    private boolean cancelled = false;

    public abstract EventType<? extends Event> getType();
    protected void onCancel() {
        // Override this method to handle cancellation logic in subclasses
    }

    public boolean isCancelled() {
        return this.cancelled;
    }
    public void cancel() {
        this.cancelled = true;
        onCancel();
    }
}
