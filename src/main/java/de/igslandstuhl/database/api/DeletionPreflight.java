package de.igslandstuhl.database.api;

import java.util.List;

/** User-facing, read-only summary of references that prevent safe deletion. */
public record DeletionPreflight(boolean exists, boolean deletable, long totalReferences,
                                List<ReferenceGroup> groups, String protectedReason) {
    public DeletionPreflight {
        groups = List.copyOf(groups);
    }

    public record ReferenceGroup(String key, String label, long count) {}
}
