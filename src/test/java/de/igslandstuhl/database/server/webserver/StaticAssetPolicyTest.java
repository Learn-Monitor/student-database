package de.igslandstuhl.database.server.webserver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;
import de.igslandstuhl.database.server.webserver.requests.RequestType;

class StaticAssetPolicyTest {
    private static final String HASHED_LOGIN_PATH = "/arcanum-coin-0123456789abcdef.webp";
    private static final String UNHASHED_LOGIN_PATH = "/arcanum-coin.webp";
    private static final String OTHER_STATIC_PATH = "/unrelated.webp";

    @BeforeAll
    static void registerPolicyRoutes() {
        WebPath.registerPath(HASHED_LOGIN_PATH, RequestType.GET, "FileRequestHandler",
                List.of("login"), "imgs", AccessLevel.PUBLIC);
        WebPath.registerPath(UNHASHED_LOGIN_PATH, RequestType.GET, "FileRequestHandler",
                List.of("login"), "imgs", AccessLevel.PUBLIC);
        WebPath.registerPath(OTHER_STATIC_PATH, RequestType.GET, "FileRequestHandler",
                List.of("student"), "imgs", AccessLevel.STUDENT);
    }

    @AfterAll
    static void unregisterPolicyRoutes() {
        Registry.webPathRegistry().unregister(WebPath.PathInfo.get(HASHED_LOGIN_PATH, RequestType.GET));
        Registry.webPathRegistry().unregister(WebPath.PathInfo.get(UNHASHED_LOGIN_PATH, RequestType.GET));
        Registry.webPathRegistry().unregister(WebPath.PathInfo.get(OTHER_STATIC_PATH, RequestType.GET));
    }

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

    @Test
    void publicHashedLoginAssetGetsImmutableCachingWithoutSessionCookie() {
        WebPath loginAsset = asset("login", AccessLevel.PUBLIC);

        assertFalse(StaticAssetPolicy.shouldSetSessionCookie(loginAsset));
        assertEquals("public, max-age=31536000, immutable",
                StaticAssetPolicy.cacheControlHeader(loginAsset, HASHED_LOGIN_PATH, Status.OK));
        assertNull(StaticAssetPolicy.cacheControlHeader(loginAsset, HASHED_LOGIN_PATH, Status.NOT_FOUND));
        assertFalse(StaticAssetPolicy.shouldSetSessionCookie(HASHED_LOGIN_PATH));
        assertEquals("public, max-age=31536000, immutable",
                StaticAssetPolicy.cacheControlHeader(HASHED_LOGIN_PATH, Status.OK));
        assertNull(StaticAssetPolicy.cacheControlHeader(HASHED_LOGIN_PATH, Status.NOT_FOUND));
    }

    @Test
    void otherAssetsKeepSessionCookieAndDoNotGetImmutableCaching() {
        WebPath userAsset = asset("user", AccessLevel.PUBLIC);
        WebPath unhashedLoginAsset = asset("login", AccessLevel.PUBLIC);

        assertTrue(StaticAssetPolicy.shouldSetSessionCookie(userAsset));
        assertNull(StaticAssetPolicy.cacheControlHeader(
                userAsset, "/arcanum-coin-0123456789abcdef.webp", Status.OK));
        assertNull(StaticAssetPolicy.cacheControlHeader(
                unhashedLoginAsset, "/arcanum-coin.webp", Status.OK));
        assertTrue(StaticAssetPolicy.shouldSetSessionCookie(OTHER_STATIC_PATH));
        assertNull(StaticAssetPolicy.cacheControlHeader(OTHER_STATIC_PATH, Status.OK));
        assertFalse(StaticAssetPolicy.shouldSetSessionCookie(UNHASHED_LOGIN_PATH));
        assertNull(StaticAssetPolicy.cacheControlHeader(UNHASHED_LOGIN_PATH, Status.OK));
    }

    private static WebPath asset(String namespace, AccessLevel accessLevel) {
        return new WebPath(RequestType.GET, "FileRequestHandler", List.of(namespace), "imgs", accessLevel);
    }
}
