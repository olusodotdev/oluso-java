# oluso

AI-powered error monitoring for Java applications: automatic uncaught-exception reporting, breadcrumb tracking, and intelligent error grouping.

## Installation

```xml
<dependency>
    <groupId>dev.oluso</groupId>
    <artifactId>oluso</artifactId>
    <version>0.1.0</version>
</dependency>
```

Requires Java 11+. Built on `java.net.http.HttpClient` (no HTTP client dependency) and Jackson for JSON.

## Usage

```java
import dev.oluso.OlusoClient;
import dev.oluso.OlusoOptions;

OlusoClient client = new OlusoClient(OlusoOptions.builder("your-api-key").build());
client.installUncaughtExceptionHandler();

try {
    doWork();
} catch (Exception e) {
    client.captureException(e);
}
```

Every `capture*` method returns a `CompletableFuture<Void>` rather than blocking the calling thread -- reporting an error should never add latency to whatever request or job triggered it. Call `.join()` if you specifically need to wait for it (tests, a graceful-shutdown hook); otherwise it's safe to ignore the returned future.

`installUncaughtExceptionHandler()` reports any exception that kills a thread without being caught anywhere, chaining after (not replacing) whatever handler was previously installed. It only covers threads that don't already set their own handler -- a thread pool that installs a per-thread handler (many do, to log and swallow) bypasses this entirely, the same way an application's own try/catch around a job runner would.

## Breadcrumbs & User Context

```java
import dev.oluso.Breadcrumb;
import dev.oluso.UserContext;

client.setUserContext(UserContext.of("user_456"));
client.addBreadcrumb(Breadcrumb.builder("user started checkout").category("action").build());

try {
    doCheckout();
} catch (Exception e) {
    client.captureException(e);
}
```

### Request-scoped context

A single process handles many concurrent requests, so a plain global breadcrumb list would mix up breadcrumbs from different requests. Wrap each unit of work (a request handler, typically) in `runInScope` to isolate it:

```java
client.runInScope(() -> {
    client.addBreadcrumb(Breadcrumb.builder("request started").build());
    return handleRequest();
});
```

This is backed by a `ThreadLocal`, which is the correct, idiomatic mechanism for the overwhelmingly common Java web deployment shape: one thread per request (the Servlet API, classic Spring MVC, and Java's virtual threads all preserve this). It is **not** correct for a reactive stack that multiplexes many requests onto a small, shared pool of threads (Spring WebFlux/Project Reactor) -- there, a request's continuation can resume on a different thread than it started on, and `ThreadLocal` state won't follow it. A WebFlux integration would need to bridge to Reactor's own `Context` instead.

Calling `addBreadcrumb`/`setUserContext`/`setCustomContext` outside `runInScope` (e.g. at startup) falls back to a single shared store rather than silently dropping the data -- fine for a one-off script, not meant for concurrent request handling.

## Manual Reporting

```java
import dev.oluso.Severity;

client.captureException(err);
client.captureMessage("disk usage above 90%", Severity.HIGH);
```

### Attaching a request

There's no framework-specific `RequestContext` builder in this library -- construct one from whatever your framework hands you and attach it:

```java
import dev.oluso.RequestContext;

RequestContext request = RequestContext.builder()
        .url(httpRequest.getRequestURI())
        .method(httpRequest.getMethod())
        .headers(headersAsMap(httpRequest))
        .build();

client.captureException(err, request);
```

## Advanced Configuration

```java
import java.time.Duration;
import java.util.List;

OlusoOptions options = OlusoOptions.builder("your-api-key")
        .endpoint("https://your-self-hosted-instance.example.com/api/v1/error/report")
        .environment("staging")
        .defaultSeverity(Severity.MEDIUM)
        .maxBreadcrumbs(50)
        .maxErrorsPerMinute(100)
        .timeout(Duration.ofSeconds(10))
        .sensitiveKeys(List.of("ssn", "internal_id"))
        .shouldReport(message -> !message.contains("expected"))
        .build();
```

## Error Report Structure

Reports sent to the API include:

- **Metadata**: Title, message, stack trace, severity, tags.
- **Context**: Request details when attached, server details (hostname, OS, arch, pid, Java version).
- **History**: Breadcrumbs leading up to the error.
- **Identification**: Fingerprint for deduplication and user ID.

## License

MIT
