package de.igslandstuhl.database.server.webserver;

/**
 * Represents a cookie with a name and value.
 * This class is used to store cookie information in HTTP requests and responses.
 */
public class Cookie {
    /**
     * The name of the cookie.
     * This is the identifier for the cookie, used to retrieve its value.
     */
    private final String name;
    /**
     * The value of the cookie.
     * This is the data stored in the cookie, which can be used for session management or other purposes.
     */
    private final String value;

    /**
     * Constructs a new Cookie with the specified name and value.
     *
     * @param name  The name of the cookie.
     * @param value The value of the cookie.
     */
    public Cookie(String name, String value) {
        this.name = name;
        this.value = value;
    }

    /**
     * Parses the value of a Cookie request header.
     *
     * <p>Cookie pairs are separated by semicolons. Each pair is split at
     * the first equals sign so that equals signs inside cookie values are
     * preserved. Invalid pairs and pairs without a name are ignored.</p>
     *
     * @param headerValue the value of the Cookie header
     * @return the parsed cookies, or an empty array for a missing or empty
     *         header value
     */
    public static Cookie[] parse(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return new Cookie[0];
        }

        java.util.List<Cookie> cookies = new java.util.ArrayList<>();

        for (String pair : headerValue.split(";")) {
            String trimmedPair = pair.trim();
            int separator = trimmedPair.indexOf('=');

            if (separator < 0) {
                continue;
            }

            String name = trimmedPair.substring(0, separator).trim();
            String value = trimmedPair.substring(separator + 1).trim();

            if (!name.isEmpty()) {
                cookies.add(new Cookie(name, value));
            }
        }

        return cookies.toArray(new Cookie[0]);
    }

    /**
     * Returns the name of the cookie.
     *
     * @return The name of the cookie.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the value of the cookie.
     *
     * @return The value of the cookie.
     */
    public String getValue() {
        return value;
    }

    public String toString() {
        return name + "=" + value;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Cookie other = (Cookie) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }
    
}
