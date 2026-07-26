package dev.oluso.spring;

import dev.oluso.RequestContext;
import jakarta.servlet.http.HttpServletRequest;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared by {@link OlusoRequestScopeFilter} and {@link OlusoHandlerExceptionResolver}. */
final class ServletRequestContexts {

    /**
     * Request attribute {@link OlusoHandlerExceptionResolver} sets once it
     * has reported an exception, so {@link OlusoRequestScopeFilter}'s own
     * safety-net catch -- for exceptions that never reach a resolver at
     * all -- doesn't report the same exception a second time once it
     * re-propagates through the filter chain.
     */
    static final String REPORTED_ATTRIBUTE = "dev.oluso.reported";

    private ServletRequestContexts() {
    }

    static RequestContext from(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                headers.put(name, request.getHeader(name));
            }
        }

        Map<String, String> query = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                query.put(key, values[0]);
            }
        });

        return RequestContext.builder()
                .url(request.getRequestURI())
                .method(request.getMethod())
                .headers(headers)
                .query(query)
                .build();
    }

    static String stackTraceOf(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
