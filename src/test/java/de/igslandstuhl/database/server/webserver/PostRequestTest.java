package de.igslandstuhl.database.server.webserver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonSyntaxException;

import de.igslandstuhl.database.server.webserver.requests.HttpHeader;
import de.igslandstuhl.database.server.webserver.requests.PostRequest;

public class PostRequestTest {
    private static final String LOCALHOST = "127.0.0.1";
    PostRequest postRequest1;
    PostRequest postRequest2;
    PostRequest postRequestJson;
    @BeforeEach
    void initPostRequest() {
        postRequest1 = new PostRequest(
                        "POST /login HTTP/1.1\r\n" + //
                        "Content-Length: 37\r\n" + //
                        "Cookie:test-key=test-value", 
                        "username=adminUser&password=adminPass", LOCALHOST, true);
        postRequest2 = new PostRequest(
                        new HttpHeader("POST /login HTTP/1.1\r\n" + //
                        "Content-Length: 37\r\n" + //
                        "Cookie:test-key=test-value;other=value"), 
                        "username=adminUser&password=adminPass", LOCALHOST, true);
        postRequestJson = new PostRequest(
                        new HttpHeader("POST /student-data HTTP/1.1\r\n" + //
                        "Content-Length: 9\r\n" + //
                        "Cookie:test-key=test-value"), 
                        "{" + //
                            "\"id\": 0" + //
                        "}", LOCALHOST, true);
    }
    @Test
    void testGetBodyAsString() {
        assertEquals("username=adminUser&password=adminPass", postRequest1.getBodyAsString());
        assertEquals("username=adminUser&password=adminPass", postRequest2.getBodyAsString());
        assertEquals("{\"id\": 0}", postRequestJson.getBodyAsString());
    }

    @Test
    void testGetContentLength() {
        assertEquals(37, postRequest1.getContentLength());
        assertEquals(37, postRequest2.getContentLength());
        assertEquals(9, postRequestJson.getContentLength());
    }

    @Test
    void testGetContext() {
        assertEquals(postRequest1.getContext(), "html");
        assertEquals(postRequest2.getContext(), "html");
        assertEquals(postRequestJson.getContext(), "html");
    }

    @Test
    void testGetCookies() {
        assertArrayEquals(new Cookie[] {new Cookie("test-key", "test-value")}, postRequest1.getCookies());
        assertArrayEquals(new Cookie[] {new Cookie("test-key", "test-value"), new Cookie("other", "value")}, postRequest2.getCookies());
        assertArrayEquals(postRequest1.getCookies(), postRequestJson.getCookies());
    }

    @Test
    void testGetFormData() {
        Map<String,String> data = postRequest1.getFormData();
        assertEquals(data, postRequest2.getFormData());
        
        assertTrue(data.containsKey("username"));
        assertTrue(data.containsKey("password"));
        
        assertEquals("adminUser", data.get("username"));
        assertEquals("adminPass", data.get("password"));

        assertThrows(IllegalArgumentException.class, postRequestJson::getFormData);
    }

    @Test
    void testGetJson() {
        Map<String,Object> json = postRequestJson.getJson();
        assertTrue(json.containsKey("id"));
        assertInstanceOf(Number.class, json.get("id"));
        assertEquals(0,((Number)json.get("id")).intValue());

        assertThrows(JsonSyntaxException.class, postRequest1::getJson);
        assertThrows(JsonSyntaxException.class, postRequest2::getJson);
    }

    @Test
    void testGetPath() {
        assertEquals("/login", postRequest1.getPath());
        assertEquals("/login", postRequest2.getPath());
        assertEquals("/student-data", postRequestJson.getPath());
    }
}
