package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonPointerPresentTaskCheckRuleTests {

    @Test
    void shouldRequireAMeaningfulValueAtTheJsonPointer() {
        TaskCheckRegistry registry = new TaskCheckRegistry(
                List.of(new JsonPointerPresentTaskCheckRule(new ObjectMapper())));
        TaskCheckDefinition definition = new TaskCheckDefinition(
                "model", "json-pointer-present", "run.json", "/request/model", true, 100, "Model");

        RuleCheckOutcome missing = registry.evaluate(
                definition,
                new SubmissionSnapshot(Map.of("run.json", "{\"request\":{}}")));
        RuleCheckOutcome present = registry.evaluate(
                definition,
                new SubmissionSnapshot(Map.of("run.json", "{\"request\":{\"model\":\"model-x\"}}")));

        assertFalse(missing.passed());
        assertTrue(present.passed());
    }
}
