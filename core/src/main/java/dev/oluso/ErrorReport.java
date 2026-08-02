package dev.oluso;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ErrorReport {
    private final String title;
    private final String message;
    private final String stackTrace;
    private final String environment;
    private final Severity severity;
    private final List<String> tags;
    private final String fingerprint;
    private final ErrorContext context;
    private final long timestamp;
    private final int schemaVersion;
    private final Map<String, Object> exception;
    private final Map<String, Object> sdk;

    ErrorReport(
            String title,
            String message,
            String stackTrace,
            String environment,
            Severity severity,
            List<String> tags,
            String fingerprint,
            ErrorContext context,
            long timestamp) {
        this(title, message, stackTrace, environment, severity, tags, fingerprint, context,
                timestamp, 2, null, null);
    }

    ErrorReport(
            String title, String message, String stackTrace, String environment,
            Severity severity, List<String> tags, String fingerprint, ErrorContext context,
            long timestamp, int schemaVersion, Map<String, Object> exception,
            Map<String, Object> sdk) {
        this.title = title;
        this.message = message;
        this.stackTrace = stackTrace;
        this.environment = environment;
        this.severity = severity;
        this.tags = tags;
        this.fingerprint = fingerprint;
        this.context = context;
        this.timestamp = timestamp;
        this.schemaVersion = schemaVersion;
        this.exception = exception;
        this.sdk = sdk;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    @JsonProperty("stack_trace")
    public String getStackTrace() {
        return stackTrace;
    }

    public String getEnvironment() {
        return environment;
    }

    public Severity getSeverity() {
        return severity;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public ErrorContext getContext() {
        return context;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @JsonProperty("schema_version")
    public int getSchemaVersion() { return schemaVersion; }

    public Map<String, Object> getException() { return exception; }

    public Map<String, Object> getSdk() { return sdk; }
}
