package de.igslandstuhl.database.server.webserver.handlers;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.webserver.AccessLevel;
import de.igslandstuhl.database.server.webserver.Status;
import de.igslandstuhl.database.server.webserver.requests.APIPostRequest;
import de.igslandstuhl.database.server.webserver.requests.GetRequest;
import de.igslandstuhl.database.server.webserver.requests.HttpRequest;
import de.igslandstuhl.database.server.webserver.responses.HttpResponse;
import de.igslandstuhl.database.server.webserver.sessions.SessionManager;
import de.igslandstuhl.database.utils.ThrowingFunction;

public class HttpHandler<Rq extends HttpRequest> {
    private final String path;
    private final AccessLevel accessLevel;
    private final ThrowingFunction<Rq, HttpResponse> handler;

    private HttpHandler(String path, AccessLevel accessLevel, ThrowingFunction<Rq, HttpResponse> handler) {
        this.accessLevel = accessLevel;
        this.handler = handler;
        this.path = path;
    }

    public HttpResponse handleHttpRequest(Rq request) {
        SessionManager sessionManager = Server.getInstance().getWebServer().getSessionManager();
        int contentLength = request.getContentLength();
        if (contentLength <= 0 && !(request instanceof GetRequest)) {
            return HttpResponse.error(request, Status.BAD_REQUEST);
        }
        if (!accessLevel.hasAccess(sessionManager.getSessionUser(request))) {
            return HttpResponse.error(request, Status.UNAUTHORIZED);
        } else if (!path.equals(request.getPath().split("\\?")[0])) {
            System.err.println("Wrong path for HTTP handler: " + handler + ", path: " + request.getPath());
            return HttpResponse.error(request, Status.INTERNAL_SERVER_ERROR);
        } else {
            try {
                return handler.apply(request);
            } catch (Throwable t) {
                t.printStackTrace();
                return HttpResponse.error(request, Status.INTERNAL_SERVER_ERROR);
            }
        }
    }

    public static void registerPostRequestHandler(String path, AccessLevel accessLevel, ThrowingFunction<APIPostRequest, HttpResponse> handler) {
        Registry.postRequestHandlerRegistry().register(path, new HttpHandler<>(path, accessLevel, handler));
    }
    public static void registerGetRequestHandler(String path, AccessLevel accessLevel, ThrowingFunction<GetRequest, HttpResponse> handler) {
        Registry.getRequestHandlerRegistry().register(path, new HttpHandler<>(path, accessLevel, handler));
    }
}
