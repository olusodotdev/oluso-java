package dev.oluso.internal;

import dev.oluso.Breadcrumb;
import dev.oluso.UserContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Request-scoped breadcrumb/user/custom-context store, backed by a
 * {@link ThreadLocal}. This is the correct, idiomatic mechanism for the
 * overwhelmingly common Java web deployment shape -- one thread per request
 * (the Servlet API, classic Spring MVC, and Java's own virtual threads all
 * preserve one-scope-per-logical-request semantics with {@code ThreadLocal}).
 *
 * <p>It is <b>not</b> correct for a reactive stack that multiplexes many
 * requests onto a small, shared pool of threads (Spring WebFlux / Project
 * Reactor) -- there, a request's continuation can resume on a different
 * thread than it started on, and {@code ThreadLocal} state won't follow it.
 * Reactor has its own answer to this ({@code reactor.util.context.Context},
 * threaded explicitly through the reactive chain); a WebFlux integration
 * for Oluso would need to bridge to that rather than relying on this class.
 *
 * <p>Falls back to a single shared store outside {@link #run(Callable)}, mirroring
 * the other Oluso SDKs' flat, non-request-scoped behavior instead of
 * silently dropping the data.
 */
public final class Scope {
    private static final class ScopeData {
        final Deque<Breadcrumb> breadcrumbs = new ArrayDeque<>();
        volatile UserContext user;
        final Map<String, Object> custom = new LinkedHashMap<>();
    }

    private final ThreadLocal<ScopeData> current = new ThreadLocal<>();
    private final ScopeData globalFallback = new ScopeData();
    private final int maxBreadcrumbs;

    public Scope(int maxBreadcrumbs) {
        this.maxBreadcrumbs = maxBreadcrumbs;
    }

    private ScopeData data() {
        ScopeData data = current.get();
        return data != null ? data : globalFallback;
    }

    /**
     * Runs {@code callable} with a fresh, isolated scope on the current
     * thread, restoring whatever scope (if any) was active before -- so
     * nested calls on a pooled thread don't bleed into each other, and a
     * thread returned to a pool doesn't carry stale scope into its next
     * task.
     */
    public <T> T run(Callable<T> callable) throws Exception {
        ScopeData previous = current.get();
        current.set(new ScopeData());
        try {
            return callable.call();
        } finally {
            if (previous != null) {
                current.set(previous);
            } else {
                current.remove();
            }
        }
    }

    public void addBreadcrumb(Breadcrumb breadcrumb) {
        ScopeData data = data();
        synchronized (data) {
            data.breadcrumbs.addLast(breadcrumb);
            while (data.breadcrumbs.size() > maxBreadcrumbs) {
                data.breadcrumbs.removeFirst();
            }
        }
    }

    public void setUserContext(UserContext user) {
        data().user = user;
    }

    public void setCustomContext(String key, Object value) {
        ScopeData data = data();
        synchronized (data) {
            data.custom.put(key, value);
        }
    }

    public List<Breadcrumb> getBreadcrumbs() {
        ScopeData data = data();
        synchronized (data) {
            return List.copyOf(data.breadcrumbs);
        }
    }

    public UserContext getUserContext() {
        return data().user;
    }

    public Map<String, Object> getCustomContext() {
        ScopeData data = data();
        synchronized (data) {
            return Map.copyOf(data.custom);
        }
    }
}
