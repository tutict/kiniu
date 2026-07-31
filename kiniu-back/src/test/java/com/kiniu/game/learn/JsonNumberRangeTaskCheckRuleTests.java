package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonNumberRangeTaskCheckRuleTests {

    @Test
    void shouldRequireANumberInsideTheInclusiveRange() {
        TaskCheckRegistry registry = new TaskCheckRegistry(
                List.of(new JsonNumberRangeTaskCheckRule(new ObjectMapper())));
        TaskCheckDefinition definition = new TaskCheckDefinition(
                "latency", "json-number-range", "run.json", "/latencyMs:1:5000", true, 100, "Latency");

        RuleCheckOutcome outside = registry.evaluate(
                definition,
                new SubmissionSnapshot(Map.of("run.json", "{\"latencyMs\":9000}")));
        RuleCheckOutcome inside = registry.evaluate(
                definition,
                new SubmissionSnapshot(Map.of("run.json", "{\"latencyMs\":240}")));

        assertFalse(outside.passed());
        assertTrue(inside.passed());
    }
}
