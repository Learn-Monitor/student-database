package de.igslandstuhl.database.server.webserver.access;

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
     * The user is logged in and has been granted access to the resource.
     */
    PERMITTED
}
