package de.igslandstuhl.database.server.webserver;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.server.resources.ResourceLocation;
import de.igslandstuhl.database.server.webserver.requests.GetRequest;
import de.igslandstuhl.database.server.webserver.responses.GetResponse;
import de.igslandstuhl.database.server.webserver.responses.HttpResponse;

public class GetResponseTest {
    GetRequest request = new GetRequest("GET / HTTP/1.1", "127.0.0.1", true);
    @BeforeAll
    public static void registerWebPaths() throws IOException {
        WebPath.registerPaths();
    }
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
        assertTrue(responseString.contains("Connection: close\n"));
    }

    @Test
    void errorResponsesDeclareExactUtf8BodyLength() {
        for (Status status : new Status[] {Status.BAD_REQUEST, Status.UNAUTHORIZED, Status.FORBIDDEN,
                Status.NOT_FOUND, Status.INTERNAL_SERVER_ERROR}) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            HttpResponse.error(request, status).respond(new PrintStream(output));
            String response = output.toString(StandardCharsets.UTF_8);
            String body = response.substring(response.indexOf("\n\n") + 2);
            String length = response.lines()
                    .filter(line -> line.startsWith("Content-Length:"))
                    .findFirst().orElseThrow().substring("Content-Length:".length()).trim();
            assertEquals(body.getBytes(StandardCharsets.UTF_8).length, Integer.parseInt(length));
            assertTrue(response.contains("Connection: close"));
        }
    }
}
