package dev.oluso;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ScopeSnapshotOverrideTest {
    private WireMockServer server;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        WireMock.configureFor("localhost", server.port());
        WireMock.stubFor(post(urlEqualTo("/report")).willReturn(aResponse().withStatus(200)));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void reportsUsingTheSuppliedSnapshotInsteadOfTheInternalThreadLocalScope() throws Exception {
        OlusoClient client = new OlusoClient(OlusoOptions.builder("test-api-key")
                .endpoint(server.baseUrl() + "/report")
                .enableOfflineQueue(false)
                .logToConsole(false)
                .build());

        // Populate the internal ThreadLocal scope with data that should be
        // ignored -- this is exactly the situation a reactive integration
        // is in: whatever's on the current thread's ThreadLocal is
        // unrelated leftover state, not this request's real context.
        client.addBreadcrumb(Breadcrumb.builder("wrong breadcrumb, must not appear").build());
        client.setUserContext(UserContext.of("wrong-user"));

        ScopeSnapshot snapshot = ScopeSnapshot.builder()
                .breadcrumbs(List.of(Breadcrumb.builder("correct breadcrumb").build()))
                .user(UserContext.of("correct-user"))
                .build();

        client.capture("RuntimeException", "boom", null, null, null, snapshot).join();

        List<LoggedRequest> requests = server.findAll(postRequestedFor(urlEqualTo("/report")));
        assertEquals(1, requests.size());

        JsonNode body = new ObjectMapper().readTree(requests.get(0).getBody());
        assertEquals("correct-user", body.at("/context/user/id").asText());
        assertEquals(1, body.at("/context/breadcrumbs").size());
        assertEquals("correct breadcrumb", body.at("/context/breadcrumbs/0/message").asText());
    }
}
