package de.igslandstuhl.database.server.webserver.responses;

import java.io.FileNotFoundException;
import java.io.PrintStream;

import com.google.gson.Gson;

import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.server.webserver.AccessManager;
import de.igslandstuhl.database.server.webserver.ContentType;
import de.igslandstuhl.database.server.webserver.Cookie;
import de.igslandstuhl.database.server.webserver.NoWebResourceException;
import de.igslandstuhl.database.server.webserver.Status;
import de.igslandstuhl.database.server.webserver.requests.HttpRequest;
import de.igslandstuhl.database.server.webserver.requests.PostRequest;

/**
 * Represents a response to a POST request in the web server.
 */
public class PostResponse implements HttpResponse {
    /**
     * The HTTP status code of this response.
     * This code indicates the result of processing the POST request.
     * For example, 200 for success, 400 for bad request, etc.
     * @see Status
     */
    private final Status statusCode;
    /**
     * The body of the response, which contains the content to be sent back to the client.
     * This can be HTML, JSON, plain text, etc., depending on the request and response type.
     */
    private final String body;
    /**
     * The content type of the response, indicating the type of data being sent back.
     * This is used by the client to correctly interpret the response body.
     * @see ContentType
     */
    private final ContentType contentType;
    /**
     * The cookie to be set in the response, if any.
     * This is used for session management or other purposes where cookies are required.
     * If no cookie is needed, this can be null.
     * @see Cookie
     */
    private final Cookie cookie;

    private final String[] headers;

    private final PostRequest request;

    /**
     * Constructs a PostResponse with the given status code, body, and content type.
     * This constructor is used when no cookie is needed in the response.
     *
     * @param statusCode The HTTP status code of the response.
     * @param body The body of the response.
     * @param contentType The content type of the response.
     */
    private PostResponse(Status statusCode, String body, ContentType contentType, PostRequest request) {
        this(statusCode, body, contentType, request, Server.getInstance().getWebServer().getSessionManager().getSession(request).createSessionCookie());
    }
    /**
     * Constructs a PostResponse with the given status code, body, content type, and cookie.
     * This constructor is used when a cookie needs to be set in the response.
     *
     * @param statusCode The HTTP status code of the response.
     * @param body The body of the response.
     * @param contentType The content type of the response.
     * @param cookie The cookie to be set in the response, or null if no cookie is needed.
     */
    private PostResponse(Status statusCode, String body, ContentType contentType, PostRequest request, Cookie cookie) {
        this.statusCode = statusCode;
        this.body = body;
        this.contentType = contentType;
        this.cookie = cookie;
        this.headers = new String[0]; // Initialize headers as an empty array
        this.request = request;
    }
    /**
     * Constructs a PostResponse with the given status code, body, content type, and headers.
     * This constructor is used when additional headers need to be included in the response.
     *
     * @param statusCode The HTTP status code of the response.
     * @param body The body of the response.
     * @param contentType The content type of the response.
     * @param headers Additional headers to be included in the response.
     */
    private PostResponse(Status statusCode, String body, ContentType contentType, PostRequest request, String[] headers) {
        this.statusCode = statusCode;
        this.body = body;
        this.contentType = contentType;
        this.cookie = null; // No cookie in this constructor
        this.request = request;
        this.headers = headers != null ? headers : new String[0]; // Initialize headers, ensuring it's not null
    }

    /**
     * Responds to the POST request by writing the response to the provided PrintWriter.
     * This method formats the response according to HTTP standards, including status code,
     * content type, and any cookies.
     *
     * @param out The PrintWriter to write the response to.
     */
    public void respond(PrintStream out) {
        out.print("HTTP/1.1 ");
        statusCode.write(out);
        out.print("\r\n");
        out.print("Content-Type: " + contentType + "; charset=UTF-8\r\n");
        if (cookie != null) {
            out.print("Set-Cookie: " + cookie + "; HttpOnly; Secure\r\n");
        }
        for (String header : headers) {
            out.print(header + "\r\n");
        }
        out.print("\r\n");
        if (body != null) {
            out.print(body);
        }
        out.flush();
    }

    public PostRequest getRequest() {
        return request;
    }

