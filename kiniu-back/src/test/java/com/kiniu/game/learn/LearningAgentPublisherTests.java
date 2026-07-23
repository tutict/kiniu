package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiniu.game.agent.AgentManager;
import com.kiniu.game.dto.AgentCatalogResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LearningAgentPublisherTests {

    @Test
    void shouldPublishPassedCompanionIntoExistingAgentCatalog() {
        AgentManager agentManager = org.mockito.Mockito.mock(AgentManager.class);
        AgentCatalogResponse catalog = new AgentCatalogResponse(List.of());
        when(agentManager.upsertAgent(any())).thenReturn(catalog);
        LearningAgentPublisher publisher = new LearningAgentPublisher(new ObjectMapper(), agentManager);
        LearningTaskDefinition task = new LearningTaskDefinition(
                "companion-agent",
                "Companion",
                "Summary",
                "beginner",
                "agent-project",
                20,
                List.of("Agent"),
                "Objective",
                "Scenario",
                "companion",
                List.of(),
                List.of());
        LearningAttempt attempt = new LearningAttempt(
                "attempt-1",
                task.id(),
                "2026-07-23T00:00:00Z",
                true,
                100,
                Map.of("agent.json", """
                        {
                          "id": "student-companion",
                          "name": "Focus Companion",
                          "personality": "calm",
                          "coreGoals": ["plan the next step", "reflect on progress"],
                          "boundaries": ["never decide high-risk matters", "never invent memories"],
                          "memoryPolicy": "remember explicit preferences and forget on request",
                          "failurePolicy": "state uncertainty and ask for human review"
                        }
                        """),
                List.of(),
                "");

        LearningAgentPublisher.PublishedLearningAgent published = publisher.publish(task, attempt);

        assertEquals("student-companion", published.agent().id());
        assertEquals("Focus Companion", published.agent().name());
        verify(agentManager).upsertAgent(any());
    }

    @Test
    void shouldRejectPublishedAgentIdsOutsideTheStudentNamespace() {
        AgentManager agentManager = org.mockito.Mockito.mock(AgentManager.class);
        LearningAgentPublisher publisher = new LearningAgentPublisher(new ObjectMapper(), agentManager);
        LearningTaskDefinition task = new LearningTaskDefinition(
                "companion-agent",
                "Companion",
                "Summary",
                "beginner",
                "agent-project",
                20,
                List.of("Agent"),
                "Objective",
                "Scenario",
                "companion",
                List.of(),
                List.of());
        LearningAttempt attempt = new LearningAttempt(
                "attempt-1",
                task.id(),
                "2026-07-23T00:00:00Z",
                true,
                100,
                Map.of("agent.json", """
                        {
                          "id": "narrator",
                          "name": "Overwritten narrator",
                          "coreGoals": ["plan", "reflect"],
                          "boundaries": ["stay safe", "ask first"],
                          "memoryPolicy": "explicit only",
                          "failurePolicy": "ask for review"
                        }
                        """),
                List.of(),
                "");

        assertThrows(IllegalArgumentException.class, () -> publisher.publish(task, attempt));
    }
}
