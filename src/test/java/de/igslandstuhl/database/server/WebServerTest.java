package de.igslandstuhl.database.server;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class WebServerTest {
    @Test
    public void testUtf8DecodingSimpleText() throws Exception {
        String original = "äöüßÄÖÜ";
        String encoded = java.net.URLEncoder.encode(original, StandardCharsets.UTF_8.name());

        String decoded = java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());
        assertEquals(original, decoded);
    }

    @Test
    public void testReadHeadersAsString() throws Exception {
        String headers = "POST /test HTTP/1.1\r\nHost: localhost\r\nContent-Length: 11\r\n\r\n";
        InputStream in = new ByteArrayInputStream(headers.getBytes(StandardCharsets.ISO_8859_1));
        WebServer.ClientHandler handler = new WebServer().new ClientHandler(null);
        String result = handler.readHeadersAsString(in);
        assertTrue(result.contains("Content-Length: 11"));
    }

    @Test
    public void emptyRequestIsIgnoredWithoutParsing() throws Exception {
        WebServer.ClientHandler handler = new WebServer().new ClientHandler(null);

        assertNull(handler.readHeadersAsString(new ByteArrayInputStream(new byte[0])));
    }
}
