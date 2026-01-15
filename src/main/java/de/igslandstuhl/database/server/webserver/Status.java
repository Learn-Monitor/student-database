package de.igslandstuhl.database.server.webserver;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Represents the various HTTP response statuses.
 */
public enum Status {
    OK (200, "OK"),
    FOUND(302, "Found"),
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN (403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    I_AM_A_TEAPOT(418, "I'm a teapot"),
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
    TOO_MANY_REQUESTS(429, "Too Many Requests")
    ;
    /**
     * The HTTP status code.
     */
    private final int code;
    /**
     * The name of the HTTP status.
     */
    private final String name;
    /**
     * Constructs a new Status with the given code and name.
     * @param code the HTTP status code
     * @param name the name of the HTTP status
     */
    private Status(int code, String name) {
        this.code = code;
        this.name = name;
    }
    /**
     * Returns the HTTP status code.
     * This code is used in the HTTP response header to indicate the status of the request.
     * @return the HTTP status code
     * see #getName()
     */
    public int getCode() {
        return code;
    }
    /**
     * Returns the name of the HTTP status.
     * @return the name of the HTTP status
     * @see #getCode()
     */
    public String getMessage() {
        return name;
    }
    /**
     * Writes the HTTP status code and name to the given PrintWriter.
     * This is used to format the HTTP response header.
     * @param out the PrintWriter to write to
     */
    public void write(PrintWriter out) {
        out.print(code);out.print(" ");out.print(name);
    }
    /**
     * Writes the HTTP status code and name to the given PrintStream.
     * This is used to format the HTTP response header.
     * @param out the PrintWriter to write to
     */
    public void write(PrintStream out) {
        out.print(code);out.print(" ");out.print(name);
    }
}