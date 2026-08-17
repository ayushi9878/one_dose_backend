package com.careflow.ai;

import com.careflow.config.CareFlowProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal Groq chat-completions client.
 *
 * <p>Every failure path returns {@link Optional#empty()} so a summarisation
 * outage degrades to the deterministic template instead of failing the care
 * manager's request. The API key is read from configuration and never logged.
 */
@Slf4j
@Component
public class GroqClient {

    private final CareFlowProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GroqClient(CareFlowProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Optional<String> summarise(String prompt) {
        CareFlowProperties.Ai ai = properties.getAi();
        if (!ai.isUsable()) {
            return Optional.empty();
        }

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "model", ai.getModel(),
                    "temperature", 0.2,
                    "max_tokens", 300,
                    "messages", java.util.List.of(
                            Map.of("role", "system",
                                    "content", "You summarise care-operations cases. "
                                            + "You never diagnose and never recommend treatment."),
                            Map.of("role", "user", "content", prompt))));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ai.getBaseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(ai.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + ai.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Groq summarisation returned status {}; falling back to template.",
                        response.statusCode());
                return Optional.empty();
            }

            JsonNode content = objectMapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content");

            return content.isMissingNode() || content.asText().isBlank()
                    ? Optional.empty()
                    : Optional.of(content.asText().trim());

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Groq summarisation interrupted; falling back to template.");
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Groq summarisation failed ({}); falling back to template.",
                    ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
