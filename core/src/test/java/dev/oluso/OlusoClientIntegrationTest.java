package dev.oluso;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OlusoClientIntegrationTest {
    private WireMockServer server;
    private ObjectMapper objectMapper;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        WireMock.configureFor("localhost", server.port());
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    private OlusoOptions.Builder optionsBuilder() {
        return OlusoOptions.builder("test-api-key")
                .endpoint(server.baseUrl() + "/report")
                .environment("test")
                .logToConsole(false);
    }

    private JsonNode bodyOf(LoggedRequest request) throws Exception {
        return objectMapper.readTree(request.getBody());
    }

    @Test
    void sendsACapturedExceptionWithTheSignatureHeader() {
        stubFor(WireMock.post(urlEqualTo("/report"))
                .withHeader("x-oluso-signature", equalTo("test-api-key"))
                .willReturn(aResponse().withStatus(200)));

        OlusoClient client = new OlusoClient(optionsBuilder().enableOfflineQueue(false).build());
        client.captureException(new IllegalStateException("widget not found")).join();

        server.verify(postRequestedFor(urlEqualTo("/report")));
    }

    @Test
    void queuesAndFlushesOnALaterSuccess() {
        stubFor(WireMock.post(urlEqualTo("/report"))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("recovered"));
        stubFor(WireMock.post(urlEqualTo("/report"))
                .inScenario("retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200)));

        OlusoClient client = new OlusoClient(optionsBuilder().enableOfflineQueue(true).build());
        client.captureException(new RuntimeException("first failure")).join();

        client.flush().join();
        server.verify(2, postRequestedFor(urlEqualTo("/report")));
    }

    @Test
    void attachesBreadcrumbsRecordedInsideRunInScope() throws Exception {
        stubFor(WireMock.post(urlEqualTo("/report")).willReturn(aResponse().withStatus(200)));

        OlusoClient client = new OlusoClient(optionsBuilder().enableOfflineQueue(false).build());
        client.runInScope(() -> {
            client.addBreadcrumb(Breadcrumb.builder("checkout started").build());
            client.setUserContext(UserContext.of("user_123"));
            return client.captureException(new RuntimeException("checkout failed")).join();
        });

        List<LoggedRequest> requests = server.findAll(postRequestedFor(urlEqualTo("/report")));
        assertEquals(1, requests.size());
        JsonNode body = bodyOf(requests.get(0));
        assertEquals("checkout started", body.at("/context/breadcrumbs/0/message").asText());
        assertEquals("user_123", body.at("/context/user/id").asText());
    }

    @Test
    void sanitizesRequestHeadersBeforeSending() throws Exception {
        stubFor(WireMock.post(urlEqualTo("/report")).willReturn(aResponse().withStatus(200)));

        OlusoClient client = new OlusoClient(optionsBuilder().enableOfflineQueue(false).build());
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", "Bearer secret");
        headers.put("accept", "application/json");

        RequestContext request = RequestContext.builder()
                .url("/api/widgets")
                .method("POST")
                .headers(headers)
                .build();

        client.captureException(new RuntimeException("boom"), request).join();

        List<LoggedRequest> requests = server.findAll(postRequestedFor(urlEqualTo("/report")));
        JsonNode body = bodyOf(requests.get(0));
        assertEquals("[REDACTED]", body.at("/context/request/headers/authorization").asText());
        assertEquals("application/json", body.at("/context/request/headers/accept").asText());
    }

    @Test
    void doesNotSendWhenShouldReportReturnsFalse() {
        stubFor(WireMock.post(urlEqualTo("/report")).willReturn(aResponse().withStatus(200)));

        OlusoClient client = new OlusoClient(optionsBuilder()
                .enableOfflineQueue(false)
                .shouldReport(message -> !message.contains("ignore me"))
                .build());

        client.captureException(new RuntimeException("ignore me please")).join();

        server.verify(0, postRequestedFor(urlEqualTo("/report")));
    }

    @Test
    void captureMessageReportsWithTheGivenSeverity() throws Exception {
        stubFor(WireMock.post(urlEqualTo("/report")).willReturn(aResponse().withStatus(200)));

        OlusoClient client = new OlusoClient(optionsBuilder().enableOfflineQueue(false).build());
        client.captureMessage("disk usage above 90%", Severity.HIGH).join();

        List<LoggedRequest> requests = server.findAll(postRequestedFor(urlEqualTo("/report")));
        JsonNode body = bodyOf(requests.get(0));
        assertEquals("high", body.get("severity").asText());
        assertEquals("disk usage above 90%", body.get("message").asText());
    }

    @Test
    void ratesLimitsExcessiveReports() {
        stubFor(WireMock.post(urlEqualTo("/report")).willReturn(aResponse().withStatus(200)));

        OlusoClient client = new OlusoClient(
                optionsBuilder().enableOfflineQueue(false).maxErrorsPerMinute(2).build());

        client.captureException(new RuntimeException("1")).join();
        client.captureException(new RuntimeException("2")).join();
        client.captureException(new RuntimeException("3")).join();

        server.verify(2, postRequestedFor(urlEqualTo("/report")));
    }
}
