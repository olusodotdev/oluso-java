package dev.oluso;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How severe a captured error is. Serialized as the lowercase string values
 * (`critical`/`high`/`medium`/`low`) the other Oluso SDKs send, so the
 * ingestion API treats reports from every language the same way.
 */
public enum Severity {
    @JsonProperty("critical")
    CRITICAL,
    @JsonProperty("high")
    HIGH,
    @JsonProperty("medium")
    MEDIUM,
    @JsonProperty("low")
    LOW,
}
