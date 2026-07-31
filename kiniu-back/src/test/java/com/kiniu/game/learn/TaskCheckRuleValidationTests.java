package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertThrows;

import tools.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TaskCheckRuleValidationTests {

    private final TaskCheckRegistry registry =
            TaskCheckRegistryTestFactory.standard(new ObjectMapper());

    @ParameterizedTest
    @MethodSource("invalidDefinitions")
    void shouldRejectInvalidRuleConfigurations(TaskCheckDefinition definition) {
        assertThrows(IllegalStateException.class, () -> registry.validateRule(definition));
    }

    private static Stream<TaskCheckDefinition> invalidDefinitions() {
        return Stream.of(
                definition("contains", " "),
                definition("markdown-section", " "),
                definition("regex", "["),
                definition("frontmatter-regex", "["),
                definition("json-field", " "),
                definition("json-array-min", "items:0"),
                definition("json-pointer-present", "not-a-pointer"),
                definition("json-array-shape", "/items:0:id"),
                definition("json-number-range", "/latency:10:1"),
                definition("min-length", "0"));
    }

    private static TaskCheckDefinition definition(String type, String rule) {
        return new TaskCheckDefinition("check", type, "artifact.txt", rule, true, 100, "Message");
    }
}
