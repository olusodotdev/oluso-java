package dev.oluso.internal;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SanitizerTest {

    @Test
    void redactsDefaultSensitiveKeys() {
        Sanitizer sanitizer = new Sanitizer(List.of());
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("username", "dave");
        value.put("password", "hunter2");

        @SuppressWarnings("unchecked")
        Map<String, Object> sanitized = (Map<String, Object>) sanitizer.sanitizeValue(value);

        assertEquals("dave", sanitized.get("username"));
        assertEquals("[REDACTED]", sanitized.get("password"));
    }

    @Test
    void matchesKeysContainingASensitivePattern() {
        Sanitizer sanitizer = new Sanitizer(List.of());
        Map<String, Object> value = Map.of("user_password_hash", "abc");

        @SuppressWarnings("unchecked")
        Map<String, Object> sanitized = (Map<String, Object>) sanitizer.sanitizeValue(value);

        assertEquals("[REDACTED]", sanitized.get("user_password_hash"));
    }

    @Test
    void redactsCustomSensitiveKeys() {
        Sanitizer sanitizer = new Sanitizer(List.of("internal_id"));
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("internal_id", "123");
        value.put("public_id", "456");

        @SuppressWarnings("unchecked")
        Map<String, Object> sanitized = (Map<String, Object>) sanitizer.sanitizeValue(value);

        assertEquals("[REDACTED]", sanitized.get("internal_id"));
        assertEquals("456", sanitized.get("public_id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void redactsNestedObjectsAndLists() {
        Sanitizer sanitizer = new Sanitizer(List.of());
        Map<String, Object> item = Map.of("token", "abc");
        Map<String, Object> value = Map.of("items", List.of(item));

        Map<String, Object> sanitized = (Map<String, Object>) sanitizer.sanitizeValue(value);
        List<Object> items = (List<Object>) sanitized.get("items");
        Map<String, Object> sanitizedItem = (Map<String, Object>) items.get(0);

        assertEquals("[REDACTED]", sanitizedItem.get("token"));
    }

    @Test
    void alwaysRedactsAuthorizationAndCookieHeaders() {
        Sanitizer sanitizer = new Sanitizer(List.of());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer abc");
        headers.put("Cookie", "session=abc");
        headers.put("Accept", "application/json");

        Map<String, String> sanitized = sanitizer.sanitizeStringMap(headers);

        assertEquals("[REDACTED]", sanitized.get("Authorization"));
        assertEquals("[REDACTED]", sanitized.get("Cookie"));
        assertEquals("application/json", sanitized.get("Accept"));
    }
}
