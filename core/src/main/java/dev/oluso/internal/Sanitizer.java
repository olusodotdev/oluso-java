package dev.oluso.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Redacts values behind sensitive-looking keys (passwords, tokens, auth
 * headers, ...) before a report leaves the process. A key is treated as
 * sensitive if it *contains* one of the patterns, case-insensitively -- same
 * substring match the other Oluso SDKs use, so {@code "user_password"} and
 * {@code "PASSWORD_HASH"} are both caught, not just an exact
 * {@code "password"} key.
 */
public final class Sanitizer {
    private static final String REDACTED = "[REDACTED]";
    private static final int MAX_DEPTH = 10;

    private static final List<String> DEFAULT_SENSITIVE_KEYS = List.of(
            "password", "passwd", "pwd", "secret", "token", "api_key", "apikey",
            "access_token", "auth", "credentials", "mysql_pwd", "private_key",
            "privatekey", "session", "cookie", "csrf", "xsrf", "authorization",
            "bearer", "jwt", "ssn", "social_security", "credit_card",
            "card_number", "cvv", "pin");

    private final List<String> patterns;

    public Sanitizer(List<String> customSensitiveKeys) {
        this.patterns = new ArrayList<>();
        for (String key : DEFAULT_SENSITIVE_KEYS) {
            patterns.add(key.toLowerCase(Locale.ROOT));
        }
        if (customSensitiveKeys != null) {
            for (String key : customSensitiveKeys) {
                patterns.add(key.toLowerCase(Locale.ROOT));
            }
        }
    }

    private boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /** Sanitize an arbitrary value tree (a parsed request body, typically). */
    public Object sanitizeValue(Object value) {
        return sanitizeValueAtDepth(value, MAX_DEPTH);
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeValueAtDepth(Object value, int depth) {
        if (depth == 0) {
            return "[Max Depth Reached]";
        }
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            int count = 0;
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                if (count++ >= 200) {
                    sanitized.put("_truncated", true);
                    break;
                }
                String key = entry.getKey();
                Object val = entry.getValue();
                if (isSensitiveKey(key)) {
                    sanitized.put(key, REDACTED);
                } else {
                    sanitized.put(key, sanitizeValueAtDepth(val, depth - 1));
                }
            }
            return sanitized;
        }
        if (value instanceof List) {
            List<Object> sanitized = new ArrayList<>();
            List<Object> items = (List<Object>) value;
            int limit = Math.min(items.size(), 100);
            for (Object item : items.subList(0, limit)) {
                sanitized.add(sanitizeValueAtDepth(item, depth - 1));
            }
            if (items.size() > limit) {
                sanitized.add("[" + (items.size() - limit) + " more items truncated]");
            }
            return sanitized;
        }
        if (value instanceof CharSequence) {
            String text = value.toString();
            return text.length() <= 4000 ? text : text.substring(0, 4000) + "... [truncated]";
        }
        return value;
    }

    /**
     * Sanitize a flat string map (HTTP headers or a query string), always
     * redacting {@code authorization}/{@code cookie} outright regardless of
     * the sensitive-key pattern list, matching the other SDKs' hardcoded
     * rule.
     */
    public Map<String, String> sanitizeStringMap(Map<String, String> map) {
        if (map == null) {
            return null;
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.equals("authorization") || lower.equals("cookie") || isSensitiveKey(key)) {
                sanitized.put(key, REDACTED);
            } else {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }
}
