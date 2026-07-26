package dev.oluso.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.oluso.ErrorReport;
import dev.oluso.TransportException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Sends error reports via {@link HttpClient}, Java 11's built-in HTTP
 * client -- no extra dependency needed for the one HTTP call this library
 * makes. Never blocks the caller's thread: {@link #send(String, ErrorReport, String, Duration)} returns
 * immediately with a future that completes (successfully or not) once the
 * request finishes.
 */
public final class Transport {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public Transport(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<Void> send(String endpoint, ErrorReport report, String apiKey, Duration timeout) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(report);
        } catch (Exception e) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new TransportException("Failed to serialize error report", e));
            return failed;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-oluso-signature", apiKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        throw new TransportException("Failed to send error report", throwable);
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new TransportException("Error reporting failed with status " + response.statusCode());
                    }
                    return (Void) null;
                });
    }
}
