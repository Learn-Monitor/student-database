package de.igslandstuhl.database.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.igslandstuhl.database.server.webserver.WebPath;
import de.igslandstuhl.database.server.webserver.handlers.GetRequestHandler;
import de.igslandstuhl.database.server.webserver.handlers.HttpHandler;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;
import de.igslandstuhl.database.server.webserver.responses.GetResponse;
import de.igslandstuhl.database.server.webserver.requests.RequestType;

import java.util.List;
import de.igslandstuhl.database.client.HTMLTemplate;

class WebServerHttpsIntegrationTest {
    private static final String PASSWORD = "changeit";

    private WebServer server;
    private Path keystore;

    @BeforeEach
    void setUp() throws Exception {
        keystore = Files.createTempFile("webserver-test-", ".jks");
        Files.delete(keystore);
        Process keytool = new ProcessBuilder(
                "keytool", "-genkeypair", "-noprompt",
                "-alias", "test",
                "-keyalg", "RSA",
                "-storetype", "JKS",
                "-keystore", keystore.toString(),
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-dname", "CN=localhost",
                "-validity", "1")
                .redirectErrorStream(true)
                .start();
        assertEquals(0, keytool.waitFor(10, TimeUnit.SECONDS) ? keytool.exitValue() : -1);

        WebPath.registerPaths();
        HTMLTemplate.registerAll();
        WebPath.registerPath("/attendance", RequestType.GET, "FileRequestHandler", List.of(), "html", AccessLevel.ADMIN);
        GetRequestHandler.getInstance().registerHandlers();
        HttpHandler.registerGetRequestHandler("/attendance", AccessLevel.ADMIN, GetResponse::notFound);
        server = new WebServer(freePort(), keystore.toString(), PASSWORD, "JKS");
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) server.stop();
        if (keystore != null) Files.deleteIfExists(keystore);
    }

    @Test
    void getRoutesCompleteOverRealTlsWithoutUnexpectedEof() throws Exception {
        SSLContext context = insecureClientContext();
        for (String path : new String[] {"/", "/login"}) {
            HttpsURLConnection connection = (HttpsURLConnection) new URL(
                    "https://127.0.0.1:" + port() + path).openConnection();
            connection.setSSLSocketFactory(context.getSocketFactory());
            connection.setHostnameVerifier((host, session) -> true);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(10000);

            assertEquals(200, connection.getResponseCode());
            byte[] body = connection.getInputStream().readAllBytes();
            assertNotNull(body);
            assertFalse(body.length == 0);
            assertEquals("close", connection.getHeaderField("Connection"));
            connection.disconnect();

            Process curl = new ProcessBuilder(
                    "curl", "-k", "--fail-with-body", "--connect-timeout", "3",
                    "--max-time", "10", "-sS", "https://127.0.0.1:" + port() + path)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = curl.waitFor(15, TimeUnit.SECONDS);
            String curlOutput = new String(curl.getInputStream().readAllBytes());
            assertEquals(0, finished ? curl.exitValue() : -1, () -> "curl failed: " + curlOutput);
        }
    }

    @Test
    void anonymousErrorRoutesAreFramedAndCloseCleanly() throws Exception {
        SSLContext context = insecureClientContext();
        for (String path : new String[] {"/dashboard", "/attendance"}) {
            HttpsURLConnection connection = (HttpsURLConnection) new URL(
                    "https://127.0.0.1:" + port() + path).openConnection();
            connection.setSSLSocketFactory(context.getSocketFactory());
            connection.setHostnameVerifier((host, session) -> true);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(10000);

            assertEquals(401, connection.getResponseCode());
            byte[] body = connection.getErrorStream().readAllBytes();
            assertEquals(body.length, Integer.parseInt(connection.getHeaderField("Content-Length")));
            assertFalse(body.length == 0);
            assertEquals("close", connection.getHeaderField("Connection"));
            connection.disconnect();

            Process curl = new ProcessBuilder(
                    "curl", "-k", "--connect-timeout", "3", "--max-time", "10", "-sS",
                    "https://127.0.0.1:" + port() + path)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = curl.waitFor(15, TimeUnit.SECONDS);
            curl.getInputStream().readAllBytes();
            assertEquals(0, finished ? curl.exitValue() : -1);
        }
    }

    @Test
    void tlsClientThatSendsNoHttpHeadersIsClosedCleanly() throws Exception {
        SSLContext context = insecureClientContext();
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket("127.0.0.1", port())) {
            socket.startHandshake();
        }
    }

    private int port() {
        return serverPort;
    }

    private int serverPort;

    private int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            serverPort = socket.getLocalPort();
            return serverPort;
        }
    }

    private static SSLContext insecureClientContext() throws Exception {
        TrustManager[] trustAll = {new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String authType) { }
            public void checkServerTrusted(X509Certificate[] chain, String authType) { }
        }};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustAll, new SecureRandom());
        return context;
    }
}
