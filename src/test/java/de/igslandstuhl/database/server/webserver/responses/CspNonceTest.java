package de.igslandstuhl.database.server.webserver.responses;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class CspNonceTest {
    private static final Pattern BASE64_URL = Pattern.compile("[A-Za-z0-9_-]{43}");

    @Test
    void createsUniqueHighEntropyUrlSafeValues() {
        CspNonce first = CspNonce.create();
        CspNonce second = CspNonce.create();
        assertNotEquals(first.value(), second.value());
        assertTrue(BASE64_URL.matcher(first.value()).matches());
        assertTrue(BASE64_URL.matcher(second.value()).matches());
    }

    @Test
    void appliesOnlyTheResponseNonceToTrustedMarkup() {
        CspNonce nonce = CspNonce.create();
        assertTrue(nonce.apply("<script nonce=\"%{cspNonce}\"></script>")
                .contains("nonce=\"" + nonce.value() + "\""));
    }
}
