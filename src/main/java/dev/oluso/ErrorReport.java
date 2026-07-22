package dev.oluso;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

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
        this.title = title;
        this.message = message;
        this.stackTrace = stackTrace;
        this.environment = environment;
        this.severity = severity;
        this.tags = tags;
        this.fingerprint = fingerprint;
        this.context = context;
        this.timestamp = timestamp;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

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
}
