package de.igslandstuhl.database.server.webserver.handlers;

import java.util.List;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.webserver.WebPath;
import de.igslandstuhl.database.server.webserver.requests.GetRequest;
import de.igslandstuhl.database.server.webserver.requests.RequestType;
import de.igslandstuhl.database.server.webserver.responses.GetResponse;
import de.igslandstuhl.database.server.webserver.responses.HttpResponse;
import de.igslandstuhl.database.server.webserver.sessions.SessionManager;
import de.igslandstuhl.database.utils.ThrowingFunction;

public class GetRequestHandler {
    private static final GetRequestHandler instance = new GetRequestHandler();
    public static GetRequestHandler getInstance() {
        return instance;
    }
    private GetRequestHandler() {}

    public final HttpResponse handleRequest(GetRequest request) {
        SessionManager sessionManager = Server.getInstance().getWebServer().getSessionManager();
        if (!sessionManager.validateSession(request)) {
            return GetResponse.forbidden(request);
        } else {
            String path = request.getPath();
            HttpHandler<GetRequest> handler = Registry.getRequestHandlerRegistry().get(path);
            return handler.handleHttpRequest(request);
        }
    }

    public static GetResponse handleFileRequest(GetRequest request) {
        String user = Server.getInstance().getWebServer().getSessionManager().getSessionUser(request).getUsername();
        return GetResponse.getResource(request, request.toResourceLocation(user), user, false);
    }
    public static GetResponse handleTemplatingFileRequest(GetRequest request) {
        String user = Server.getInstance().getWebServer().getSessionManager().getSessionUser(request).getUsername();
        return GetResponse.getResource(request, request.toResourceLocation(user), user, true);
    }
    public static GetResponse handleSQLRequest(GetRequest request) {
        String user = Server.getInstance().getWebServer().getSessionManager().getSessionUser(request).getUsername();
        return GetResponse.getResource(request, request.toResourceLocation(user), user, false);
    }

    public final void registerHandlers() {
        if (Registry.getRequestHandlerRegistry().stream().count() > 0) return; // already registered
        List<String> getPaths = Registry.webPathRegistry().keyStream().filter((p) -> Registry.webPathRegistry().get(p).type() == RequestType.GET).toList();
        for (String path : getPaths) {
            WebPath webPath = Registry.webPathRegistry().get(path);
            ThrowingFunction<GetRequest, HttpResponse> handlerFunction = switch (webPath.handlerType()) {
                case "FileRequestHandler" -> GetRequestHandler::handleFileRequest;
                case "TemplatingFileRequestHandler" -> GetRequestHandler::handleTemplatingFileRequest;
                case "SQLRequestHandler" -> GetRequestHandler::handleSQLRequest;
                default -> throw new IllegalArgumentException("Unknown handler type: " + webPath.handlerType());
            };
            HttpHandler.registerGetRequestHandler(path, webPath.accessLevel(), handlerFunction);
        }
    }
}
