package de.igslandstuhl.database.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.igslandstuhl.database.Arguments;

public class KeystorePasswordTest {
    @TempDir
    Path tempDir;

    @Test
    void legacyPasswordStillWorks() throws Exception {
        assertEquals("secret", resolve("--keystore-password", "secret"));
    }

    @Test
    void passwordFileIsRead() throws Exception {
        Path file = write("secret");

        assertEquals("secret", resolve("--keystore-password-file", file.toString()));
    }

    @Test
    void removesOneLfOnly() throws Exception {
        Path file = write("secret\n\n");

        assertEquals("secret\n", resolve("--keystore-password-file", file.toString()));
    }

    @Test
    void removesOneCrLfOnly() throws Exception {
        Path file = write("secret\r\n");

        assertEquals("secret", resolve("--keystore-password-file", file.toString()));
    }

    @Test
    void preservesOtherWhitespace() throws Exception {
        Path file = write(" secret value ");

        assertEquals(" secret value ", resolve("--keystore-password-file", file.toString()));
    }

    @Test
    void emptyPasswordIsAllowed() throws Exception {
        Path file = write("");

        assertEquals("", resolve("--keystore-password-file", file.toString()));
    }

    @Test
    void defaultIsKeptWhenNoPasswordOptionIsPresent() throws Exception {
        assertEquals("changeit", KeystorePassword.resolve(new Arguments(new String[0])));
    }

    @Test
    void bothPasswordOptionsAreRejectedWithoutSecrets() {
        String secret = "do-not-leak";
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> resolve("--keystore-password", secret, "--keystore-password-file", "/credential"));

        assertEquals("Specify either --keystore-password or --keystore-password-file, not both.", error.getMessage());
        assertFalse(error.toString().contains(secret));
    }

    @Test
    void missingValueIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> KeystorePassword.resolve(new Arguments(new String[] {"--keystore-password-file"})));

        assertEquals("Option --keystore-password-file requires a value.", error.getMessage());
    }

    @Test
    void missingFileIsReportedWithoutPasswordContent() {
        String secret = "never-in-file";
        Path file = tempDir.resolve("missing-" + secret);

        IOException error = assertThrows(IOException.class,
                () -> resolve("--keystore-password-file", file.toString()));

        assertTrue(error.getMessage().contains(file.toString()));
        assertFalse(error.toString().contains("secret-value"));
    }

    @Test
    void directoryIsRejected() {
        Path directory = tempDir.resolve("credential-directory");

        IOException error = assertThrows(IOException.class,
                () -> resolve("--keystore-password-file", directory.toString()));

        assertTrue(error.getMessage().contains(directory.toString()));
    }

    private Path write(String content) throws IOException {
        Path file = tempDir.resolve("credential");
        Files.writeString(file, content);
        return file;
    }

    private String resolve(String... arguments) throws Exception {
        return KeystorePassword.resolve(new Arguments(arguments));
    }
}
