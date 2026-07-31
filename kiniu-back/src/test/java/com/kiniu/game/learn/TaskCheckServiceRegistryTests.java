package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskCheckServiceRegistryTests {

    @Test
    void shouldIsolateUnexpectedRuleFailuresAndContinueChecking() {
        TaskCheckService service = new TaskCheckService(new TaskCheckRegistry(List.of(
                throwingRule(),
                new ContainsTaskCheckRule())));
        LearningTaskDefinition task = task(List.of(
                new TaskCheckDefinition("broken", "throwing", "notes.md", "ignored", true, 50, "Broken"),
                new TaskCheckDefinition("content", "contains", "notes.md", "expected", true, 50, "Content")));

        List<TaskCheckResult> results = service.check(task, Map.of("notes.md", "expected"));

        assertFalse(results.get(0).passed());
        assertTrue(results.get(0).evidence().contains("无法解析"));
        assertFalse(results.get(0).evidence().contains("sensitive internal detail"));
        assertTrue(results.get(1).passed());
    }

    private TaskCheckRule throwingRule() {
        return new TaskCheckRule() {
            @Override
            public String type() {
                return "throwing";
            }

            @Override
            public void validateRule(TaskCheckDefinition definition) {
            }

            @Override
            public RuleCheckOutcome evaluate(
                    TaskCheckDefinition definition,
                    SubmissionSnapshot submission) {
                throw new IllegalStateException("sensitive internal detail");
            }
        };
    }

    private LearningTaskDefinition task(List<TaskCheckDefinition> checks) {
        return new LearningTaskDefinition(
                "task",
                "Task",
                "Summary",
                "beginner",
                "requirements",
                10,
                List.of("requirements"),
                "Define a contract",
                "Scenario",
                "project-agent",
                List.of(new LearningFileView("notes.md", "")),
                checks,
                "Lesson",
                List.of("Artifact"),
                List.of(),
                "document",
                List.of());
    }
}
