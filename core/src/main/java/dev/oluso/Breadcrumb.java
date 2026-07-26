package dev.oluso;

import java.util.Map;
import java.util.Objects;

/**
 * A single event on the trail leading up to an error (a request started, a
 * button was clicked, a query ran). Attached to the next error report, then
 * cleared. Build with {@link #builder()}; the timestamp is filled in
 * automatically at {@link Builder#build()} time, not by the caller.
 */
public final class Breadcrumb {
    private final String message;
    private final BreadcrumbLevel level;
    private final String category;
    private final Map<String, Object> data;
    private final long timestamp;

    private Breadcrumb(Builder builder) {
        this.message = builder.message;
        this.level = builder.level;
        this.category = builder.category;
        this.data = builder.data;
        this.timestamp = System.currentTimeMillis();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String message) {
        return new Builder().message(message);
    }

    public String getMessage() {
        return message;
    }

    public BreadcrumbLevel getLevel() {
        return level;
    }

    public String getCategory() {
        return category;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public static final class Builder {
        private String message;
        private BreadcrumbLevel level = BreadcrumbLevel.INFO;
        private String category;
        private Map<String, Object> data;

        private Builder() {
        }

        public Builder message(String message) {
            this.message = Objects.requireNonNull(message, "message");
            return this;
        }

        public Builder level(BreadcrumbLevel level) {
            this.level = Objects.requireNonNull(level, "level");
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder data(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        public Breadcrumb build() {
            Objects.requireNonNull(message, "message");
            return new Breadcrumb(this);
        }
    }
}
