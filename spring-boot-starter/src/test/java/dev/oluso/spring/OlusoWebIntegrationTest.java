package dev.oluso.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.oluso.OlusoClient;
import dev.oluso.Breadcrumb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OlusoWebIntegrationTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OlusoWebIntegrationTest {
    private static WireMockServer server;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OlusoClient client;

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

        @Autowired
        private OlusoClient client;

        @GetMapping("/ok")
        String ok() {
            return "ok";
        }

        @GetMapping("/boom")
        String boom() {
            client.addBreadcrumb(Breadcrumb.builder("about to fail").build());
            throw new IllegalStateException("widget not found");
        }

        @GetMapping("/explicit-500")
        ResponseEntity<String> explicit500() {
            return ResponseEntity.status(500).body("nope");
        }
    }

    /**
     * {@code captureException}/{@code capture} are fire-and-forget by
     * design -- the filter/resolver never block the response on the
     * report actually landing -- so the POST to WireMock can still be in
     * flight after {@code mockMvc.perform(...)} already returned. Polls
     * briefly instead of asserting immediately.
     */
    private JsonNode reportedBody() throws Exception {
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() ->
                        assertThat(server.findAll(postRequestedFor(urlEqualTo("/report")))).hasSize(1));

        List<LoggedRequest> requests = server.findAll(postRequestedFor(urlEqualTo("/report")));
        return new ObjectMapper().readTree(requests.get(0).getBody());
    }

    @Test
    void doesNotReportOnASuccessfulRequest() throws Exception {
        mockMvc.perform(get("/ok")).andExpect(status().isOk());

        // No positive event to await here, so this settles for a short,
        // fixed window before checking -- long enough for a fire-and-forget
        // report to have landed if one were (wrongly) sent.
        Thread.sleep(300);
        assertThat(server.findAll(postRequestedFor(urlEqualTo("/report")))).isEmpty();
    }

    @Test
    void reportsAnExceptionThrownByAControllerWithBreadcrumbsAndRequestContext() throws Exception {
        // webEnvironment=MOCK has no real servlet container, so an
        // exception no HandlerExceptionResolver claims (ours reports it
        // then deliberately returns null) propagates straight back to the
        // caller here instead of becoming a real response, unlike on a
        // live server where Spring Boot's own error handling would turn it
        // into a 500. The report to the ingestion API already happened by
        // this point regardless -- that's what's under test.
        assertThatThrownBy(() -> mockMvc.perform(get("/boom")))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("widget not found");

        JsonNode body = reportedBody();
        assertThat(body.get("message").asText()).isEqualTo("widget not found");
        assertThat(body.at("/context/request/url").asText()).isEqualTo("/boom");
        assertThat(body.at("/context/request/method").asText()).isEqualTo("GET");

        JsonNode breadcrumbs = body.at("/context/breadcrumbs");
        assertThat(breadcrumbs.get(0).get("message").asText()).isEqualTo("GET /boom");
        assertThat(breadcrumbs.get(1).get("message").asText()).isEqualTo("about to fail");
    }

    @Test
    void reportsASyntheticErrorWhenAControllerReturns5xxWithoutThrowing() throws Exception {
        mockMvc.perform(get("/explicit-500")).andExpect(status().is5xxServerError());

        JsonNode body = reportedBody();
        assertThat(body.get("message").asText()).contains("500");
    }
}
