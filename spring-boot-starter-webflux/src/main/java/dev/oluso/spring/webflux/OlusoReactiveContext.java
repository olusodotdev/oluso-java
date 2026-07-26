package dev.oluso.spring.webflux;

import dev.oluso.Breadcrumb;
import dev.oluso.UserContext;
import reactor.core.publisher.Mono;

/**
 * Records breadcrumbs/user/custom context against the current request's
 * {@link RequestScopeHolder}, threaded through the reactive chain via
 * Reactor's {@code Context} rather than a {@code ThreadLocal} -- the
 * WebFlux counterpart to {@code OlusoClient.addBreadcrumb}/{@code
 * setUserContext}/{@code setCustomContext} in the servlet-stack starter.
 *
 * <p>Unlike the imperative, fire-and-forget calls those methods offer,
 * these return a {@code Mono<Void>} you compose into your chain (typically
 * via {@code .then(...)}) -- there's no way around that here: reading
 * "ambient" per-request state in Reactor only works through the reactive
 * chain itself, which is exactly the property that makes it correct across
 * thread hops in the first place. A plain synchronous method faking the
 * same ergonomics would have to fall back to a {@code ThreadLocal}, which
 * is the exact bug this module exists to avoid.
 *
 * <pre>{@code
 * @GetMapping("/checkout")
 * Mono<String> checkout() {
 *     return OlusoReactiveContext.addBreadcrumb(Breadcrumb.builder("checkout started").build())
 *             .then(doCheckout());
 * }
 * }</pre>
 *
 * <p>A call made outside a request this module scoped (no {@link
 * OlusoWebFilter} upstream) is a no-op -- there's no holder in the Reactor
 * {@code Context} to record it against.
 */
public final class OlusoReactiveContext {
    static final Class<RequestScopeHolder> CONTEXT_KEY = RequestScopeHolder.class;

    private OlusoReactiveContext() {
    }

    public static Mono<Void> addBreadcrumb(Breadcrumb breadcrumb) {
        return withHolder(holder -> holder.addBreadcrumb(breadcrumb));
    }

    public static Mono<Void> setUserContext(UserContext user) {
        return withHolder(holder -> holder.setUserContext(user));
    }

    public static Mono<Void> setCustomContext(String key, Object value) {
        return withHolder(holder -> holder.setCustomContext(key, value));
    }

    private static Mono<Void> withHolder(java.util.function.Consumer<RequestScopeHolder> action) {
        return Mono.deferContextual(contextView -> {
            RequestScopeHolder holder = contextView.getOrDefault(CONTEXT_KEY, null);
            if (holder != null) {
                action.accept(holder);
            }
            return Mono.empty();
        });
    }
}
