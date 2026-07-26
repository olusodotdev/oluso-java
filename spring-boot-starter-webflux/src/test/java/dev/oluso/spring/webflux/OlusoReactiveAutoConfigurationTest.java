package dev.oluso.spring.webflux;

import dev.oluso.OlusoClient;
import dev.oluso.OlusoOptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OlusoReactiveAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OlusoReactiveAutoConfiguration.class));

    @Test
    void doesNotRegisterAnyBeansWithoutAnApiKey() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(OlusoClient.class);
            assertThat(context).doesNotHaveBean(OlusoWebFilter.class);
        });
    }

    @Test
    void registersTheClientAndFilterWhenAnApiKeyIsSet() {
        contextRunner.withPropertyValues("oluso.api-key=test-key").run(context -> {
            assertThat(context).hasSingleBean(OlusoClient.class);
            assertThat(context).hasSingleBean(OlusoWebFilter.class);
        });
    }

    @Test
    void webEnabledFalseSkipsTheFilterButKeepsTheClient() {
        contextRunner
                .withPropertyValues("oluso.api-key=test-key", "oluso.web-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(OlusoClient.class);
                    assertThat(context).doesNotHaveBean(OlusoWebFilter.class);
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
