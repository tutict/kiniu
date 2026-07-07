package com.kiniu.game.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderUrlValidatorTests {

    @Test
    void shouldAllowOnlyConfiguredHttpHosts() {
        LocalAccessProperties properties = new LocalAccessProperties();
        properties.setAllowedProviderHosts(List.of("localhost", "127.0.0.1", "::1"));
        ProviderUrlValidator validator = new ProviderUrlValidator(properties);

        assertThat(validator.validate("http://localhost:11434/v1")).isEqualTo("http://localhost:11434/v1");
        assertThat(validator.validate("http://127.0.0.1:11434/v1")).isEqualTo("http://127.0.0.1:11434/v1");
        assertThat(validator.validate("http://[::1]:11434/v1")).isEqualTo("http://[::1]:11434/v1");
        assertThat(validator.validate(" ")).isEmpty();

        assertThatThrownBy(() -> validator.validate("https://api.openai.com/v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
        assertThatThrownBy(() -> validator.validate("file:///tmp/model"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http or https");
    }
}