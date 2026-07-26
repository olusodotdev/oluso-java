package dev.oluso;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Info about the request in flight when the error was captured. There's no
 * framework-specific builder for this in the core library -- a framework
 * integration (a Servlet filter, a Spring interceptor, ...) constructs one
 * from whatever request type it has.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RequestContext {
    private final String url;
    private final String method;
    private final Map<String, String> headers;
    private final Map<String, String> query;
    private final Object body;

    private RequestContext(Builder builder) {
        this.url = builder.url;
        this.method = builder.method;
        this.headers = builder.headers;
        this.query = builder.query;
        this.body = builder.body;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, String> getQuery() {
        return query;
    }

    public Object getBody() {
        return body;
    }

    RequestContext withSanitized(Map<String, String> headers, Map<String, String> query, Object body) {
        Builder builder = builder().url(url).method(method).headers(headers).query(query);
        if (body != null) {
            builder.body(body);
        }
        return builder.build();
    }

    public static final class Builder {
        private String url;
        private String method;
        private Map<String, String> headers;
        private Map<String, String> query;
        private Object body;

        private Builder() {
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder query(Map<String, String> query) {
            this.query = query;
            return this;
        }

        public Builder body(Object body) {
            this.body = body;
            return this;
        }

        public RequestContext build() {
            return new RequestContext(this);
        }
    }
}
