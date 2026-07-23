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
    void shouldValidateJsonPointersArrayShapesAndNumberRanges() {
        LearningTaskDefinition task = task(List.of(
                new TaskCheckDefinition("model", "json-pointer-present", "run.json", "/request/model", true, 25, "Model"),
                new TaskCheckDefinition("trials", "json-array-shape", "run.json", "/trials:2:id,status,grader", true, 35, "Trials"),
                new TaskCheckDefinition("latency", "json-number-range", "run.json", "/latencyMs:1:5000", true, 40, "Latency")));

        List<TaskCheckResult> results = service.check(task, Map.of("run.json", """
                {
                  "request": {"model": "model-x"},
                  "trials": [{"id":"a","status":"pass","grader":"code"},{"id":"b","status":"pass","grader":"human"}],
                  "latencyMs": 240
                }
                """));

        assertEquals(100, service.score(results));
        assertTrue(service.passed(results));
    }

    @Test
    void shouldRejectInvalidJsonShapesAndOutOfRangeNumbers() {
        LearningTaskDefinition task = task(List.of(
                new TaskCheckDefinition("trials", "json-array-shape", "run.json", "/trials:2:id,status,grader", true, 50, "Trials"),
                new TaskCheckDefinition("latency", "json-number-range", "run.json", "/latencyMs:1:5000", true, 50, "Latency")));

        List<TaskCheckResult> results = service.check(task, Map.of("run.json", """
                {"trials":[{"id":"only","status":"pass"}],"latencyMs":9000}
                """));

        assertEquals(0, service.score(results));
        assertFalse(service.passed(results));
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

    @Test
    void shouldEnforceUtf8ByteLimitsForMultibyteContent() {
        LearningTaskDefinition task = task(List.of(
                new TaskCheckDefinition("content", "min-length", "notes.md", "5", true, 10, "Notes")));

        String oversizedInBytes = "学".repeat((TaskCheckService.MAX_FILE_BYTES / 3) + 1);
        assertTrue(oversizedInBytes.length() < TaskCheckService.MAX_FILE_CHARS);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.check(task, Map.of("notes.md", oversizedInBytes)));
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
                checks,
                "Lesson",
                List.of("Artifact"),
                List.of(),
                "document",
                List.of());
    }
}
