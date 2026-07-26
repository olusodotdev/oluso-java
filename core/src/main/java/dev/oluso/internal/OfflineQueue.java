package dev.oluso.internal;

import dev.oluso.ErrorReport;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * In-memory-only retry queue for reports that failed to send. There's
 * nothing durable to persist to here -- a queued report only gets a second
 * chance if a *later* error triggers {@link #process(Function)} again in the same
 * process. On a process that crashes or restarts before that happens, the
 * report is simply lost, same as if retrying weren't attempted at all.
 */
public final class OfflineQueue {
    private static final class QueuedReport {
        final ErrorReport report;
        int retries;

        QueuedReport(ErrorReport report) {
            this.report = report;
        }
    }

    private final Deque<QueuedReport> queue = new ArrayDeque<>();
    private final int maxSize;
    private boolean processing = false;

    public OfflineQueue(int maxSize) {
        this.maxSize = maxSize;
    }

    public synchronized void enqueue(ErrorReport report) {
        queue.addLast(new QueuedReport(report));
        while (queue.size() > maxSize) {
            queue.removeFirst();
        }
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Drains the queue, sending each report in order via {@code sendFn},
     * stopping at the first failure (requeuing it, up to 3 attempts, then
     * dropping it) rather than hammering an endpoint that's still down. If
     * another {@code process} call is already draining the queue, this
     * returns immediately instead of waiting its turn -- the in-flight call
     * will pick up whatever's left.
     */
    public CompletableFuture<Void> process(Function<ErrorReport, CompletableFuture<Void>> sendFn) {
        synchronized (this) {
            if (processing || queue.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            processing = true;
        }
        return processNext(sendFn).whenComplete((result, error) -> {
            synchronized (this) {
                processing = false;
            }
        });
    }

    private CompletableFuture<Void> processNext(Function<ErrorReport, CompletableFuture<Void>> sendFn) {
        QueuedReport front;
        synchronized (this) {
            front = queue.peekFirst();
        }
        if (front == null) {
            return CompletableFuture.completedFuture(null);
        }

        return sendFn.apply(front.report)
                .handle((result, error) -> error)
                .thenCompose(error -> {
                    if (error == null) {
                        synchronized (this) {
                            queue.pollFirst();
                        }
                        return processNext(sendFn);
                    }
                    synchronized (this) {
                        front.retries++;
                        if (front.retries >= 3) {
                            queue.pollFirst();
                        }
                    }
                    return CompletableFuture.<Void>completedFuture(null);
                });
    }
}
