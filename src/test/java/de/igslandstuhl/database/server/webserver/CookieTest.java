package de.igslandstuhl.database.server.webserver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CookieTest {
    Cookie cookie;
    @BeforeEach
    public void initCookie() {
        cookie = new Cookie("test-key", "test-value");
    }
    @Test
    void testGetName() {
        assertEquals(cookie.getName(), "test-key");
    }

    @Test
    void testGetValue() {
        assertEquals(cookie.getValue(), "test-value");
    }

    @Test
    void testToString() {
        assertEquals(cookie.toString(), "test-key=test-value");
    }

    @Test
    void testEquals() {
        assertEquals(cookie, new Cookie("test-key", "test-value"));
    }

    @Test
    void testParseSingleCookie() {
        assertArrayEquals(
            new Cookie[] {new Cookie("test-key", "test-value")},
            Cookie.parse("test-key=test-value")
        );
    }

    @Test
    void testParseMultipleCookies() {
        assertArrayEquals(
            new Cookie[] {
                new Cookie("first", "one"),
                new Cookie("second", "two")
            },
            Cookie.parse("first=one; second=two")
        );
    }

    @Test
    void testParseValueContainingEqualsSign() {
        assertArrayEquals(
            new Cookie[] {new Cookie("token", "part1=part2=part3")},
            Cookie.parse("token=part1=part2=part3")
        );
    }

    @Test
    void testParseEmptyValue() {
        assertArrayEquals(
            new Cookie[] {new Cookie("empty", "")},
            Cookie.parse("empty=")
        );
    }

    @Test
    void testParseIgnoresInvalidPairs() {
        assertArrayEquals(
            new Cookie[] {
                new Cookie("valid", "value"),
                new Cookie("other", "cookie")
            },
            Cookie.parse(
                "invalid; =missing-name; valid=value; other=cookie"
            )
        );
    }

    @Test
    void testParseEmptyHeader() {
        assertArrayEquals(new Cookie[0], Cookie.parse(null));
        assertArrayEquals(new Cookie[0], Cookie.parse(""));
        assertArrayEquals(new Cookie[0], Cookie.parse("   "));
    }
}
