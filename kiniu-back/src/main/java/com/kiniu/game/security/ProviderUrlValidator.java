package com.kiniu.game.security;

import java.net.URI;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ProviderUrlValidator {

    private final LocalAccessProperties properties;

    public ProviderUrlValidator(LocalAccessProperties properties) {
        this.properties = properties;
    }

    public String validate(String providerUrl) {
        String trimmed = providerUrl == null ? "" : providerUrl.trim();
        if (trimmed.isBlank()) {
            return "";
        }

        URI uri = URI.create(trimmed);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Provider URL must use http or https.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Provider URL must include a host.");
        }

        String normalizedHost = normalizeHost(host);
        boolean allowed = properties.getAllowedProviderHosts().stream()
                .map(this::normalizeHost)
                .anyMatch(allowedHost -> allowedHost.equals(normalizedHost));
        if (!allowed) {
            throw new IllegalArgumentException("Provider host is not allowed: " + host);
        }
        return trimmed;
    }

    private String normalizeHost(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }
}
