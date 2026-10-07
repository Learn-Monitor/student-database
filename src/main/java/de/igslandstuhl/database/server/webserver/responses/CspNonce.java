package de.igslandstuhl.database.server.webserver.responses;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/** Creates and carries the nonce for one HTML response. */
public final class CspNonce {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int BYTES = 32;

    private final String value;

    private CspNonce(String value) {
        this.value = value;
    }

    public static CspNonce create() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return new CspNonce(ENCODER.encodeToString(bytes));
    }

    public String value() {
        return value;
    }

    /** Replaces the only supported nonce placeholder in trusted HTML templates. */
    public String apply(String html) {
        Objects.requireNonNull(html, "html");
        return html.replace("%{cspNonce}", value);
    }
}
