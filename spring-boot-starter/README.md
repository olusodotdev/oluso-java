# oluso-spring-boot-starter

Spring Boot auto-configuration for [Oluso](https://oluso.dev) error monitoring: request-scoped breadcrumbs and automatic exception reporting for Spring MVC (servlet-stack) applications.

## Installation

```xml
<dependency>
    <groupId>dev.oluso</groupId>
    <artifactId>oluso-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Requires Spring Boot 3.x (Java 17+) and the servlet stack (`spring-boot-starter-web`). **Not for Spring WebFlux** -- see [Compatibility](#compatibility) below.

## Usage

```yaml
# application.yml
oluso:
  api-key: your-api-key
  environment: production
```

That's it. Auto-configuration only activates once `oluso.api-key` is set -- an app with no key configured gets no Oluso beans at all, rather than a client that silently fails every send. With a key configured, you automatically get:

- An `OlusoClient` bean, with a JVM-wide uncaught exception handler installed.
- A servlet `Filter` that wraps every request in a request-scoped breadcrumb/context store (`OlusoClient.runInScope`) and adds an HTTP breadcrumb for it.
- A `HandlerExceptionResolver` that reports any exception a controller throws -- with its real stack trace, the request it happened during, and whatever breadcrumbs were recorded during that request -- then lets Spring's normal exception handling (your own `@ControllerAdvice`, the default error page, ...) run completely unchanged. It only observes the exception; it never decides how it's rendered to the client.
- A synthetic "Server error: 5xx" report when a controller returns a 5xx response *without* throwing (e.g. `ResponseEntity.status(500).build()`), since the resolver above never sees that case.

## Manual Reporting

Inject `OlusoClient` like any other bean:

```java
@RestController
class CheckoutController {

    private final OlusoClient oluso;

    CheckoutController(OlusoClient oluso) {
        this.oluso = oluso;
    }

    @PostMapping("/checkout")
    ResponseEntity<?> checkout(@RequestBody CheckoutRequest request) {
        oluso.addBreadcrumb(Breadcrumb.builder("checkout started").category("action").build());
        oluso.setUserContext(UserContext.of(request.userId()));

        try {
            doCheckout(request);
        } catch (Exception e) {
            oluso.captureException(e);
            return ResponseEntity.status(500).build();
        }

        return ResponseEntity.ok().build();
    }
}
```

Breadcrumbs added this way join whatever the request-scoping filter already recorded, since both run inside the same `runInScope` call for the request.

## Configuration Reference

```yaml
oluso:
  api-key: your-api-key          # required -- no key, no auto-configuration
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
  web-enabled: true                 # set false to skip the filter + exception resolver
```

## Compatibility

This starter is built on a servlet `Filter` and a `HandlerExceptionResolver` -- both Spring MVC (servlet-stack) concepts. It auto-detects a servlet web application (`@ConditionalOnWebApplication(type = SERVLET)`) and won't register the filter or resolver otherwise, but it also won't do anything useful in a WebFlux app -- there's no reactive equivalent here yet.

The reason isn't just "not built yet": the underlying `OlusoClient.runInScope` is backed by a `ThreadLocal`, which is correct for the servlet stack's one-thread-per-request model but breaks under WebFlux/Project Reactor, where a request's continuation can resume on a different thread than it started on. A WebFlux integration needs to thread state through Reactor's own `Context` instead, which is different enough in shape (a `WebFilter` and reactive exception handling, not a `Filter` and `HandlerExceptionResolver`) that it doesn't fit naturally into this same module -- it would be a separate one.

## License

MIT
