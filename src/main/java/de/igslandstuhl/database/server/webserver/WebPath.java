package de.igslandstuhl.database.server.webserver;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;
import de.igslandstuhl.database.server.webserver.requests.RequestType;

public record WebPath(RequestType type, String handlerType, List<String> namespaces, String context, AccessLevel accessLevel) {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebPath.class);

    public static void registerPath(String path, RequestType type, String handlerType, List<String> namespaces, String context, AccessLevel accessLevel) {
        Registry.webPathRegistry().register(PathInfo.get(path, type), new WebPath(type, handlerType, namespaces, context, accessLevel));
    }
    private static void registerPaths(ResourceLocation metaLocation) throws IOException {
        Map<String, ?> pathData = Server.getInstance().getResourceManager().readJsonResourceMerged(metaLocation);
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
    public static void registerPaths() throws IOException {
        LOGGER.info("Registering get request paths...");
        if (Registry.webPathRegistry().stream().count() > 0) return; // already registered
        ResourceLocation getPathLocation = new ResourceLocation("meta", "paths", "get_paths.json");
        registerPaths(getPathLocation);
        ResourceLocation postPathLocation = new ResourceLocation("meta", "paths", "post_paths.json");
        registerPaths(postPathLocation);
    }

    public static record PathInfo(String path, RequestType type) {
        private static final LinkedList<PathInfo> pathInfos = new LinkedList<>();

        public static PathInfo get(String path, RequestType type) {
            Optional<PathInfo> existing = pathInfos.stream().filter((p) -> p.path().equals(path) && p.type().equals(type)).findAny();
            if (existing.isPresent()) {
                return existing.get();
            } else {
                PathInfo newPathInfo = new PathInfo(path.intern(), type);
                pathInfos.add(newPathInfo);
                return newPathInfo;
            }
        }
    }
}
