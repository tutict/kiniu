package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class TaskCheckRegistryTests {

    @Test
    void shouldRejectDuplicateCheckTypes() {
        assertThrows(
                IllegalStateException.class,
                () -> new TaskCheckRegistry(List.of(rule("duplicate"), rule("duplicate"))));
    }

    @Test
    void shouldRejectBlankCheckTypes() {
        assertThrows(IllegalStateException.class, () -> new TaskCheckRegistry(List.of(rule(" "))));
    }

    @Test
    void shouldReportInvalidAdapterConfigurationAsACatalogError() {
        TaskCheckRegistry registry = new TaskCheckRegistry(List.of(new MinLengthTaskCheckRule()));
        TaskCheckDefinition invalid = new TaskCheckDefinition(
                "length", "min-length", "notes.md", "not-a-number", true, 100, "Length");

        assertThrows(IllegalStateException.class, () -> registry.validateRule(invalid));
    }

    private TaskCheckRule rule(String type) {
        return new TaskCheckRule() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public void validateRule(TaskCheckDefinition definition) {
            }

            @Override
            public RuleCheckOutcome evaluate(
                    TaskCheckDefinition definition,
                    SubmissionSnapshot submission) {
                return RuleCheckOutcome.passed("ok");
            }
        };
    }
}
