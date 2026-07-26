package dev.oluso.spring;

import dev.oluso.OlusoClient;
import dev.oluso.OlusoOptions;
import dev.oluso.Severity;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Locale;

/**
 * Auto-configures an {@link OlusoClient} bean from {@code oluso.*}
 * properties, and -- for servlet-stack (Spring MVC) applications -- a
 * request-scoping filter and exception resolver. Only activates when
 * {@code oluso.api-key} is set; an app with no key configured gets no
 * Oluso beans at all rather than a client that silently fails every send.
 */
@AutoConfiguration
@EnableConfigurationProperties(OlusoProperties.class)
@ConditionalOnProperty(prefix = "oluso", name = "api-key")
public class OlusoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OlusoClient olusoClient(OlusoProperties properties) {
        OlusoOptions.Builder builder = OlusoOptions.builder(properties.getApiKey())
                .environment(properties.getEnvironment())
                .defaultSeverity(parseSeverity(properties.getDefaultSeverity()))
                .tags(properties.getTags())
                .timeout(properties.getTimeout())
                .logToConsole(properties.isLogToConsole())
                .maxBreadcrumbs(properties.getMaxBreadcrumbs())
                .enableOfflineQueue(properties.isEnableOfflineQueue())
                .maxQueueSize(properties.getMaxQueueSize())
                .maxErrorsPerMinute(properties.getMaxErrorsPerMinute())
                .sensitiveKeys(properties.getSensitiveKeys());

        if (properties.getEndpoint() != null) {
            builder.endpoint(properties.getEndpoint());
        }

        OlusoClient client = new OlusoClient(builder.build());
        if (properties.isUncaughtExceptionHandler()) {
            client.installUncaughtExceptionHandler();
        }
        return client;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "oluso", name = "web-enabled", matchIfMissing = true)
    public OlusoHandlerExceptionResolver olusoHandlerExceptionResolver(OlusoClient client) {
        return new OlusoHandlerExceptionResolver(client);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "oluso", name = "web-enabled", matchIfMissing = true)
    public FilterRegistrationBean<OlusoRequestScopeFilter> olusoRequestScopeFilter(OlusoClient client) {
        FilterRegistrationBean<OlusoRequestScopeFilter> registration =
                new FilterRegistrationBean<>(new OlusoRequestScopeFilter(client));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    private static Severity parseSeverity(String value) {
        try {
            return Severity.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Severity.MEDIUM;
        }
    }

    /**
     * A plain {@code @Bean} of type {@link HandlerExceptionResolver} is
     * <em>not</em> automatically picked up into the resolver chain Spring
     * Boot's auto-configured {@code DispatcherServlet} actually uses --
     * {@code WebMvcConfigurationSupport} assembles its own explicit list
     * (defaults plus whatever {@link WebMvcConfigurer#extendHandlerExceptionResolvers}
     * contributes) rather than scanning the context for every bean of that
     * type. This is the extra wiring that actually gets it into the chain.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "oluso", name = "web-enabled", matchIfMissing = true)
    @ConditionalOnBean(OlusoHandlerExceptionResolver.class)
    static class ExceptionResolverWiring implements WebMvcConfigurer {
        private final OlusoHandlerExceptionResolver resolver;

        ExceptionResolverWiring(OlusoHandlerExceptionResolver resolver) {
            this.resolver = resolver;
        }

        @Override
        public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
            resolvers.add(0, resolver);
        }
    }
}
