package de.igslandstuhl.database.server.webserver.access;

/**
 * AccessState represents the different states of access a user can have to a resource.
 */
public enum AccessState {
    /**
     * The user is not logged in and does not have access to the resource.
     */
    UNAUTHORIZED,
    /**
     * The user is logged in and has access to the resource.
     */
    AUTHORIZED,
    /**
     * The user is logged in but does not have access to the resource.
     */
    RESTRICTED,
    /**
     * The user is logged in and has requested access to the resource.
     */
    PENDING,
    /**
     * The user has been granted access to the resource, regardless of their login state.
     */
    PERMITTED
}
