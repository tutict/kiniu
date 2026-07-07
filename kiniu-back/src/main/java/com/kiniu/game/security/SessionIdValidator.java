package com.kiniu.game.security;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SessionIdValidator {

    private static final Pattern SAFE_SESSION_ID = Pattern.compile("[A-Za-z0-9._-]{1,80}");

    public String normalize(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "default-session";
        }
        String normalized = sessionId.trim();
        if (!SAFE_SESSION_ID.matcher(normalized).matches()
                || normalized.equals(".")
                || normalized.equals("..")
                || normalized.contains("..")) {
            throw new IllegalArgumentException("Session id must be 1-80 characters and only contain letters, numbers, '.', '_' or '-'.");
        }
        return normalized;
    }
}
