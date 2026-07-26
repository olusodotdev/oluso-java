package dev.oluso;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.oluso.internal.Fingerprint;
import dev.oluso.internal.OfflineQueue;
import dev.oluso.internal.RateLimiter;
import dev.oluso.internal.Sanitizer;
import dev.oluso.internal.Scope;
import dev.oluso.internal.Transport;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * The Oluso client. Thread-safe and meant to be constructed once and shared
 * across your application (a singleton, a Spring bean, ...).
 *
 * <p>Every {@code capture*} method returns a {@link CompletableFuture}
 * rather than blocking the calling thread -- reporting an error should
 * never add latency to whatever request or job triggered it. Call
 * {@code .join()} if you specifically need to wait for it (tests, a
 * graceful-shutdown hook), otherwise it's safe to ignore the returned
 * future entirely.
 */
public final class OlusoClient {
    private final OlusoOptions options;
    private final Transport transport;
    private final Scope scope;
    private final Sanitizer sanitizer;
    private final RateLimiter rateLimiter;
    private final OfflineQueue queue;

    public OlusoClient(OlusoOptions options) {
        this.options = options;
        this.transport = new Transport(new ObjectMapper());
        this.scope = new Scope(options.getMaxBreadcrumbs());
        this.sanitizer = new Sanitizer(options.getSensitiveKeys());
        this.rateLimiter = new RateLimiter(options.getMaxErrorsPerMinute());
        this.queue = new OfflineQueue(options.getMaxQueueSize());
    }

    /**
     * Runs {@code callable} with a fresh, request-scoped breadcrumb/user/
     * context store, backed by a {@link ThreadLocal}. Correct for the
     * common one-thread-per-request Java web deployment shape; not
     * propagated across a reactive stack that hops threads (Spring
     * WebFlux/Reactor) -- see {@link Scope} for details.
     */
    public <T> T runInScope(Callable<T> callable) throws Exception {
        return scope.run(callable);
    }

    public void addBreadcrumb(Breadcrumb breadcrumb) {
        scope.addBreadcrumb(breadcrumb);
    }

    public void setUserContext(UserContext user) {
        scope.setUserContext(user);
    }

    public void setCustomContext(String key, Object value) {
        scope.setCustomContext(key, value);
    }

    public CompletableFuture<Void> captureException(Throwable throwable) {
        return captureException(throwable, null);
    }

    /** Same as {@link #captureException(Throwable)}, attaching the request it happened during. */
    public CompletableFuture<Void> captureException(Throwable throwable, RequestContext request) {
        String errorType = throwable.getClass().getSimpleName();
        String message = throwable.getMessage() != null ? throwable.getMessage() : errorType;
        return capture(errorType, message, stackTraceOf(throwable), null, request);
    }

    public CompletableFuture<Void> captureMessage(String message, Severity severity) {
        return capture("Message", message, null, severity, null);
    }

    /**
     * Low-level entry point the ergonomic {@code capture*} methods above
     * are built on. Exposed directly so framework integrations built
     * outside this library can report with a specific severity/stack
     * trace/request without losing access to any of it.
     */
    public CompletableFuture<Void> capture(
            String errorType, String message, String stackTrace, Severity severity, RequestContext request) {
        if (options.getShouldReport() != null && !options.getShouldReport().test(message)) {
            return CompletableFuture.completedFuture(null);
        }

        if (!rateLimiter.canSend()) {
            if (options.isLogToConsole()) {
                System.err.println("[Oluso] Rate limit exceeded, error not reported");
            }
            return CompletableFuture.completedFuture(null);
        }

        if (options.isLogToConsole()) {
            System.err.println("[Oluso] " + errorType + ": " + message);
        }

        ErrorContext context = buildErrorContext(request);
        String fingerprint = options.getFingerprint() != null
                ? options.getFingerprint().apply(message, context)
                : Fingerprint.generate(errorType, message, stackTrace);

        ErrorReport report = new ErrorReport(
                generateTitle(message, errorType),
                message,
                stackTrace,
                options.getEnvironment(),
                severity != null ? severity : options.getDefaultSeverity(),
                options.getTags(),
                fingerprint,
                context,
                System.currentTimeMillis());

        return sendReport(report);
    }

    /**
     * Flushes any reports that failed to send and are waiting in the
     * offline queue. There's no background timer driving this
     * automatically -- call it wherever your application already has a
     * natural "done with this unit of work" point (end of a request, a
     * graceful-shutdown hook, ...).
     */
    public CompletableFuture<Void> flush() {
        return queue.process(this::sendToTransport);
    }

    /**
     * Installs a JVM-wide default uncaught exception handler that reports
     * uncaught exceptions as critical errors, chaining after (not
     * replacing) whatever handler was previously installed.
     *
     * <p>Only covers threads that don't already set their own handler -- a
     * thread pool that installs a per-thread handler (many do, to log and
     * swallow) bypasses this entirely, the same way an application's own
     * try/catch around a job runner would.
     */
    public void installUncaughtExceptionHandler() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            captureException(throwable, null).thenAccept(v -> {
                if (previous != null) {
                    previous.uncaughtException(thread, throwable);
                }
            });
        });
    }

    private CompletableFuture<Void> sendToTransport(ErrorReport report) {
        return transport.send(options.getEndpoint(), report, options.getApiKey(), options.getTimeout());
    }

    private CompletableFuture<Void> sendReport(ErrorReport report) {
        return sendToTransport(report)
                .handle((result, error) -> error)
                .thenCompose(error -> {
                    if (error == null) {
                        if (options.isEnableOfflineQueue() && !queue.isEmpty()) {
                            return queue.process(this::sendToTransport);
                        }
                        return CompletableFuture.completedFuture(null);
                    }
                    if (options.isLogToConsole()) {
                        System.err.println("[Oluso] " + error.getMessage());
                    }
                    if (options.isEnableOfflineQueue()) {
                        queue.enqueue(report);
                    }
                    return CompletableFuture.<Void>completedFuture(null);
                });
    }

    private ErrorContext buildErrorContext(RequestContext request) {
        Map<String, Object> custom = new LinkedHashMap<>(scope.getCustomContext());
        RequestContext sanitizedRequest = request != null ? sanitizeRequest(request) : null;
        return new ErrorContext(
                sanitizedRequest, scope.getUserContext(), ServerContext.capture(), custom, scope.getBreadcrumbs());
    }

    private RequestContext sanitizeRequest(RequestContext request) {
        return request.withSanitized(
                sanitizer.sanitizeStringMap(request.getHeaders()),
                sanitizer.sanitizeStringMap(request.getQuery()),
                request.getBody() != null ? sanitizer.sanitizeValue(request.getBody()) : null);
    }

    private static String generateTitle(String message, String errorType) {
        String firstLine = message.lines().findFirst().orElse("").trim();
        if (firstLine.isEmpty()) {
            return errorType + " Error";
        }
        return firstLine.length() <= 100 ? firstLine : firstLine.substring(0, 97) + "...";
    }

    private static String stackTraceOf(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
