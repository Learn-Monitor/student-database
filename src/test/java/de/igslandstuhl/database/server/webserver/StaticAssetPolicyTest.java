package de.igslandstuhl.database.server.webserver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.server.webserver.access.AccessLevel;
import de.igslandstuhl.database.server.webserver.requests.RequestType;

class StaticAssetPolicyTest {
    @Test
    void onlyExplicitlyPublicLoginAndInspirationImagesCanBeSessionless() {
        assertTrue(StaticAssetPolicy.isSessionlessPublicLoginAsset(asset("login", AccessLevel.PUBLIC)));
        assertTrue(StaticAssetPolicy.isSessionlessPublicLoginAsset(asset("inspiration", AccessLevel.PUBLIC)));
        assertFalse(StaticAssetPolicy.isSessionlessPublicLoginAsset(asset("user", AccessLevel.PUBLIC)));
        assertFalse(StaticAssetPolicy.isSessionlessPublicLoginAsset(asset("login", AccessLevel.STUDENT)));
        assertFalse(StaticAssetPolicy.isSessionlessPublicLoginAsset(new WebPath(
                RequestType.GET, "TemplatingFileRequestHandler", List.of("login"), "imgs", AccessLevel.PUBLIC)));
        assertFalse(StaticAssetPolicy.isSessionlessPublicLoginAsset(new WebPath(
                RequestType.POST, "FileRequestHandler", List.of("login"), "imgs", AccessLevel.PUBLIC)));
    }

    @Test
    void immutableCachingRequiresContentHashedWebpFilename() {
        assertTrue(StaticAssetPolicy.isImmutableContentHashedPath("/arcanum-coin-0123456789abcdef.webp"));
        assertFalse(StaticAssetPolicy.isImmutableContentHashedPath("/arcanum-coin.webp"));
        assertFalse(StaticAssetPolicy.isImmutableContentHashedPath("/arcanum-coin-0123456789abcdef.png"));
        assertFalse(StaticAssetPolicy.isImmutableContentHashedPath("/arcanum-coin-0123456789ABCDEf.webp"));
    }

    private static WebPath asset(String namespace, AccessLevel accessLevel) {
        return new WebPath(RequestType.GET, "FileRequestHandler", List.of(namespace), "imgs", accessLevel);
    }
}
