package de.igslandstuhl.database.server.webserver;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.webserver.requests.PostRequest;
import de.igslandstuhl.database.server.webserver.responses.PostResponse;

public class PostResponseTest {
    PostRequest initialRequest;
    @BeforeEach
    void setup() {
        initialRequest = new PostRequest("POST /login HTTP/1.1", "username=adminUser;password=adminPass", "127.0.0.1", true);
    }
    private String read(PostResponse r) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        r.respond(new PrintStream(out));
        return out.toString();
    }
    @Test
    void testBadRequest() {
        assert read(PostResponse.badRequest("Test", initialRequest)).contains("400");
    }

    @Test
    void testForbidden() {
        assert read(PostResponse.forbidden("Test", initialRequest)).contains("403");
    }

    @Test
    void testInternalServerError() {
        assert read(PostResponse.internalServerError("Test", initialRequest)).contains("500");
    }

    @Test
    void testNotFound() {
        assert read(PostResponse.notFound("Test", initialRequest)).contains("404");
    }

    @Test
    void testOk() {
        String response = read(PostResponse.ok("Test", ContentType.TEXT_PLAIN, initialRequest));
        assert response.contains("200");
        assert response.contains("Connection: close");
        assert read(PostResponse.ok("Test", ContentType.TEXT_PLAIN, initialRequest, new Cookie("test-key", "test-value"))).contains("Set-Cookie: test-key=test-value");
    }

    @Test
    void testRedirect() {
        assert read(PostResponse.redirect("Test", initialRequest)).contains("302");
    }

    @Test
    void testUnauthorized() {
        assert read(PostResponse.unauthorized("Test", initialRequest)).contains("401");
    }

    @Test
    void debugLogContainsOnlyResponseMetadata() {
        Logger logger = (Logger) LoggerFactory.getLogger(Server.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            String sensitiveBody = "{\"token\":\"must-not-appear-in-logs\"}";
            read(PostResponse.ok(sensitiveBody, ContentType.JSON, initialRequest));

            String message = appender.list.get(appender.list.size() - 1).getFormattedMessage();
            Assertions.assertEquals(
                "HTTP response: status=200, contentType=text/json, bodyLength="
                    + sensitiveBody.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                message
            );
            Assertions.assertFalse(message.contains(sensitiveBody));
            Assertions.assertFalse(message.contains("must-not-appear-in-logs"));
        } finally {
            logger.setLevel(previousLevel);
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
