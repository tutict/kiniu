package com.kiniu.game;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "game.story.catalog-path=target/test-story-catalog.json",
        "game.agents.catalog-path=target/test-agent-catalog.json",
        "game.sessions.export-path=target/test-session-exports"
})
class GameApplicationTests {

    @Test
    void contextLoads() {
    }
}
