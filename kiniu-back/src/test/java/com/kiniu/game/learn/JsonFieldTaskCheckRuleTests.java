package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonFieldTaskCheckRuleTests {

    @Test
    void shouldRequireAMeaningfulJsonField() {
        TaskCheckRegistry registry = new TaskCheckRegistry(List.of(new JsonFieldTaskCheckRule(new ObjectMapper())));
        TaskCheckDefinition definition = new TaskCheckDefinition(
                "policy", "json-field", "agent.json", "memoryPolicy", true, 100, "Policy");

        RuleCheckOutcome blank = registry.evaluate(
                definition,
                new SubmissionSnapshot(Map.of("agent.json", "{\"memoryPolicy\":\"\"}")));
        RuleCheckOutcome meaningful = registry.evaluate(
                definition,
                new SubmissionSnapshot(Map.of("agent.json", "{\"memoryPolicy\":\"bounded\"}")));

        assertFalse(blank.passed());
        assertTrue(meaningful.passed());
    }
}
