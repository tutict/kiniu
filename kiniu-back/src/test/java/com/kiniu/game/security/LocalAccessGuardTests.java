package com.kiniu.game.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class LocalAccessGuardTests {

    private static LocalAccessGuard guard(String configuredToken) {
        LocalAccessProperties properties = new LocalAccessProperties();
        properties.setLocalToken(configuredToken);
        return new LocalAccessGuard(properties);
    }

    private static MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (token != null) {
            request.addHeader("X-Local-Token", token);
        }
        return request;
    }

    @Test
    void shouldAllowEverythingWhenNoTokenIsConfigured() {
        assertTrue(guard("").allows(requestWithToken(null)));
    }

    @Test
    void shouldRejectWhenAConfiguredTokenIsMissing() {
        assertFalse(guard("secret").allows(requestWithToken(null)));
    }

    @Test
    void shouldRejectAWrongToken() {
        assertFalse(guard("secret").allows(requestWithToken("not-secret")));
    }

    @Test
    void shouldRejectABlankTokenWhenOneIsConfigured() {
        assertFalse(guard("secret").allows(requestWithToken("   ")));
    }

    @Test
    void shouldAcceptTheConfiguredTokenAndTrimSurroundingWhitespace() {
        assertTrue(guard("secret").allows(requestWithToken("  secret  ")));
    }

    @Test
    void shouldNotTreatAPrefixOfTheTokenAsValid() {
        assertFalse(guard("secret").allows(requestWithToken("secre")));
    }

    @Test
    void requireShouldThrowUnauthorizedForAWrongToken() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> guard("secret").require(requestWithToken("wrong")));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void requireShouldPassForTheConfiguredToken() {
        guard("secret").require(requestWithToken("secret"));
    }
}