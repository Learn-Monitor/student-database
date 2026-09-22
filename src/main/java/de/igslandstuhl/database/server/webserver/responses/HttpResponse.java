package de.igslandstuhl.database.server.webserver.responses;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.server.webserver.ContentType;
import de.igslandstuhl.database.server.webserver.Status;
import de.igslandstuhl.database.server.webserver.requests.HttpRequest;

public interface HttpResponse {
    public Status getStatus();
    public HttpRequest getHttpRequest();
    public ContentType getContentType();
    public void respond(PrintStream out);

    public static HttpResponse error(HttpRequest request, Status errorStatus) {
        return new HttpResponse() {
            @Override
            public Status getStatus() {
                return errorStatus;
            }
            @Override
            public HttpRequest getHttpRequest() {
                return request;
            }
            @Override
            public void respond(PrintStream out) {
                try {
                    respondError(out, request, errorStatus);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            @Override
            public ContentType getContentType() {
                return ContentType.HTML;
            }
            
        };
    }
    public static HttpResponse internalServerError(HttpRequest request, Throwable cause) {
        Status errorStatus = Status.INTERNAL_SERVER_ERROR;
        return new HttpResponse() {
            @Override
            public Status getStatus() {
                return errorStatus;
            }
            @Override
            public HttpRequest getHttpRequest() {
                return request;
            }
            @Override
            public void respond(PrintStream out) {
                try {
                    respondError(out, request, errorStatus);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            @Override
            public ContentType getContentType() {
                return ContentType.HTML;
            }
            
        };
    }

    private static void respondError(PrintStream out, HttpRequest request, Status status) throws Exception {
        ResourceLocation resourceLocation = new ResourceLocation("html", "errors", status.getCode() + ".html");
        byte[] body = (Server.getInstance().getResourceManager().readResourceCompletely(resourceLocation)
                + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        out.print("HTTP/1.1 "); status.write(out); out.println();
        out.println("Connection: close");
        out.println("Content-Type: text/html; charset=UTF8");
        out.println("Content-Length: " + body.length);
        out.println("Set-Cookie: " + Server.getInstance().getWebServer().getSessionManager().getSession(request).createSessionCookie());
        out.println();
        out.write(body);
        out.flush();
    }
}
