package dev.oluso.internal;

import java.util.regex.Pattern;

/**
 * Generates a stable fingerprint for error deduplication, from the error
 * type, its (normalized) message, and the first few lines of its stack
 * trace when one's available.
 *
 * <p>Uses a plain FNV-1a hash rather than a cryptographic one -- same
 * choice every other Oluso SDK makes, since deduplication only needs a
 * stable, well-distributed hash, and this keeps the algorithm (and the
 * resulting fingerprints) identical across every Oluso SDK.
 */
public final class Fingerprint {
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATH = Pattern.compile("[/\\\\][\\w/\\\\.\\-]+");
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private Fingerprint() {
    }

    public static String generate(String errorType, String message, String stackTrace) {
        StringBuilder components = new StringBuilder();
        components.append(errorType).append('|').append(normalizeMessage(message));

        if (stackTrace != null && !stackTrace.isEmpty()) {
            components.append('|').append(stackSignature(stackTrace));
        }

        return fnv1aHash(components.toString());
    }

    private static String normalizeMessage(String message) {
        String normalized = NUMBER.matcher(message).replaceAll("N");
        normalized = UUID.matcher(normalized).replaceAll("UUID");
        normalized = PATH.matcher(normalized).replaceAll("PATH");
        normalized = URL.matcher(normalized).replaceAll("URL");
        return WHITESPACE.matcher(normalized).replaceAll(" ").trim();
    }

    /**
     * Java stack traces don't share JS/V8's {@code at fn (file:line)} shape,
     * so unlike some other SDKs this doesn't try to extract bare method
     * names -- it just takes the first few lines verbatim as a
     * stable-enough signature.
     */
    private static String stackSignature(String stackTrace) {
        String[] lines = stackTrace.split("\n");
        StringBuilder signature = new StringBuilder();
        int limit = Math.min(5, lines.length);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                signature.append("->");
            }
            signature.append(lines[i].trim());
        }
        return signature.toString();
    }

    private static String fnv1aHash(String input) {
        long hash = 0x811c9dc5L;
        for (byte b : input.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash = (hash * 0x01000193L) & 0xFFFFFFFFL;
        }
        return String.format("%08x", hash);
    }
}
