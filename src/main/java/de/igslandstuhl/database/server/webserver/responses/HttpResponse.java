package de.igslandstuhl.database.server.webserver.responses;

import java.io.PrintStream;

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
                out.print("HTTP/1.1 ");errorStatus.write(out);out.println();
                out.print("Content-Type: text/html");
                out.print("; charset=UTF8");
                out.println();
                out.println("Set-Cookie: " + Server.getInstance().getWebServer().getSessionManager().getSession(request).createSessionCookie());
                out.println(); // <--- This line is important: seperates Header and Body!
                ResourceLocation resourceLocation = new ResourceLocation("html", "errors", errorStatus.getCode() + ".html");
                String resource;
                try {
                    resource = Server.getInstance().getResourceManager().readResourceCompletely(resourceLocation);
                    out.println(resource);
                } catch (Exception e) {
                    if (errorStatus != Status.INTERNAL_SERVER_ERROR) {
                        resourceLocation = new ResourceLocation("html", "errors", errorStatus.getCode() + ".html");
                        try {
                            resource = Server.getInstance().getResourceManager().readResourceCompletely(resourceLocation);
                            out.println(resource);
                        } catch (Exception e2) {
                            throw new IllegalStateException(e);
                        }
                    } else {
                        throw new IllegalStateException(e);
                    }
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
                out.print("HTTP/1.1 ");errorStatus.write(out);out.println();
                out.print("Content-Type: text/html");
                out.print("; charset=UTF8");
                out.println();
                out.println("Set-Cookie: " + Server.getInstance().getWebServer().getSessionManager().getSession(request).createSessionCookie());
                out.println(); // <--- This line is important: seperates Header and Body!
                ResourceLocation resourceLocation = new ResourceLocation("html", "errors", errorStatus.getCode() + ".html");
                String resource;
                try {
                    resource = Server.getInstance().getResourceManager().readResourceCompletely(resourceLocation);
                    out.println(resource);
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
}
