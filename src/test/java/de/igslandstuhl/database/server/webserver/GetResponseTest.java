package de.igslandstuhl.database.server.webserver;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.server.webserver.requests.GetRequest;
import de.igslandstuhl.database.server.webserver.responses.GetResponse;

public class GetResponseTest {
    GetRequest request = new GetRequest("GET / HTTP/1.1", "127.0.0.1", true);
    @Test
    void testForbidden() throws FileNotFoundException {
        assertTrue(GetResponse.forbidden(request).getResponseBody().contains("403"));
    }

    @Test
    void testInternalServerError() throws FileNotFoundException {
        assertTrue(GetResponse.internalServerError(request).getResponseBody().contains("500"));
    }

    @Test
    void testNotFound() throws FileNotFoundException {
        assertTrue(GetResponse.notFound(request).getResponseBody().contains("404"));
    }

    @Test
    void testUnauthorized() throws FileNotFoundException {
        assertTrue(GetResponse.unauthorized(request).getResponseBody().contains("401"));
    }

    @Test
    void testGetResource() throws FileNotFoundException {
        assertTrue(GetResponse.getResource(request, ResourceLocation.get("html", "site:login.html"), null, false).getResponseBody().contains("login"));
    }

    @Test
    void testGetResponseBody() {
        assertNotNull(GetResponse.getResource(request, ResourceLocation.get("html", "site:login.html"), null, false));
    }

    @Test
    void testRespond() throws FileNotFoundException {
        ByteArrayOutputStream testStream = new ByteArrayOutputStream();
        PrintStream printWriter = new PrintStream(testStream);
        GetResponse response = GetResponse.getResource(request, ResourceLocation.get("html", "site:login.html"), null, false);
        response.respond(printWriter);
        String responseString = testStream.toString();
        String responseBody = response.getResponseBody();
        assertTrue(responseString.contains(responseBody));
        assertTrue(responseString.contains("HTTP/1.1 200 OK"));
    }
}
