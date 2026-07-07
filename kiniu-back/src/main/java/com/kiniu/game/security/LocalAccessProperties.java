package com.kiniu.game.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "game.security")
public class LocalAccessProperties {

    private String localToken = "";
    private List<String> allowedOrigins = List.of("http://localhost:3000", "http://127.0.0.1:3000");
    private List<String> allowedProviderHosts = List.of("localhost", "127.0.0.1", "::1");

    public String getLocalToken() {
        return localToken;
    }

    public void setLocalToken(String localToken) {
        this.localToken = localToken == null ? "" : localToken.trim();
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = cleanList(allowedOrigins);
    }

    public List<String> getAllowedProviderHosts() {
        return allowedProviderHosts;
    }

    public void setAllowedProviderHosts(List<String> allowedProviderHosts) {
        this.allowedProviderHosts = cleanList(allowedProviderHosts);
    }

    public boolean requiresToken() {
        return !localToken.isBlank();
    }

    private List<String> cleanList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }
}
