package de.igslandstuhl.database.server.webserver.requests;

import de.igslandstuhl.database.server.webserver.Cookie;

public interface HttpRequest {
     /**
     * Returns the path of the HTTP request.
     * This is the part of the request line that specifies the resource being requested.
     * @return the path of the HTTP request
     */
    public String getPath();
    /**
     * Returns the context of the HTTP request.
     * This is derived from the path, typically indicating the type of resource (e.g., "html", "json").
     * @return the context of the HTTP request
     */
    public String getContext();
    /**
     * Returns the content length of the HTTP request.
     * This is used to determine the size of the request body.
     * @return the content length of the HTTP request
     */
    public int getContentLength();
    /**
     * Returns the cookies already set in the browser.
     * This is used for session storage for example.
     * @return the cookies
     */
    public Cookie[] getCookies();
    public String getIP();
    public String getUserAgent();
    public String getAcceptLanguage();
    public boolean isSecureConnection();
    /**
     * Returns the type of the HTTP request (GET, POST, etc.).
     * @return the type of the HTTP request as RequestType enum
     * @see de.igslandstuhl.database.server.webserver.requests.RequestType
     */
    public RequestType getRequestType();
}
