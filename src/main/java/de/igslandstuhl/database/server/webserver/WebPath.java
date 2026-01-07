package de.igslandstuhl.database.server.webserver;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.server.resources.ResourceHelper;
import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.server.webserver.requests.RequestType;

public record WebPath(RequestType type, String handlerType, List<String> namespaces, String context, AccessLevel accessLevel) {
    public static void registerPath(String path, RequestType type, String handlerType, List<String> namespaces, String context, AccessLevel accessLevel) {
        Registry.webPathRegistry().register(path, new WebPath(type, handlerType, namespaces, context, accessLevel));
    }
    public static void registerPaths() throws IOException {
        ResourceLocation metaLocation = new ResourceLocation("meta", "paths", "get_paths.json");
        Map<String, ?> pathData = ResourceHelper.readJsonResourceAsMap(metaLocation);
        pathData.keySet().forEach((path) -> {
            @SuppressWarnings("unchecked")
            Map<String, ?> pathInfo = (Map<String, ?>) pathData.get(path);
            RequestType requestType = RequestType.valueOf((String) pathInfo.get("type"));
            String handlerType = (String) pathInfo.get("handler_type");
            @SuppressWarnings("unchecked")
            List<String> namespaces = (List<String>) pathInfo.get("namespaces");
            String context = (String) pathInfo.get("context");
            AccessLevel accessLevel = AccessLevel.valueOf(((String) pathInfo.get("access_level")).toUpperCase());
            registerPath(path, requestType, handlerType, namespaces, context, accessLevel);
        });
    }
}
