package dev.oluso;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ErrorContext {
    private final RequestContext request;
    private final UserContext user;
    private final ServerContext server;
    private final Map<String, Object> custom;
    private final List<Breadcrumb> breadcrumbs;

    ErrorContext(
            RequestContext request,
            UserContext user,
            ServerContext server,
            Map<String, Object> custom,
            List<Breadcrumb> breadcrumbs) {
        this.request = request;
        this.user = user;
        this.server = server;
        this.custom = custom;
        this.breadcrumbs = breadcrumbs;
    }

    public RequestContext getRequest() {
        return request;
    }

    public UserContext getUser() {
        return user;
    }

    public ServerContext getServer() {
        return server;
    }

    public Map<String, Object> getCustom() {
        return custom;
    }

    public List<Breadcrumb> getBreadcrumbs() {
        return breadcrumbs;
    }
}
