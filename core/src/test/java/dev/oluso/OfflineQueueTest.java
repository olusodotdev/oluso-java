package dev.oluso;

import dev.oluso.internal.OfflineQueue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineQueueTest {

    private static ErrorReport dummyReport(String message) {
        return new ErrorReport(message, message, null, "test", Severity.MEDIUM, List.of(), "abc", null, 0L);
    }

    @Test
    void drainsTheQueueOnSuccessfulSends() {
        OfflineQueue queue = new OfflineQueue(10);
        queue.enqueue(dummyReport("a"));
        queue.enqueue(dummyReport("b"));

        queue.process(report -> CompletableFuture.completedFuture(null)).join();
        assertTrue(queue.isEmpty());
    }

    @Test
    void stopsAtTheFirstFailureAndLeavesTheRestQueued() {
        OfflineQueue queue = new OfflineQueue(10);
        queue.enqueue(dummyReport("a"));
        queue.enqueue(dummyReport("b"));

        AtomicInteger attempts = new AtomicInteger();
        queue.process(report -> {
            attempts.incrementAndGet();
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("boom"));
            return failed;
        }).join();

        assertEquals(1, attempts.get());
        assertFalse(queue.isEmpty());
    }

    @Test
    void dropsAReportAfterThreeFailedRetries() {
        OfflineQueue queue = new OfflineQueue(10);
        queue.enqueue(dummyReport("a"));

        for (int i = 0; i < 3; i++) {
            queue.process(report -> {
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(new RuntimeException("boom"));
                return failed;
            }).join();
        }

        assertTrue(queue.isEmpty());
    }

    @Test
    void respectsMaxSizeByDroppingTheOldest() {
        OfflineQueue queue = new OfflineQueue(2);
        queue.enqueue(dummyReport("a"));
        queue.enqueue(dummyReport("b"));
        queue.enqueue(dummyReport("c"));

        List<String> seen = new ArrayList<>();
        queue.process(report -> {
            seen.add(report.getMessage());
            return CompletableFuture.completedFuture(null);
        }).join();

        assertEquals(List.of("b", "c"), seen);
    }
}
