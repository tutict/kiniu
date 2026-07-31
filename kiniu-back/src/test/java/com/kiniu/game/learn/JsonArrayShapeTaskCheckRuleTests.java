package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonArrayShapeTaskCheckRuleTests {

    @Test
    void shouldRequireMinimumItemsWithAllConfiguredFields() {
        TaskCheckRegistry registry = new TaskCheckRegistry(
                List.of(new JsonArrayShapeTaskCheckRule(new ObjectMapper())));
        TaskCheckDefinition definition = new TaskCheckDefinition(
                "trials", "json-array-shape", "run.json", "/trials:2:id,status", true, 100, "Trials");

        RuleCheckOutcome incomplete = registry.evaluate(
                definition,
                new SubmissionSnapshot(Map.of("run.json", "{\"trials\":[{\"id\":\"a\"}]}")));
        RuleCheckOutcome complete = registry.evaluate(
                definition,
                new SubmissionSnapshot(Map.of(
                        "run.json",
                        "{\"trials\":[{\"id\":\"a\",\"status\":\"pass\"},{\"id\":\"b\",\"status\":\"pass\"}]}")));

        assertFalse(incomplete.passed());
        assertTrue(complete.passed());
    }
}
