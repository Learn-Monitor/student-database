package de.igslandstuhl.database.server.webserver;

import de.igslandstuhl.database.server.resources.ResourceLocation;

/**
 * Represents the different content types that can be served by the web server.
 */
public enum ContentType {
    TEXT_PLAIN("text/plain"),
    HTML ("text/html"),
    JAVASCRIPT ("text/javascript"),
    CSS ("text/css"),
    PNG ("image/png"),
    JSON ("text/json"),
    CSV ("text/csv")
    ;
    /**
     * The name of the content type, used in HTTP headers.
     */
    private final String name;
    /**
     * Constructs a ContentType with the specified name.
     * @param name the name of the content type
     */
    private ContentType(String name) {
        this.name = name;
    }
    /**
     * Returns the name of the content type.
     * @return the name of the content type
     */
    public String getName() {
        return name;
    }
    public boolean isText() {
        switch (this) {
            case TEXT_PLAIN:
            case HTML:
            case JAVASCRIPT:
            case CSS:
            case JSON:
                return true;
            default:
                return false;
        }
    }
    /**
     * Returns the ContentType corresponding to the given ResourceLocation.
     * @param l the ResourceLocation to determine the content type for
     * @return the ContentType corresponding to the ResourceLocation
     * @throws NoWebResourceException if the ResourceLocation does not correspond to a known web resource
     */
    public static ContentType ofResourceLocation(ResourceLocation l) throws NoWebResourceException {
        if (l.context().equals("html")) {
            return HTML;
        } else if (l.context().equals("js")) {
            return JAVASCRIPT;
        } else if (l.context().equals("css")) {
            return CSS;
        } else if (l.context().equals("imgs")) {
            if (l.resource().endsWith(".png") || l.resource().endsWith(".ico")) {
                return PNG;
            } else {
                throw new NoWebResourceException(l);
            }
        } else if (l.context().equals("virtual")) {
            // For virtual resources, try to determine content type from resource name
            if (l.resource().endsWith(".json")) {
                return JSON;
            } else {
                // If we can't determine the content type, default to JSON for virtual resources
                return JSON;
            }
        } else {
            // Try to infer content type from file extension
            String resource = l.resource();
            if (resource.endsWith(".js")) {
                return JAVASCRIPT;
            } else if (resource.endsWith(".css")) {
                return CSS;
            } else if (resource.endsWith(".png") || resource.endsWith(".ico")) {
                return PNG;
            } else if (resource.endsWith(".html") || resource.endsWith(".htm")) {
                return HTML;
            } else if (resource.endsWith(".json")) {
                return JSON;
            } else if (resource.endsWith(".csv")) {
                return CSV;
            }
            throw new NoWebResourceException(l);
        }
    }
}
