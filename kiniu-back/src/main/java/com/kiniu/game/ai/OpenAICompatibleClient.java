package com.kiniu.game.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey().trim())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Provider returned HTTP " + response.statusCode() + ".");
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        String content = contentNode.asText("").trim();
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
