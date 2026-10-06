package de.igslandstuhl.database;

public record Argument(String key, String value) {
    public Argument(String keyOnly) {
        this(keyOnly, null);
    }
}
