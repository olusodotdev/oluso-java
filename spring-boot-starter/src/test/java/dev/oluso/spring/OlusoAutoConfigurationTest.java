package dev.oluso.spring;

import dev.oluso.OlusoClient;
import dev.oluso.OlusoOptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OlusoAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(OlusoAutoConfiguration.class));

    @Test
    void doesNotRegisterAnyBeansWithoutAnApiKey() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(OlusoClient.class);
            assertThat(context).doesNotHaveBean(OlusoHandlerExceptionResolver.class);
        });
    }

    @Test
    void registersTheClientAndWebBeansWhenAnApiKeyIsSet() {
        contextRunner.withPropertyValues("oluso.api-key=test-key").run(context -> {
            assertThat(context).hasSingleBean(OlusoClient.class);
            assertThat(context).hasSingleBean(OlusoHandlerExceptionResolver.class);
            assertThat(context).hasBean("olusoRequestScopeFilter");
        });
    }

    @Test
    void webEnabledFalseSkipsFilterAndResolverButKeepsTheClient() {
        contextRunner
                .withPropertyValues("oluso.api-key=test-key", "oluso.web-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(OlusoClient.class);
                    assertThat(context).doesNotHaveBean(OlusoHandlerExceptionResolver.class);
                    assertThat(context).doesNotHaveBean("olusoRequestScopeFilter");
                });
    }

    @Test
    void respectsAUserSuppliedOlusoClientBean() {
        contextRunner
                .withPropertyValues("oluso.api-key=test-key")
                .withUserConfiguration(CustomClientConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(OlusoClient.class);
                    assertThat(context.getBean(OlusoClient.class)).isSameAs(CustomClientConfig.CLIENT);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomClientConfig {
        static final OlusoClient CLIENT = new OlusoClient(OlusoOptions.builder("custom-key").build());

        @Bean
        OlusoClient olusoClient() {
            return CLIENT;
        }
    }
}
