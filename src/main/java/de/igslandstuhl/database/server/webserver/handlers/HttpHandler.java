package de.igslandstuhl.database.server.webserver.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.webserver.Status;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;
import de.igslandstuhl.database.server.webserver.access.AccessManager;
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
        UserAccess access = new UserAccess(sessionManager.getSessionUser(request), request);
        boolean allowed;
        try {
            allowed = access.allowed();
        } catch (Throwable t) {
            LOGGER.error("Failed to evaluate HTTP access for {}", path, t);
            allowed = false;
        }
        if (!allowed) {
            var user = sessionManager.getSessionUser(request);
            return HttpResponse.error(request, user == null || user == de.igslandstuhl.database.api.User.ANONYMOUS
                    ? Status.UNAUTHORIZED : Status.FORBIDDEN);
        } else if (!path.equals(request.getPath().split("\\?")[0])) {
            LOGGER.error("Wrong path for HTTP handler: path {} does not match handler path {}", request.getPath(), path);
            return HttpResponse.error(request, Status.INTERNAL_SERVER_ERROR);
        } else {
            try {
                return handler.apply(request);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Invalid HTTP request for {}: {}", path, e.getMessage());
                return HttpResponse.error(request, Status.BAD_REQUEST);
            } catch (Throwable t) {
                LOGGER.error("Failed to apply HTTP handler", t);
                return HttpResponse.error(request, Status.INTERNAL_SERVER_ERROR);
            }
        }
    }

    private final class UserAccess {
        private final de.igslandstuhl.database.api.User user;
        private final HttpRequest request;
        UserAccess(de.igslandstuhl.database.api.User user, HttpRequest request) { this.user = user; this.request = request; }
        boolean allowed() {
            var webPath = Registry.webPathRegistry().get(de.igslandstuhl.database.server.webserver.WebPath.PathInfo.get(path, request.getRequestType()));
            return webPath == null ? accessLevel.hasAccess(user) : AccessManager.getInstance().hasAccess(user, path, request);
        }
    }

    public static void registerPostRequestHandler(String path, AccessLevel accessLevel, ThrowingFunction<APIPostRequest, HttpResponse> handler) {
        Registry.postRequestHandlerRegistry().register(path, new HttpHandler<>(path, accessLevel, handler));
    }
    public static void registerGetRequestHandler(String path, AccessLevel accessLevel, ThrowingFunction<GetRequest, HttpResponse> handler) {
        Registry.getRequestHandlerRegistry().register(path, new HttpHandler<>(path, accessLevel, handler));
    }
}
