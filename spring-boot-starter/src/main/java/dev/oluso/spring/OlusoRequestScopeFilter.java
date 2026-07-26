package dev.oluso.spring;

import dev.oluso.Breadcrumb;
import dev.oluso.OlusoClient;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Wraps each request in {@link OlusoClient#runInScope}, so breadcrumbs
 * added anywhere during the request (a service layer, a repository, ...)
 * are scoped to it rather than leaking into whichever other request
 * happens to run next on the same pooled thread.
 *
 * <p>Also reports a synthetic "Server error: 5xx" when a controller
 * returns a 5xx response without throwing (e.g. {@code
 * ResponseEntity.status(500).build()}), and -- as a safety net -- reports
 * any exception that escapes the whole filter chain, including one no
 * {@code HandlerExceptionResolver} claimed. The primary exception-capture
 * path is {@link OlusoHandlerExceptionResolver}, which sees the exception
 * with its real stack trace while Spring is still trying to resolve it
 * into a response; when nothing resolves it, the same exception then
 * propagates back through here too. It marks the request (via {@link
 * ServletRequestContexts#REPORTED_ATTRIBUTE}) once it has already
 * reported one, so this filter's catch only reports exceptions that
 * never reached a resolver at all, rather than reporting the same one
 * twice.
 */
public class OlusoRequestScopeFilter implements Filter {
    private final OlusoClient client;

    public OlusoRequestScopeFilter(OlusoClient client) {
        this.client = client;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            client.runInScope(() -> {
                client.addBreadcrumb(Breadcrumb.builder(httpRequest.getMethod() + " " + httpRequest.getRequestURI())
                        .category("http")
                        .build());

                try {
                    chain.doFilter(request, response);
                } catch (Exception e) {
                    if (!Boolean.TRUE.equals(httpRequest.getAttribute(ServletRequestContexts.REPORTED_ATTRIBUTE))) {
                        client.captureException(e, ServletRequestContexts.from(httpRequest));
                    }
                    throw e;
                }

                if (httpResponse.getStatus() >= 500) {
                    RuntimeException syntheticError = new RuntimeException("Server error: " + httpResponse.getStatus()
                            + " - " + httpRequest.getMethod() + " " + httpRequest.getRequestURI());
                    client.captureException(syntheticError, ServletRequestContexts.from(httpRequest));
                }

                return null;
            });
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
