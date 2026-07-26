package dev.oluso.spring.webflux;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.oluso.Breadcrumb;
import dev.oluso.UserContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        classes = OlusoWebFluxIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class OlusoWebFluxIntegrationTest {
    private static WireMockServer server;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void startServer() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        WireMock.configureFor("localhost", server.port());
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @BeforeEach
    void resetStubs() {
        server.resetAll();
        WireMock.stubFor(post(urlEqualTo("/report")).willReturn(aResponse().withStatus(200)));
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("oluso.api-key", () -> "test-key");
        registry.add("oluso.endpoint", () -> server.baseUrl() + "/report");
        registry.add("oluso.log-to-console", () -> "false");
        registry.add("oluso.enable-offline-queue", () -> "false");
    }

    @SpringBootApplication
    @RestController
    static class TestApp {

        @GetMapping("/ok")
        Mono<String> ok() {
            return Mono.just("ok");
        }

        @GetMapping("/boom")
        Mono<String> boom() {
            return OlusoReactiveContext.addBreadcrumb(Breadcrumb.builder("about to fail").build())
                    .then(OlusoReactiveContext.setUserContext(UserContext.of("user_123")))
                    .then(Mono.error(new IllegalStateException("widget not found")));
        }

        @GetMapping("/explicit-500")
        Mono<ResponseEntity<String>> explicit500() {
            return Mono.just(ResponseEntity.status(500).body("nope"));
        }
    }

    private JsonNode reportedBody() throws Exception {
        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() ->
                        assertThat(server.findAll(postRequestedFor(urlEqualTo("/report")))).hasSize(1));

        List<LoggedRequest> requests = server.findAll(postRequestedFor(urlEqualTo("/report")));
        return new ObjectMapper().readTree(requests.get(0).getBody());
    }

    @Test
    void doesNotReportOnASuccessfulRequest() throws InterruptedException {
        webTestClient.get().uri("/ok").exchange().expectStatus().isOk();

        // No positive event to await here, so this settles for a short,
        // fixed window before checking -- long enough for a fire-and-forget
        // report to have landed if one were (wrongly) sent.
        Thread.sleep(300);
        assertThat(server.findAll(postRequestedFor(urlEqualTo("/report")))).isEmpty();
    }

    @Test
    void reportsAnExceptionWithBreadcrumbsUserAndRequestContext() throws Exception {
        webTestClient.get().uri("/boom").exchange().expectStatus().is5xxServerError();

        JsonNode body = reportedBody();
        assertThat(body.get("message").asText()).isEqualTo("widget not found");
        assertThat(body.at("/context/request/url").asText()).isEqualTo("/boom");
        assertThat(body.at("/context/user/id").asText()).isEqualTo("user_123");

        JsonNode breadcrumbs = body.at("/context/breadcrumbs");
        assertThat(breadcrumbs.get(0).get("message").asText()).isEqualTo("GET /boom");
        assertThat(breadcrumbs.get(1).get("message").asText()).isEqualTo("about to fail");
    }

    @Test
    void reportsASyntheticErrorWhenAHandlerReturns5xxWithoutThrowing() throws Exception {
        webTestClient.get().uri("/explicit-500").exchange().expectStatus().is5xxServerError();

        JsonNode body = reportedBody();
        assertThat(body.get("message").asText()).contains("500");
    }
}
