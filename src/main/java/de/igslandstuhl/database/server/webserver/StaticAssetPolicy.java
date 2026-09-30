package de.igslandstuhl.database.server.webserver;

import java.util.regex.Pattern;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;
import de.igslandstuhl.database.server.webserver.requests.RequestType;

/** Narrow policy for explicitly public login artwork that does not need a session. */
public final class StaticAssetPolicy {
    private static final Pattern CONTENT_HASHED_FILENAME = Pattern.compile(".*-[a-f0-9]{16}\\.webp");

    private StaticAssetPolicy() {}

    public static boolean isSessionlessPublicLoginAsset(String path) {
        WebPath webPath = Registry.webPathRegistry().get(WebPath.PathInfo.get(path, RequestType.GET));
        return isSessionlessPublicLoginAsset(webPath);
    }

    static boolean isSessionlessPublicLoginAsset(WebPath webPath) {
        return webPath != null
                && webPath.type() == RequestType.GET
                && "FileRequestHandler".equals(webPath.handlerType())
                && "imgs".equals(webPath.context())
                && webPath.accessLevel() == AccessLevel.PUBLIC
                && (webPath.namespaces().contains("login") || webPath.namespaces().contains("inspiration"));
    }

    public static boolean isImmutablePublicLoginAsset(String path) {
        return isSessionlessPublicLoginAsset(path)
                && isImmutableContentHashedPath(path);
    }

    static boolean isImmutableContentHashedPath(String path) {
        return path != null && CONTENT_HASHED_FILENAME.matcher(path).matches();
    }
}
