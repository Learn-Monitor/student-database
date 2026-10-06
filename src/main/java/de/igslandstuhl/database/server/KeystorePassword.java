package de.igslandstuhl.database.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import de.igslandstuhl.database.Arguments;

/** Resolves the web server keystore password from the supported CLI options. */
public final class KeystorePassword {
    private static final String PASSWORD_OPTION = "keystore-password";
    private static final String PASSWORD_FILE_OPTION = "keystore-password-file";

    private KeystorePassword() {
    }

    /**
     * Resolves the configured password. Exactly one trailing LF, including a
     * CRLF pair, is removed from file contents. No other whitespace is changed.
     *
     * @param arguments parsed application arguments
     * @return the configured password, or the legacy default when neither
     *         password option is present
     * @throws IOException if the password file cannot be read
     * @throws IllegalArgumentException if options are conflicting or incomplete
     */
    public static String resolve(Arguments arguments) throws IOException {
        boolean hasPassword = arguments.hasKey(PASSWORD_OPTION);
        boolean hasPasswordFile = arguments.hasKey(PASSWORD_FILE_OPTION);

        if (hasPassword && hasPasswordFile) {
            throw new IllegalArgumentException(
                    "Specify either --keystore-password or --keystore-password-file, not both.");
        }
        if (hasPassword) {
            requireValue(arguments, PASSWORD_OPTION);
            return arguments.get(PASSWORD_OPTION);
        }
        if (hasPasswordFile) {
            requireValue(arguments, PASSWORD_FILE_OPTION);
            return readPasswordFile(arguments.get(PASSWORD_FILE_OPTION));
        }
        return "changeit";
    }

    private static void requireValue(Arguments arguments, String option) {
        if (!arguments.hasValue(option)) {
            throw new IllegalArgumentException("Option --" + option + " requires a value.");
        }
    }

    private static String readPasswordFile(String fileName) throws IOException {
        final Path path;
        try {
            path = Path.of(fileName);
        } catch (InvalidPathException | SecurityException e) {
            throw new IOException("Could not read keystore password file '" + fileName + "'.", e);
        }
        final String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException e) {
            throw new IOException("Could not read keystore password file '" + fileName + "'.", e);
        }

        if (content.endsWith("\r\n")) {
            return content.substring(0, content.length() - 2);
        }
        if (content.endsWith("\n")) {
            return content.substring(0, content.length() - 1);
        }
        return content;
    }
}
