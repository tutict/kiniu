package com.kiniu.game.ai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OpenAICompatibleClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAICompatibleClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String complete(
            AIRequestConfig config,
            String systemPrompt,
            String userPrompt,
            double temperature,
            int maxTokens) throws IOException, InterruptedException {
        String endpoint = resolveEndpoint(config.providerUrl());
        Map<String, Object> payload = Map.of(
                "model", config.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", temperature,
                "max_tokens", maxTokens);
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IOException("Failed to serialize provider request.", exception);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey().trim())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Provider returned HTTP " + response.statusCode() + ".");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (JacksonException exception) {
            throw new IOException("Provider response was not valid JSON.", exception);
        }
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        String content = contentNode.asString("").trim();
        if (content.isBlank()) {
            throw new IOException("Provider response did not contain choices[0].message.content.");
        }
        return content;
    }

    private String resolveEndpoint(String providerUrl) {
        String trimmed = providerUrl == null ? "" : providerUrl.trim();
        if (trimmed.endsWith("/chat/completions")) {
            return trimmed;
        }
        if (trimmed.endsWith("/v1")) {
            return trimmed + "/chat/completions";
        }
        if (trimmed.contains("/v1/")) {
            return trimmed;
        }
        if (trimmed.endsWith("/")) {
            return trimmed + "v1/chat/completions";
        }
        return trimmed + "/v1/chat/completions";
    }
}
