package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContainsTaskCheckRuleTests {

    @Test
    void shouldEvaluateRegisteredRuleAgainstSubmissionSnapshot() {
        TaskCheckRegistry registry = new TaskCheckRegistry(List.of(new ContainsTaskCheckRule()));
        TaskCheckDefinition definition = new TaskCheckDefinition(
                "content", "contains", "notes.md", "expected", true, 100, "Content");

        RuleCheckOutcome outcome = registry.evaluate(
                definition,
                new SubmissionSnapshot(Map.of("notes.md", "the expected value")));

        assertTrue(outcome.passed());
    }
}
