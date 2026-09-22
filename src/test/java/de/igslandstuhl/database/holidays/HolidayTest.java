package de.igslandstuhl.database.holidays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import de.igslandstuhl.database.api.SchoolYear;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HolidayTest {
    private final String originalApiUrl = Holiday.API_URL;
    private final HttpClient originalClient = Holiday.HTTP_CLIENT;
    private final Duration originalTimeout = Holiday.REQUEST_TIMEOUT;
    private HttpServer server;

    @AfterEach
    void restoreHolidayClient() {
        Holiday.API_URL = originalApiUrl;
        Holiday.HTTP_CLIENT = originalClient;
        Holiday.REQUEST_TIMEOUT = originalTimeout;
        if (server != null) server.stop(0);
    }

    @Test
    void http200StillParsesHolidayData() throws Exception {
        startServer(200, "{\"periods\":[{\"id\":1,\"name\":\"Sommer\",\"starts_on\":\"2025-07-01\",\"ends_on\":\"2025-08-01\",\"location_id\":1,\"is_public_holiday\":false,\"is_school_vacation\":true}]}");

        Holiday[] holidays = Holiday.holidaysInterval(Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-12-31T00:00:00Z"));

        assertEquals(1, holidays.length);
        assertEquals("Sommer", holidays[0].getName());
    }

    @Test
    void http500FallsBackToExistingSchoolYear() throws Exception {
        startServer(500, "failure");
        SchoolYear local = localSchoolYear();

        assertDoesNotThrow(() -> Holiday.setupCurrentSchoolYear(() -> local));
    }

    @Test
    void ioFailureFallsBackToExistingSchoolYear() {
        Holiday.API_URL = "http://127.0.0.1:1/unavailable";
        Holiday.HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(100)).build();
        SchoolYear local = localSchoolYear();

        assertDoesNotThrow(() -> Holiday.setupCurrentSchoolYear(() -> local));
    }

    @Test
    void timeoutFallsBackToExistingSchoolYear() throws Exception {
        startServer(200, "{\"periods\":[]}", 1000);
        Holiday.REQUEST_TIMEOUT = Duration.ofMillis(100);
        SchoolYear local = localSchoolYear();

        assertDoesNotThrow(() -> Holiday.setupCurrentSchoolYear(() -> local));
    }

    @Test
    void remoteFailureWithoutSchoolYearStillFails() throws Exception {
        startServer(500, "failure");

        assertThrows(IllegalStateException.class, () -> Holiday.setupCurrentSchoolYear(() -> null));
    }

    private SchoolYear localSchoolYear() {
        return new SchoolYear(1, "2025/2026", 40, 10,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(10), null);
    }

    private void startServer(int status, String body) throws IOException {
        startServer(status, body, 0);
    }

    private void startServer(int status, String body, long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/periods", exchange -> {
            try {
                if (delayMillis > 0) {
                    try {
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally {
                exchange.close();
            }
        });
        server.start();
        Holiday.API_URL = "http://127.0.0.1:" + server.getAddress().getPort() + "/periods";
        Holiday.HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    }
}
