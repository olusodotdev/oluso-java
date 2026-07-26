# oluso-spring-boot-starter-webflux

Spring Boot auto-configuration for [Oluso](https://oluso.dev) error monitoring on the reactive stack: Reactor `Context`-scoped breadcrumbs and automatic exception reporting for Spring WebFlux applications.

For the servlet-stack (Spring MVC) equivalent, see [`oluso-spring-boot-starter`](../spring-boot-starter).

## Installation

```xml
<dependency>
    <groupId>dev.oluso</groupId>
    <artifactId>oluso-spring-boot-starter-webflux</artifactId>
    <version>0.2.0</version>
</dependency>
```

Requires Spring Boot 3.x (Java 17+) and `spring-boot-starter-webflux`.

## Usage

```yaml
# application.yml
oluso:
  api-key: your-api-key
  environment: production
```

Auto-configuration only activates once `oluso.api-key` is set. With a key configured, you get:

- An `OlusoClient` bean, with a JVM-wide uncaught exception handler installed.
- An `OlusoWebFilter` that creates a request-scoped breadcrumb/context holder threaded through the reactive chain via Reactor's `Context` (not a `ThreadLocal` -- see [Why not `ThreadLocal`](#why-not-threadlocal) below), adds an HTTP breadcrumb, reports any exception that escapes the filter chain with its real stack trace and the request it happened during, and reports a synthetic "Server error: 5xx" when a handler returns a 5xx response without throwing.

## Breadcrumbs & User Context

Unlike the servlet-stack starter's plain, imperative `client.addBreadcrumb(...)`, per-request state here has to flow through the reactive chain itself -- that's exactly the property that makes it correct across thread hops, and there's no way around it without falling back to the `ThreadLocal` this module exists to avoid. Compose these into your chain with `.then(...)`:

```java
import dev.oluso.spring.webflux.OlusoReactiveContext;

@GetMapping("/checkout")
Mono<ResponseEntity<?>> checkout(@RequestBody CheckoutRequest request) {
    return OlusoReactiveContext.addBreadcrumb(Breadcrumb.builder("checkout started").category("action").build())
            .then(OlusoReactiveContext.setUserContext(UserContext.of(request.userId())))
            .then(doCheckout(request))
            .map(result -> ResponseEntity.ok().build())
            .onErrorResume(e -> {
                client.captureException(e);
                return Mono.just(ResponseEntity.status(500).build());
            });
}
```

A call made outside a request this module scoped (no `OlusoWebFilter` upstream in the chain) is a no-op -- there's no holder in the Reactor `Context` to record it against.

For a one-off manual report you don't need scoped into the reactive chain, `OlusoClient.captureException(...)`/`captureMessage(...)` still work exactly as documented in the core library; they just won't have this request's breadcrumbs attached unless you're inside `OlusoWebFilter`'s own error path, which already handles that for you.

## Why not `ThreadLocal`?

The servlet-stack starter's request scoping is `ThreadLocal`-based, which is correct there because one thread handles one request start-to-finish. WebFlux breaks that assumption on purpose -- a request's processing can resume on a different thread after each asynchronous boundary, so anything read from a `ThreadLocal` partway through would be unrelated leftover state from whatever request last touched that thread, not this request's real context.

This module solves it the way Reactor itself recommends: a small, plain mutable holder (not a `ThreadLocal`) is created once per request and threaded through the chain as a single Reactor `Context` value via `contextWrite`. The `Context` mapping itself is immutable, but the object it points to is free to mutate internally, which is what lets breadcrumbs accumulate onto the same instance regardless of which thread each step in the chain happens to run on.

## Configuration Reference

Same property names as the servlet-stack starter (`web-enabled` here gates the `WebFilter` instead of a `Filter` + `HandlerExceptionResolver` pair):

```yaml
oluso:
  api-key: your-api-key
  endpoint: https://api.oluso.dev/api/v1/error/report  # override for self-hosting
  environment: production
  default-severity: medium        # critical | high | medium | low
  tags: [checkout-service]
  timeout: 5s
  log-to-console: true
  max-breadcrumbs: 30
  enable-offline-queue: true
  max-queue-size: 100
  max-errors-per-minute: 60
  sensitive-keys: [internalId]
  uncaught-exception-handler: true  # set false to skip installing the JVM-wide handler
  web-enabled: true                 # set false to skip the OlusoWebFilter
```

## License

MIT
