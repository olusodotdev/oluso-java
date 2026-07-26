package dev.oluso.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void allowsUpToTheConfiguredLimit() {
        RateLimiter limiter = new RateLimiter(3);
        assertTrue(limiter.canSend());
        assertTrue(limiter.canSend());
        assertTrue(limiter.canSend());
        assertFalse(limiter.canSend());
    }

    @Test
    void zeroLimitNeverAllowsSending() {
        RateLimiter limiter = new RateLimiter(0);
        assertFalse(limiter.canSend());
    }
}
