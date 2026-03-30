package de.igslandstuhl.database.server.webserver.handlers.get;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.server.webserver.requests.GetRequest;
import de.igslandstuhl.database.server.webserver.responses.GetResponse;

public class ModuleRequestHandler {
    public static GetResponse handleRequest(User user, GetRequest request) {
        if (user == null || !user.isAdmin()) return GetResponse.unauthorized(request);
        if (!request.getPath().equals("/module-list")) return GetResponse.notFound(request);

        return GetResponse.getResource(request, new ResourceLocation("virtual", "module", "list"), user.getUsername(), false);
    }
    public static String getModuleResource(String resource) {
        if (resource.equals("list")) {
            return "[" +
                Registry.moduleRegistry().keyStream()
                .reduce("", (s1, s2) -> s1 + ", '" + s2 + "'")
                .substring(2)
                + "]";
        } else {
            throw new NullPointerException("Module Resource not found: " + resource);
        }
    }
}
