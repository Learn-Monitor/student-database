package de.igslandstuhl.database.server.webserver.requests;

import java.util.ArrayList;
import java.util.List;

import de.igslandstuhl.database.server.webserver.Cookie;

public class HttpHeader {
    /**
     * Represents the path of the POST request.
     * This is the part of the request line that specifies the resource being requested.
     */
    private final String path;
    /**
     * Represents the content length of the POST request.
     * This is used to determine the size of the request body.
     */
    private final int contentLength;
    /**
     * Represents the cookies associated with the POST request.
     * This is an array of Cookie objects that may be included in the request.
     */
    private final Cookie[] cookies;
    private final String userAgent;
    private final String acceptLanguage;

    /**
     * Constructs a new PostRequest with the given header and body.
     * @param header the header of the POST request
     * @param body the body of the POST request
     */
    public HttpHeader(String header) {
        // Example: "POST /login HTTP/1.1"
        String[] parts = header.split(" ");
        this.path = parts[1];
        
        int contentLength = 0;
        Cookie[] cookies = new Cookie[0];
        String userAgent = null;
        String acceptLanguage = null;
        for (String line : header.split("\n")) {
            if (line.startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(line.split(":")[1].trim());
            } else if (line.startsWith("Cookie:")) {
                String[] cookieData = line.substring(7).split(";");
                List<Cookie> cookieList = new ArrayList<>();
                for (String cookie : cookieData) {
                    String[] keyValue = cookie.trim().split("=");
                    if (keyValue.length == 2) {
                        cookieList.add(new Cookie(keyValue[0].trim(), keyValue[1].trim()));
                    }
                }
                cookies = cookieList.toArray(new Cookie[0]);
            } else if (line.startsWith("User-Agent:")) {
                userAgent = line.split(":")[1].trim();
            } else if (line.startsWith("Accept-Language:")) {
                acceptLanguage = line.split(":")[1].trim();
            }
        }
        this.cookies = cookies;
        this.contentLength = contentLength;
        this.userAgent = userAgent;
        this.acceptLanguage = acceptLanguage;
    }

    /**
     * Returns the path of the POST request.
     * This is the part of the request line that specifies the resource being requested.
     * @return the path of the POST request
     */
    public String getPath() {
        return path;
    }
    /**
     * Returns the content length of the POST request.
     * This is used to determine the size of the request body.
     * @return the content length of the POST request
     */
    public int getContentLength() {
        return contentLength;
    }

    public Cookie[] getCookies() {
        return cookies;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getAcceptLanguage() {
        return acceptLanguage;
    }
}
