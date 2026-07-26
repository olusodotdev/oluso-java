package dev.oluso.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FingerprintTest {

    @Test
    void sameErrorTypeAndMessageProduceTheSameFingerprint() {
        String a = Fingerprint.generate("ValueError", "invalid input", null);
        String b = Fingerprint.generate("ValueError", "invalid input", null);
        assertEquals(a, b);
    }

    @Test
    void differentMessagesProduceDifferentFingerprints() {
        String a = Fingerprint.generate("ValueError", "invalid input", null);
        String b = Fingerprint.generate("ValueError", "missing field", null);
        assertNotEquals(a, b);
    }

    @Test
    void normalizesDynamicValuesSoSimilarErrorsCollapseTogether() {
        String a = Fingerprint.generate("NotFound", "user 12345 not found", null);
        String b = Fingerprint.generate("NotFound", "user 67890 not found", null);
        assertEquals(a, b);
    }

    @Test
    void normalizesUuidsAndPathsAndUrls() {
        String a = Fingerprint.generate(
                "IoError", "failed to read /var/data/file.txt from https://example.test/a", null);
        String b = Fingerprint.generate(
                "IoError", "failed to read /tmp/other.log from https://example.test/b", null);
        assertEquals(a, b);
    }

    @Test
    void returnsAnEightCharacterHexString() {
        String fingerprint = Fingerprint.generate("Error", "boom", null);
        assertEquals(8, fingerprint.length());
        assertTrue(fingerprint.chars().allMatch(c -> Character.digit(c, 16) != -1));
    }
}
