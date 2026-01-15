package de.igslandstuhl.database.server.webserver.responses;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.PrintStream;

import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.resources.ResourceHelper;
import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.server.webserver.AccessManager;
import de.igslandstuhl.database.server.webserver.ContentType;
import de.igslandstuhl.database.server.webserver.NoWebResourceException;
import de.igslandstuhl.database.server.webserver.Status;
import de.igslandstuhl.database.server.webserver.requests.HttpRequest;

/**
 * Represents a response to a GET request in the web server.
 */
public class GetResponse implements HttpResponse {
    /**
     * Returns a response for a GET request that was not found.
     * @return the GetResponse object
     */
    public static GetResponse notFound(HttpRequest request) {
        return new GetResponse(request, Status.NOT_FOUND, new ResourceLocation("html", "errors", "404.html"), ContentType.HTML, "", false);
    }
    /**
     * Returns a response for a GET request that resulted in an internal error.
     * @return the GetResponse object
     */
    public static GetResponse internalServerError(HttpRequest request) {
        return new GetResponse(request, Status.INTERNAL_SERVER_ERROR, new ResourceLocation("html", "errors", "500.html"), ContentType.HTML, "", false);
    }
    /**
     * Returns a response for a GET request the user has no access to.
     * @return the HttpRequest object
     */
    public static GetResponse forbidden(HttpRequest request) {
        return new GetResponse(request, Status.FORBIDDEN, new ResourceLocation("html", "errors", "403.html"), ContentType.HTML, "", false);
    }
    /**
     * Returns a response for a GET request the user must be logged in for.
     * @return the GetResponse object
     */
    public static GetResponse unauthorized(HttpRequest request) {
        return new GetResponse(request, Status.UNAUTHORIZED, new ResourceLocation("html", "errors", "401.html"), ContentType.HTML, "", false);
    }
    /**
     * Returns a response for a GET request the user must be logged in for.
     * This overload allows specifying a custom message (e.g. "Invalid Session") without leaking details.
     * @param message The message to include in the response.
     * @return the GetResponse object
     */
    public static GetResponse unauthorized(String message, HttpRequest request) {
        return new GetResponse(
                request,
                Status.UNAUTHORIZED,
                new ResourceLocation("html", "errors", "401.html"),
                ContentType.HTML,
                message,
                false
        );
    }
    /**
     * Returns a response indicating that the client has sent too many requests in a given amount of time.
     * This is used for rate limiting.
     * @param message The message to include in the response.
     * @return the GetResponse object
     */
    public static GetResponse tooManyRequests(String message, HttpRequest request) {
        return new GetResponse(
                request,
                Status.TOO_MANY_REQUESTS,
                new ResourceLocation("html", "errors", "429.html"),
                ContentType.HTML,
                message,
                false
        );
    }

    /**
     * Returns a response indicating that the client has sent too many requests in a given amount of time.
     * This is used for rate limiting.
     * @return the GetResponse object
     */
    public static GetResponse tooManyRequests(HttpRequest request) {
        return tooManyRequests("", request);
    }

    /**
     * The HTTP status of this response
     * @see Status
     */
    private final Status status;
    /**
     * The location of the resource to be returned.
     */
    private final ResourceLocation resourceLocation;
    /**
     * The HTTP content type of the resource.
     * @see ContentType
     */
    private final ContentType contentType;
    /**
     * The character set used for the response.
     * This is always UTF-8.
     */
    private final String charset = "UTF-8";
    /**
     * The user who made the request.
     * This is used to check access permissions.
     */
    private final String user;
    private final HttpRequest request;

    private final boolean isTemplating;

    /**
     * Creates a new GetResponse with the given parameters.
     * This constructor is used to create a response for a GET request.
     * @param status the HTTP status of the response
     * @param resourceLocation the location of the resource to be returned
     * @param contentType the HTTP content type of the resource
     * @param user the user who made the request
     * @param isTemplating whether the resource is a templated resource
     */
    public GetResponse(HttpRequest request, Status status, ResourceLocation resourceLocation, ContentType contentType, String user, boolean isTemplating) {
        this.status = status;
        this.resourceLocation = resourceLocation;
        this.contentType = contentType;
        this.user = user;
        this.request = request;
        this.isTemplating = isTemplating;
    }
    /**
     * Returns a response for a GET request for the given resource.
     * @param resourceLocation the location of the resource to be returned
     * @param user the user who made the request
     * @return the GetResponse object
     */
    public static GetResponse getResource(HttpRequest request, ResourceLocation resourceLocation, String user, boolean isTemplating) {
        try {
            if (AccessManager.getInstance().hasAccess(user, resourceLocation)) {
                return new GetResponse(request, Status.OK, resourceLocation, ContentType.ofResourceLocation(resourceLocation), user, isTemplating);
            } else {
                return unauthorized(request);
            }
        } catch (NoWebResourceException e) {
            return forbidden(request);
        }
    }

    /**
     * Responds to the GET request by writing the response to the given PrintWriter.
     * This method formats the HTTP response header and body, including the status, content type, and resource.
     * @param out the PrintWriter to write the response to
     */
    public void respond(PrintStream out) {
        try {
            out.print("HTTP/1.1 "); status.write(out); out.println();
            if (contentType != null) {
                out.print("Content-Type: "); out.print(contentType.getName());
                if (contentType.isText()) {
                    out.print("; charset=");out.print(charset);
                }
                out.println();
                out.println("Set-Cookie: " + Server.getInstance().getWebServer().getSessionManager().getSession(request).createSessionCookie());
            }
            out.println(); // <--- This line is important: seperates Header and Body!
            if (contentType.isText()) {
                String resource = "";
                if (resourceLocation != null) {
                    if (!resourceLocation.isVirtual()) {
                        resource = ResourceHelper.readResourceCompletely(resourceLocation);
                    } else {
                        resource = ResourceHelper.readVirtualResource(user, resourceLocation);
                        if (resource == null) throw new NullPointerException();
                    }
                }
                if (isTemplating) {
                    resource = TemplatingPreprocessor.getInstance().executeTemplating(resource);
                }
                out.println(resource);
            } else {
                try (InputStream in = ResourceHelper.openResourceAsStream(resourceLocation)) {
                    in.transferTo(out); // Streams bytes directly
                }
            }
        } catch (FileNotFoundException e) {
            notFound(request).respond(out);
        } catch (Exception e) {
            e.printStackTrace();
            if (status != Status.INTERNAL_SERVER_ERROR) {
                internalServerError(request).respond(out);
            } else {
                throw new IllegalStateException("Uncaught exception", e);
            }
        }
    }

    public String getResponseBody() throws FileNotFoundException {
        if (resourceLocation != null) {
            if (!resourceLocation.isVirtual()) {
                return ResourceHelper.readResourceCompletely(resourceLocation);
            } else {
                return ResourceHelper.readVirtualResource(user, resourceLocation);
            }
        }
        return "";
    }
    @Override
    public Status getStatus() {
        return status;
    }
    @Override
    public ContentType getContentType() {
        return contentType;
    }
    @Override
    public HttpRequest getHttpRequest() {
        return request;
    }
}
