package dev.oluso.spring.webflux;

import dev.oluso.OlusoClient;
import dev.oluso.OlusoOptions;
import dev.oluso.Severity;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Locale;

/**
 * Auto-configures an {@link OlusoClient} bean from {@code oluso.*}
 * properties, and -- for reactive-stack (Spring WebFlux) applications -- a
 * request-scoping {@link OlusoWebFilter}. Only activates when {@code
 * oluso.api-key} is set; an app with no key configured gets no Oluso beans
 * at all rather than a client that silently fails every send.
 */
@AutoConfiguration
@EnableConfigurationProperties(OlusoProperties.class)
@ConditionalOnProperty(prefix = "oluso", name = "api-key")
public class OlusoReactiveAutoConfiguration {

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
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnProperty(prefix = "oluso", name = "web-enabled", matchIfMissing = true)
    public OlusoWebFilter olusoWebFilter(OlusoClient client, OlusoProperties properties) {
        return new OlusoWebFilter(client, properties.getMaxBreadcrumbs());
    }

    private static Severity parseSeverity(String value) {
        try {
            return Severity.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Severity.MEDIUM;
        }
    }
}
