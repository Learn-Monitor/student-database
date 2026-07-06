package de.igslandstuhl.database.server.webserver.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.webserver.Status;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;
import de.igslandstuhl.database.server.webserver.requests.APIPostRequest;
import de.igslandstuhl.database.server.webserver.requests.GetRequest;
import de.igslandstuhl.database.server.webserver.requests.HttpRequest;
import de.igslandstuhl.database.server.webserver.responses.HttpResponse;
import de.igslandstuhl.database.server.webserver.sessions.SessionManager;
import de.igslandstuhl.database.utils.ThrowingFunction;

public class HttpHandler<Rq extends HttpRequest> {
    public static final Logger LOGGER = LoggerFactory.getLogger(HttpHandler.class);
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
            LOGGER.error("Wrong path for HTTP handler: path {} does not match handler path {}", request.getPath(), path);
            return HttpResponse.error(request, Status.INTERNAL_SERVER_ERROR);
        } else {
            try {
                return handler.apply(request);
            } catch (Throwable t) {
                LOGGER.error("Failed to apply HTTP handler", t);
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
