package dev.oluso.spring.webflux;

import dev.oluso.Breadcrumb;
import dev.oluso.ScopeSnapshot;
import dev.oluso.UserContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A plain, thread-safe mutable holder -- no {@code ThreadLocal} involved.
 * One instance is created per request by {@link OlusoWebFilter} and
 * threaded through the reactive chain as a single Reactor {@code Context}
 * value (see {@link OlusoReactiveContext}); Reactor's {@code Context}
 * mapping itself is immutable, but the value it points to is free to
 * mutate internally; that's what this class exists to do, so breadcrumbs
 * added at any point in the chain accumulate onto the one instance for
 * that request, regardless of which thread each step runs on.
 */
final class RequestScopeHolder {
    private final Deque<Breadcrumb> breadcrumbs = new ArrayDeque<>();
    private final Map<String, Object> custom = new LinkedHashMap<>();
    private volatile UserContext user;
    private final int maxBreadcrumbs;

    RequestScopeHolder(int maxBreadcrumbs) {
        this.maxBreadcrumbs = maxBreadcrumbs;
    }

    synchronized void addBreadcrumb(Breadcrumb breadcrumb) {
        breadcrumbs.addLast(breadcrumb);
        while (breadcrumbs.size() > maxBreadcrumbs) {
            breadcrumbs.removeFirst();
        }
    }

    void setUserContext(UserContext user) {
        this.user = user;
    }

    synchronized void setCustomContext(String key, Object value) {
        custom.put(key, value);
    }

    synchronized ScopeSnapshot snapshot() {
        return ScopeSnapshot.builder()
                .breadcrumbs(List.copyOf(breadcrumbs))
                .user(user)
                .custom(new LinkedHashMap<>(custom))
                .build();
    }
}
