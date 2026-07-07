package com.kiniu.game.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "game.story.catalog-path=target/test-security-story-catalog.json",
        "game.agents.catalog-path=target/test-security-agent-catalog.json",
        "game.sessions.export-path=target/test-security-session-exports",
        "game.security.local-token=test-token",
        "game.security.allowed-origins=http://localhost:3000,http://127.0.0.1:3000",
        "game.security.allowed-provider-hosts=localhost,127.0.0.1"
})
@AutoConfigureMockMvc
class GameControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRequireLocalTokenWhenConfigured() throws Exception {
        mockMvc.perform(get("/agent/story")
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectDisallowedBrowserOrigins() throws Exception {
        mockMvc.perform(get("/agent/story")
                        .header("Origin", "https://example.test")
                        .header("X-Local-Token", "test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectProviderHostsOutsideAllowlist() throws Exception {
        mockMvc.perform(post("/agent/next")
                        .header("Origin", "http://localhost:3000")
                        .header("X-Local-Token", "test-token")
                        .header("X-Provider-Url", "https://api.openai.com/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"safe-session\",\"input\":\"hello\",\"choice\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}