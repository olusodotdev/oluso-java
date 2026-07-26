package dev.oluso;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An explicit, pre-built breadcrumb/user/custom-context bundle for
 * integrations that maintain their own scoping instead of {@link
 * OlusoClient}'s built-in {@code ThreadLocal}-based one -- a WebFlux
 * integration threading state through Reactor's {@code Context}, for
 * example, where thread-hopping means the synchronous {@link
 * OlusoClient#runInScope} can't be used correctly. Pass one to {@link
 * OlusoClient#capture(String, String, String, Severity, RequestContext,
 * ScopeSnapshot)} to report using this data instead of whatever's in the
 * internal scope.
 */
public final class ScopeSnapshot {
    private final List<Breadcrumb> breadcrumbs;
    private final UserContext user;
    private final Map<String, Object> custom;

    private ScopeSnapshot(Builder builder) {
        this.breadcrumbs = List.copyOf(builder.breadcrumbs);
        this.user = builder.user;
        this.custom = Map.copyOf(builder.custom);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ScopeSnapshot empty() {
        return builder().build();
    }

    public List<Breadcrumb> getBreadcrumbs() {
        return breadcrumbs;
    }

    public UserContext getUser() {
        return user;
    }

    public Map<String, Object> getCustom() {
        return custom;
    }

    public static final class Builder {
        private List<Breadcrumb> breadcrumbs = List.of();
        private UserContext user;
        private Map<String, Object> custom = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder breadcrumbs(List<Breadcrumb> breadcrumbs) {
            this.breadcrumbs = breadcrumbs;
            return this;
        }

        public Builder user(UserContext user) {
            this.user = user;
            return this;
        }

        public Builder custom(Map<String, Object> custom) {
            this.custom = custom;
            return this;
        }

        public ScopeSnapshot build() {
            return new ScopeSnapshot(this);
        }
    }
}
