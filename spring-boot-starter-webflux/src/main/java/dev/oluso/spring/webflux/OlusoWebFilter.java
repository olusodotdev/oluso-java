package dev.oluso.spring.webflux;

import dev.oluso.Breadcrumb;
import dev.oluso.OlusoClient;
import dev.oluso.RequestContext;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates a fresh {@link RequestScopeHolder} for each request and threads
 * it through the reactive chain via Reactor {@code Context} (see {@link
 * OlusoReactiveContext}), adding an HTTP breadcrumb for the request itself.
 *
 * <p>Also reports a synthetic "Server error: 5xx" when a handler completes
 * with a 5xx response without an exception propagating (e.g. {@code
 * ServerResponse.status(500).build()}), and reports any exception that
 * escapes the filter chain.
 *
 * <p>Unlike the servlet-stack starter's {@code Filter}/{@code
 * HandlerExceptionResolver} pair, there's no separate resolver here and no
 * duplicate-report bookkeeping needed: Spring Boot's own reactive error
 * handling ({@code DefaultErrorWebExceptionHandler}) wraps *around* the
 * entire {@code WebFilter} chain rather than running inside it the way
 * Spring MVC's {@code HandlerExceptionResolver}s do -- so this filter's
 * {@code doOnError} is the first and only place an unhandled exception is
 * ever observed here, before Boot's default handling converts it into a
 * response.
 */
public class OlusoWebFilter implements WebFilter, Ordered {
    private final OlusoClient client;
    private final int maxBreadcrumbs;

    public OlusoWebFilter(OlusoClient client, int maxBreadcrumbs) {
        this.client = client;
        this.maxBreadcrumbs = maxBreadcrumbs;
    }

    /**
     * Runs first, so its {@code contextWrite} covers as much of the
     * downstream filter chain and handler dispatch as possible -- any
     * filter registered before this one wouldn't have the request-scoped
     * holder available even if it tried to use {@link OlusoReactiveContext}.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        RequestScopeHolder holder = new RequestScopeHolder(maxBreadcrumbs);
        ServerHttpRequest request = exchange.getRequest();
        holder.addBreadcrumb(Breadcrumb.builder(String.valueOf(request.getMethod()) + " " + request.getPath().value())
                .category("http")
                .build());

        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    if (status != null && status.value() >= 500) {
                        String message = "Server error: " + status.value() + " - " + request.getMethod() + " "
                                + request.getPath().value();
                        client.capture(
                                "RuntimeException", message, null, null, requestContextOf(exchange), holder.snapshot());
                    }
                })
                .doOnError(throwable -> {
                    client.captureException(throwable, requestContextOf(exchange), holder.snapshot());
                })
                .contextWrite(ctx -> ctx.put(OlusoReactiveContext.CONTEXT_KEY, holder));
    }

    private RequestContext requestContextOf(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        Map<String, String> headers = new LinkedHashMap<>();
        request.getHeaders().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name, values.get(0));
            }
        });

        Map<String, String> query = new LinkedHashMap<>();
        request.getQueryParams().forEach((name, values) -> {
            if (!values.isEmpty()) {
                query.put(name, values.get(0));
            }
        });

        return RequestContext.builder()
                .url(request.getPath().value())
                .method(String.valueOf(request.getMethod()))
                .headers(headers)
                .query(query)
                .build();
    }
}
