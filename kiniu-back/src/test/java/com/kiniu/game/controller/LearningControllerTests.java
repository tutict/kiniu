package com.kiniu.game.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "game.security.local-token=",
        "game.learning.progress-path=target/test-learning-progress.json",
        "game.learning.attempts-path=target/test-learning-attempts.json",
        "game.agents.catalog-path=target/test-learning-agent-catalog.json",
        "game.story.catalog-path=target/test-learning-story-catalog.json"
})
@AutoConfigureMockMvc
class LearningControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetLearningProgress() throws Exception {
        mockMvc.perform(post("/learn/progress/reset"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldEnforceUnlocksPersistAttemptsAndExplainEvidence() throws Exception {
        mockMvc.perform(post("/learn/tasks/data-lifecycle/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":{\"data-contract.json\":\"{}\"}}"))
                .andExpect(status().isConflict());

        String request = """
                {
                  "notes": "first complete attempt",
                  "files": {
                    "requirements.md": "# Requirements\\n\\n## 用户\\n开发者\\n\\n## 业务目标\\n得到下一步行动\\n\\n## 非目标\\n不替用户做高风险决定\\n\\n## 验收标准\\n- 给出可追踪的下一步"
                  }
                }
                """;
        String response = mockMvc.perform(post("/learn/tasks/requirements-contract/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.progress.currentTaskId").value("data-lifecycle"))
                .andExpect(jsonPath("$.attemptId").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String attemptId = response.replaceAll(".*\"attemptId\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(post("/learn/tasks/requirements-contract/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptId\":\"" + attemptId + "\",\"question\":\"为什么通过？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback").isString())
                .andExpect(jsonPath("$.feedback").value(org.hamcrest.Matchers.containsString("任务目标")));
    }
}
