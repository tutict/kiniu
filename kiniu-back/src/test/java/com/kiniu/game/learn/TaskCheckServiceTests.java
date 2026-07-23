package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskCheckServiceTests {

    private final TaskCheckService service = new TaskCheckService(new ObjectMapper());

    @Test
    void shouldRequireAllRequiredChecksAndReportEvidence() {
        LearningTaskDefinition task = task(List.of(
                new TaskCheckDefinition("heading", "markdown-section", "requirements.md", "## User", true, 50, "User"),
                new TaskCheckDefinition("bullets", "regex", "requirements.md", "(?m)^[-*] .+", true, 50, "Bullets")));

        List<TaskCheckResult> results = service.check(task, Map.of(
                "requirements.md",
                "## User\nDevelopers integrating an AI assistant.\n\n## Acceptance\n- returns an auditable result"));

        assertEquals(100, service.score(results));
        assertTrue(service.passed(results));
        assertTrue(results.get(0).evidence().contains("有效"));
    }

    @Test
    void shouldRejectEmptyStructuredValuesAndArrayItems() {
        LearningTaskDefinition task = task(List.of(
                new TaskCheckDefinition("policy", "json-field", "agent.json", "memoryPolicy", true, 50, "Policy"),
                new TaskCheckDefinition("goals", "json-array-min", "agent.json", "coreGoals:2", true, 50, "Goals")));

        List<TaskCheckResult> results = service.check(
                task,
                Map.of("agent.json", "{\"memoryPolicy\":\"\",\"coreGoals\":[\"\",{}]}"));

        assertEquals(0, service.score(results));
        assertFalse(service.passed(results));
    }

    @Test
    void shouldRejectEmptyMarkdownSections() {
        LearningTaskDefinition task = task(List.of(
                new TaskCheckDefinition("user", "markdown-section", "requirements.md", "## User", true, 100, "User")));

        List<TaskCheckResult> results = service.check(
                task,
                Map.of("requirements.md", "# Requirements\n\n## User\n\n## Goal\nSomething"));

        assertFalse(service.passed(results));
    }

    @Test
    void shouldRejectMalformedJsonAndMissingFiles() {
        LearningTaskDefinition task = task(List.of(
                new TaskCheckDefinition("goals", "json-array-min", "agent.json", "coreGoals:2", true, 100, "Goals")));

        List<TaskCheckResult> results = service.check(task, Map.of("agent.json", "not-json"));

        assertEquals(0, service.score(results));
        assertFalse(service.passed(results));
        assertTrue(results.get(0).evidence().contains("无法解析"));
    }

    @Test
    void shouldNotPassWhenTaskHasNoRequiredChecks() {
        LearningTaskDefinition task = task(List.of(
                new TaskCheckDefinition("optional", "min-length", "notes.md", "5", false, 10, "Notes")));

        assertFalse(service.passed(service.check(task, Map.of("notes.md", "enough"))));
    }

    @Test
    void shouldRejectOversizedSubmissions() {
        LearningTaskDefinition task = task(List.of(
                new TaskCheckDefinition("content", "min-length", "notes.md", "5", true, 10, "Notes")));

        String oversized = "x".repeat(TaskCheckService.MAX_FILE_CHARS + 1);
        assertThrows(IllegalArgumentException.class, () -> service.check(task, Map.of("notes.md", oversized)));
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
                List.of(),
                checks);
    }
}