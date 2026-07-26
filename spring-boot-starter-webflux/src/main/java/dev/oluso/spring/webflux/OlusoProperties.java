package dev.oluso.spring.webflux;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Binds {@code oluso.*} properties from {@code application.yml}/
 * {@code .properties} to configure the auto-registered {@link
 * dev.oluso.OlusoClient}. Same shape and property names as {@code
 * oluso-spring-boot-starter}'s properties class -- kept as a separate
 * class (different package) rather than shared, since the two starters
 * are meant to be used independently and neither should need to depend on
 * the other just for this.
 */
@ConfigurationProperties(prefix = "oluso")
public class OlusoProperties {

    /**
     * API key for authentication. Auto-configuration only activates when
     * this is set -- an app with no key configured gets no Oluso beans at
     * all, rather than a client that silently fails every send.
     */
    private String apiKey;

    private String endpoint;
    private String environment = "production";
    private String defaultSeverity = "medium";
    private List<String> tags = new ArrayList<>();
    private Duration timeout = Duration.ofSeconds(5);
    private boolean logToConsole = true;
    private int maxBreadcrumbs = 30;
    private boolean enableOfflineQueue = true;
    private int maxQueueSize = 100;
    private int maxErrorsPerMinute = 60;
    private List<String> sensitiveKeys = new ArrayList<>();

    /** Whether to install a JVM-wide uncaught exception handler on startup. */
    private boolean uncaughtExceptionHandler = true;

    /** Whether to register the request-scoping WebFilter. */
    private boolean webEnabled = true;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getDefaultSeverity() {
        return defaultSeverity;
    }

    public void setDefaultSeverity(String defaultSeverity) {
        this.defaultSeverity = defaultSeverity;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public boolean isLogToConsole() {
        return logToConsole;
    }

    public void setLogToConsole(boolean logToConsole) {
        this.logToConsole = logToConsole;
    }

    public int getMaxBreadcrumbs() {
        return maxBreadcrumbs;
    }

    public void setMaxBreadcrumbs(int maxBreadcrumbs) {
        this.maxBreadcrumbs = maxBreadcrumbs;
    }

    public boolean isEnableOfflineQueue() {
        return enableOfflineQueue;
    }

    public void setEnableOfflineQueue(boolean enableOfflineQueue) {
        this.enableOfflineQueue = enableOfflineQueue;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public int getMaxErrorsPerMinute() {
        return maxErrorsPerMinute;
    }

    public void setMaxErrorsPerMinute(int maxErrorsPerMinute) {
        this.maxErrorsPerMinute = maxErrorsPerMinute;
    }

    public List<String> getSensitiveKeys() {
        return sensitiveKeys;
    }

    public void setSensitiveKeys(List<String> sensitiveKeys) {
        this.sensitiveKeys = sensitiveKeys;
    }

    public boolean isUncaughtExceptionHandler() {
        return uncaughtExceptionHandler;
    }

    public void setUncaughtExceptionHandler(boolean uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = uncaughtExceptionHandler;
    }

    public boolean isWebEnabled() {
        return webEnabled;
    }

    public void setWebEnabled(boolean webEnabled) {
        this.webEnabled = webEnabled;
    }
}
