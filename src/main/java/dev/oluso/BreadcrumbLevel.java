package dev.oluso;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum BreadcrumbLevel {
    @JsonProperty("debug")
    DEBUG,
    @JsonProperty("info")
    INFO,
    @JsonProperty("warning")
    WARNING,
    @JsonProperty("error")
    ERROR,
}
