package dev.oluso.spring;

import dev.oluso.OlusoClient;
import dev.oluso.Severity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Captures exceptions thrown by controllers -- with the real stack trace
 * and request context attached -- then returns {@code null} so Spring's
 * normal exception handling (a {@code @ControllerAdvice}, the default
 * error page, ...) still runs unchanged. This only observes the
 * exception; it never decides how it's rendered to the client.
 *
 * <p>Ordered first ({@link Ordered#HIGHEST_PRECEDENCE}) so it sees the
 * original exception before any {@code @ExceptionHandler} has a chance to
 * translate it into a different one.
 */
public class OlusoHandlerExceptionResolver implements HandlerExceptionResolver, Ordered {
    private final OlusoClient client;

    public OlusoHandlerExceptionResolver(OlusoClient client) {
        this.client = client;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String errorType = ex.getClass().getSimpleName();
        String message = ex.getMessage() != null ? ex.getMessage() : errorType;

        client.capture(
                errorType,
                message,
                ServletRequestContexts.stackTraceOf(ex),
                severityOf(ex),
                ServletRequestContexts.from(request));
        request.setAttribute(ServletRequestContexts.REPORTED_ATTRIBUTE, Boolean.TRUE);

        return null;
    }

    /**
     * {@code null} lets {@link OlusoClient} fall back to its configured
     * default severity. Only overridden when the exception itself already
     * carries a status code, mirroring how the other Oluso SDKs derive
     * severity from the response status for framework-level errors.
     */
    private Severity severityOf(Exception ex) {
        if (ex instanceof ResponseStatusException) {
            return severityForStatus(((ResponseStatusException) ex).getStatusCode().value());
        }

        ResponseStatus annotation = ex.getClass().getAnnotation(ResponseStatus.class);
        if (annotation != null) {
            return severityForStatus(annotation.code().value());
        }

        return null;
    }

    private Severity severityForStatus(int status) {
        if (status >= 500) {
            return Severity.CRITICAL;
        }
        if (status >= 400) {
            return Severity.HIGH;
        }
        return null;
    }
}
