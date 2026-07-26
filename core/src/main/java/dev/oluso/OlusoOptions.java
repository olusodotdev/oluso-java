package dev.oluso;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Configuration for an {@link OlusoClient}. Construct with {@link #builder(String)},
 * which fills in every default; override individual fields on the builder.
 */
public final class OlusoOptions {
    static final String DEFAULT_ENDPOINT = "https://api.oluso.dev/api/v1/error/report";

    private final String apiKey;
    private final String endpoint;
    private final String environment;
    private final Severity defaultSeverity;
    private final List<String> tags;
    private final Duration timeout;
    private final boolean logToConsole;
    private final int maxBreadcrumbs;
    private final boolean enableOfflineQueue;
    private final int maxQueueSize;
    private final int maxErrorsPerMinute;
    private final List<String> sensitiveKeys;
    private final Predicate<String> shouldReport;
    private final BiFunction<String, ErrorContext, String> fingerprint;

    private OlusoOptions(Builder builder) {
        this.apiKey = Objects.requireNonNull(builder.apiKey, "apiKey");
        this.endpoint = builder.endpoint;
        this.environment = builder.environment;
        this.defaultSeverity = builder.defaultSeverity;
        this.tags = builder.tags;
        this.timeout = builder.timeout;
        this.logToConsole = builder.logToConsole;
        this.maxBreadcrumbs = builder.maxBreadcrumbs;
        this.enableOfflineQueue = builder.enableOfflineQueue;
        this.maxQueueSize = builder.maxQueueSize;
        this.maxErrorsPerMinute = builder.maxErrorsPerMinute;
        this.sensitiveKeys = builder.sensitiveKeys;
        this.shouldReport = builder.shouldReport;
        this.fingerprint = builder.fingerprint;
    }

    public static Builder builder(String apiKey) {
        return new Builder(apiKey);
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getEnvironment() {
        return environment;
    }

    public Severity getDefaultSeverity() {
        return defaultSeverity;
    }

    public List<String> getTags() {
        return tags;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public boolean isLogToConsole() {
        return logToConsole;
    }

    public int getMaxBreadcrumbs() {
        return maxBreadcrumbs;
    }

    public boolean isEnableOfflineQueue() {
        return enableOfflineQueue;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public int getMaxErrorsPerMinute() {
        return maxErrorsPerMinute;
    }

    public List<String> getSensitiveKeys() {
        return sensitiveKeys;
    }

    public Predicate<String> getShouldReport() {
        return shouldReport;
    }

    public BiFunction<String, ErrorContext, String> getFingerprint() {
        return fingerprint;
    }

    public static final class Builder {
        private final String apiKey;
        private String endpoint = DEFAULT_ENDPOINT;
        private String environment = "production";
        private Severity defaultSeverity = Severity.MEDIUM;
        private List<String> tags = new ArrayList<>();
        private Duration timeout = Duration.ofSeconds(5);
        private boolean logToConsole = true;
        private int maxBreadcrumbs = 30;
        private boolean enableOfflineQueue = true;
        private int maxQueueSize = 100;
        private int maxErrorsPerMinute = 60;
        private List<String> sensitiveKeys = new ArrayList<>();
        private Predicate<String> shouldReport;
        private BiFunction<String, ErrorContext, String> fingerprint;

        private Builder(String apiKey) {
            this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder defaultSeverity(Severity defaultSeverity) {
            this.defaultSeverity = defaultSeverity;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder logToConsole(boolean logToConsole) {
            this.logToConsole = logToConsole;
            return this;
        }

        public Builder maxBreadcrumbs(int maxBreadcrumbs) {
            this.maxBreadcrumbs = maxBreadcrumbs;
            return this;
        }

        public Builder enableOfflineQueue(boolean enableOfflineQueue) {
            this.enableOfflineQueue = enableOfflineQueue;
            return this;
        }

        public Builder maxQueueSize(int maxQueueSize) {
            this.maxQueueSize = maxQueueSize;
            return this;
        }

        public Builder maxErrorsPerMinute(int maxErrorsPerMinute) {
            this.maxErrorsPerMinute = maxErrorsPerMinute;
            return this;
        }

        public Builder sensitiveKeys(List<String> sensitiveKeys) {
            this.sensitiveKeys = sensitiveKeys;
            return this;
        }

        public Builder shouldReport(Predicate<String> shouldReport) {
            this.shouldReport = shouldReport;
            return this;
        }

        public Builder fingerprint(BiFunction<String, ErrorContext, String> fingerprint) {
            this.fingerprint = fingerprint;
            return this;
        }

        public OlusoOptions build() {
            return new OlusoOptions(this);
        }
    }
}
