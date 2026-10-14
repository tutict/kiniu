package com.kiniu.game.security;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single source of truth for the local access token check.
 *
 * <p>Anything that can reach the loopback port can call every endpoint, so the token is the only
 * thing separating the app from other processes on the machine. The comparison is constant-time so
 * the token cannot be recovered byte by byte through response timing.
 *
 * <p>When no token is configured the check is a deliberate no-op: {@code start.ps1 -NoLocalToken}
 * turns authentication off on purpose. That state is insecure and is logged at startup.
 */
@Component
public class LocalAccessGuard {

    public static final String TOKEN_HEADER = "X-Local-Token";

    private final LocalAccessProperties properties;

    public LocalAccessGuard(LocalAccessProperties properties) {
        this.properties = properties;
    }

    /** True when the request carries a valid token, or when no token is configured. */
    public boolean allows(HttpServletRequest request) {
        if (!properties.requiresToken()) {
            return true;
        }
        String provided = request.getHeader(TOKEN_HEADER);
        if (provided == null) {
            return false;
        }
        return constantTimeEquals(properties.getLocalToken(), provided.trim());
    }

    /** Rejects the request with 401 unless {@link #allows(HttpServletRequest)}. */
    public void require(HttpServletRequest request) {
        if (!allows(request)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid local access token.");
        }
    }

    private boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}