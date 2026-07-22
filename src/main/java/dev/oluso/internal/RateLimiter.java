package dev.oluso.internal;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Sliding-window limiter over the last 60 seconds, used to stop a crash loop
 * from flooding the ingestion API. Synchronized since {@code OlusoClient} is
 * shared across request-handling threads.
 */
public final class RateLimiter {
    private final int maxPerMinute;
    private final Deque<Long> timestamps = new ArrayDeque<>();

    public RateLimiter(int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    /**
     * Returns true and records the attempt if under the limit; returns
     * false (recording nothing) once the limit's been hit for this window.
     */
    public synchronized boolean canSend() {
        long now = System.currentTimeMillis();
        long oneMinuteAgo = now - 60_000L;

        while (!timestamps.isEmpty() && timestamps.peekFirst() <= oneMinuteAgo) {
            timestamps.pollFirst();
        }

        if (timestamps.size() < maxPerMinute) {
            timestamps.addLast(now);
            return true;
        }
        return false;
    }
}
