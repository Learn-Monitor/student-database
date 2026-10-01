package de.igslandstuhl.database.api;

import java.sql.SQLException;

/** Raised when the authoritative transactional deletion check finds dependencies. */
public final class ObjectInUseException extends SQLException {
    private final DeletionPreflight preflight;

    public ObjectInUseException(DeletionPreflight preflight) {
        super("object_in_use");
        this.preflight = preflight;
    }

    public DeletionPreflight preflight() {
        return preflight;
    }
}
