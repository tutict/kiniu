package com.kiniu.game.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SessionIdValidatorTests {

    private final SessionIdValidator validator = new SessionIdValidator();

    @Test
    void shouldAcceptOnlySafeSessionIds() {
        assertThat(validator.normalize("default-session")).isEqualTo("default-session");
        assertThat(validator.normalize("profile.01_test-2")).isEqualTo("profile.01_test-2");
        assertThat(validator.normalize("  trimmed  ")).isEqualTo("trimmed");
        assertThat(validator.normalize(" ")).isEqualTo("default-session");
    }

    @Test
    void shouldRejectTraversalAndShellLikeSessionIds() {
        assertThatThrownBy(() -> validator.normalize("../escape"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session id");
        assertThatThrownBy(() -> validator.normalize(".."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session id");
        assertThatThrownBy(() -> validator.normalize("session/child"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session id");
        assertThatThrownBy(() -> validator.normalize("session;rm"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session id");
    }
}