    /**
     * Returns a successful response for a POST request with the given body and content type.
     * This is used when the request is processed successfully and a response body is needed.
     *
     * @param body The body of the response.
     * @param contentType The content type of the response.
     * @return A PostResponse object representing the successful response.
     */
    public static PostResponse ok(String body, ContentType contentType, PostRequest request) {
        return new PostResponse(Status.OK, body, contentType, request);
    }
    /**
     * Returns a successful response for a POST request with the given body, content type, and cookie.
     * This is used when the request is processed successfully and a response body and cookie are needed.
     *
     * @param body The body of the response.
     * @param contentType The content type of the response.
     * @param cookie The cookie to be set in the response, or null if no cookie is needed.
     * @return A PostResponse object representing the successful response with a cookie.
     */
    public static PostResponse ok(String body, ContentType contentType, PostRequest request, Cookie cookie) {
        return new PostResponse(Status.OK, body, contentType, request, cookie);
    }
    /**
     * Returns a response for a POST request for the given resource that can be accessed via GET (for example, a teacher can do a post request to student GET data with a specific ID).
     * @param resourceLocation the location of the resource to be returned
     * @param user the user who made the request
     * @return the PostResponse object
     */
    public static PostResponse getResource(ResourceLocation resourceLocation, String user, PostRequest request) {
        try {
            if (AccessManager.getInstance().hasAccess(user, resourceLocation)) {
                return new PostResponse(Status.OK, GetResponse.getResource(request, resourceLocation, user, false).getResponseBody(), ContentType.ofResourceLocation(resourceLocation), request);
            } else {
                return unauthorized("You have to be logged in to access this resource.", request);
            }
        } catch (NoWebResourceException e) {
            return forbidden("You do not have permission to access this resource.", request);
        } catch (FileNotFoundException e) {
            return notFound("The requested resource was not found: " + resourceLocation, request);
        } catch (Exception e) {
            e.printStackTrace();
            return internalServerError("An error occurred while processing your request.", request);
        }
    }

    /**
     * Returns a response indicating a bad request.
     * This is used when the request cannot be processed due to client error, such as malformed syntax or invalid request parameters.
     * @param message The error message to include in the response.
     * @return A PostResponse object representing the bad request response.
     */
    public static PostResponse badRequest(String message, PostRequest request) {
        return new PostResponse(Status.BAD_REQUEST, message, ContentType.TEXT_PLAIN, request);
    }

    /**
     * Returns a response indicating that the user is unauthorized to access the requested resource.
     * This is used when the user is not authenticated or does not have permission to access the resource.
     * @param message The error message to include in the response.
     * @return A PostResponse object representing the unauthorized response.
     */
    public static PostResponse unauthorized(String message, PostRequest request) {
        return new PostResponse(Status.UNAUTHORIZED, message, ContentType.TEXT_PLAIN, request);
    }
    /**
     * Returns a response indicating that the user is unauthorized to access the requested resource.
     * This is used when the user is not authenticated or does not have permission to access the resource.
     * @return A PostResponse object representing the unauthorized response.
     */
    public static PostResponse unauthorized(PostRequest rq) {
        return unauthorized("Not logged in or invalid session", rq);
    }

    /**
     * Returns a response indicating that the server encountered an internal error while processing the request.
     * This is used when an unexpected condition prevents the server from fulfilling the request.
     * @param message The error message to include in the response.
     * @return A PostResponse object representing the internal server error response.
     */
    public static PostResponse internalServerError(String message, PostRequest request) {
        return new PostResponse(Status.INTERNAL_SERVER_ERROR, message, ContentType.TEXT_PLAIN, request);
    }

    /**
     * Returns a response indicating that the requested resource was not found.
     * This is used when the server cannot find the requested resource, such as a missing file or endpoint.
     * @param message The error message to include in the response.
     * @return A PostResponse object representing the not found response.
     */
    public static PostResponse notFound(String message, PostRequest request) {
        return new PostResponse(Status.NOT_FOUND, message, ContentType.TEXT_PLAIN, request);
    }

    /**
     * Returns a response indicating that the user does not have permission to access the requested resource.
     * This is used when the user is authenticated but does not have the necessary permissions for the resource.
     * @param message The error message to include in the response.
     * @return A PostResponse object representing the forbidden response.
     */
    public static PostResponse forbidden(String message, PostRequest request) {
        return new PostResponse(Status.FORBIDDEN, message, ContentType.TEXT_PLAIN, request);
    }

    /**
     * Returns a response indicating that the client has sent too many requests in a given amount of time.
     * This is used for rate limiting.
     * @param message The error message to include in the response.
     * @return A PostResponse object representing the too many requests response.
     */
    public static PostResponse tooManyRequests(String message, PostRequest request) {
        return new PostResponse(Status.TOO_MANY_REQUESTS, message, ContentType.TEXT_PLAIN, request);
    }

    /**
     * Returns a response indicating that the client has sent too many requests in a given amount of time.
     * This is used for rate limiting.
     * @return A PostResponse object representing the too many requests response.
     */
    public static PostResponse tooManyRequests(PostRequest rq) {
        return tooManyRequests("Too Many Requests", rq);
    }

    public static PostResponse redirect(String location, PostRequest request) {
        return new PostResponse(Status.FOUND, "", ContentType.TEXT_PLAIN, request, new String[] {
            "Location: " + location
        });
    }
    public static PostResponse json(Object json, PostRequest request) {
        Gson gson = new Gson();
        return new PostResponse(Status.OK, gson.toJson(json), ContentType.JSON, request);
    }
    @Override
    public Status getStatus() {
        return statusCode;
    }
    @Override
    public HttpRequest getHttpRequest() {
        return request;
    }
    @Override
    public ContentType getContentType() {
        return contentType;
    }
}