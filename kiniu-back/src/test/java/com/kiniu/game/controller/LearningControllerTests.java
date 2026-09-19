package com.kiniu.game.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
        mockMvc.perform(get("/learn/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[0].tasks[0].id").value("requirements-contract"))
                .andExpect(jsonPath("$.modules[0].tasks[0].evidenceMode").value("quiz"))
                .andExpect(jsonPath("$.modules[0].tasks[0].quizQuestions[0].correctOptionId").doesNotExist())
                .andExpect(jsonPath("$.modules[0].tasks[0].quizQuestions[0].explanation").doesNotExist())
                .andExpect(jsonPath("$.modules[0].tasks[0].quizQuestions[0].options").isArray());

        mockMvc.perform(get("/learn/tasks/requirements-contract"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceMode").value("quiz"))
                .andExpect(jsonPath("$.quizQuestions[0].correctOptionId").doesNotExist())
                .andExpect(jsonPath("$.quizQuestions[0].explanation").doesNotExist());

        mockMvc.perform(post("/learn/tasks/data-lifecycle/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":{\"data-contract.json\":\"{}\"}}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/learn/tasks/requirements-contract/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": {
                                    "first-move": "write-prompt",
                                    "user-scope": "specific-scene",
                                    "out-of-scope-user": "team-coordinator",
                                    "business-outcome": "observable-result",
                                    "input-ambiguity": "flag-and-bound",
                                    "data-boundary": "pasted-only",
                                    "risk-boundary": "refuse-and-escalate",
                                    "irreversible-request": "refuse-external-write",
                                    "missing-owner": "flag-or-refuse",
                                    "acceptance-criteria": "given-when-then"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.progress.currentTaskId").value("requirements-contract"));

        String request = """
                {
                  "notes": "first complete attempt",
                  "answers": {
                    "first-move": "define-contract",
                    "user-scope": "specific-scene",
                    "out-of-scope-user": "team-coordinator",
                    "business-outcome": "observable-result",
                    "input-ambiguity": "flag-and-bound",
                    "data-boundary": "pasted-only",
                    "risk-boundary": "refuse-and-escalate",
                    "irreversible-request": "refuse-external-write",
                    "missing-owner": "flag-or-refuse",
                    "acceptance-criteria": "given-when-then"
                  }
                }
                """;
        String response = mockMvc.perform(post("/learn/tasks/requirements-contract/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.progress.currentTaskId").value("http-json-basics"))
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

        mockMvc.perform(get("/learn/tasks/http-json-basics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceMode").value("quiz"))
                .andExpect(jsonPath("$.quizQuestions[0].correctOptionId").doesNotExist())
                .andExpect(jsonPath("$.quizQuestions[0].explanation").doesNotExist());

        mockMvc.perform(post("/learn/tasks/http-json-basics/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": {
                                    "method-choice": "post-plan",
                                    "path-resource": "plan-next",
                                    "json-role": "body-data",
                                    "status-contract": "http-classes",
                                    "timeout-semantics": "unknown-effect",
                                    "client-error": "param-400",
                                    "auth-error": "auth-401",
                                    "idempotency-key": "same-key",
                                    "retry-safety": "keyed-backoff",
                                    "secret-boundary": "redact-record"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.progress.currentTaskId").value("model-response-contract"));

        mockMvc.perform(get("/learn/tasks/model-response-contract"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceMode").value("quiz"))
                .andExpect(jsonPath("$.quizQuestions[0].correctOptionId").doesNotExist());

        mockMvc.perform(post("/learn/tasks/model-response-contract/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": {
                                    "why-schema": "shared-contract",
                                    "required-fields": "actions-required",
                                    "refusal-branch": "explicit-refusal",
                                    "three-failures": "parse-refuse-transport",
                                    "schema-not-truth": "shape-only",
                                    "sampling-variance": "distribution",
                                    "run-metadata": "ids-numbers",
                                    "numeric-fields": "plain-numbers",
                                    "parsed-result": "structured",
                                    "secret-prompt": "redact-run"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.progress.currentTaskId").value("prompt-context-design"));

        mockMvc.perform(post("/learn/tasks/prompt-context-design/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": {
                                    "layer-roles": "stable-split",
                                    "priority": "system-wins",
                                    "untrusted-data": "untrusted",
                                    "conflict-calendar": "keep-contract",
                                    "injection": "quoted-data",
                                    "ignore-phrase": "not-enough",
                                    "token-budget": "split-trim",
                                    "dump-history": "dilute",
                                    "insufficient-evidence": "mark-limit",
                                    "separator": "quoted-block"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.progress.currentTaskId").value("data-lifecycle"));
    }
}
