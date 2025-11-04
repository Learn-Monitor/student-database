package de.igslandstuhl.database.server.webserver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.server.webserver.requests.HttpHeader;

public class HttpHeaderTest {
    HttpHeader postHeader;
    @BeforeEach
    void initPostHeader() {
        postHeader = new HttpHeader("POST /login HTTP/1.1\r\nContent-Length: 45\r\nCookie:test-key=test-value");
    }

    @Test
    void testGetContentLength() {
        assertEquals(postHeader.getContentLength(), 45);
    }

    @Test
    void testGetCookies() {
        assertArrayEquals(postHeader.getCookies(), new Cookie [] {new Cookie("test-key", "test-value")});
    }

    @Test
    void testGetPath() {
        assertEquals(postHeader.getPath(), "/login");
    }
}
