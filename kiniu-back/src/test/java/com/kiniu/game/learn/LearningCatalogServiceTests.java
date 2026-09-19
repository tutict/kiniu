package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LearningCatalogServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void shouldUnlockTasksSequentially() throws Exception {
        LearningCatalogService service = serviceWith(task("first", "min-length"), task("second", "min-length"));

        assertTrue(service.isUnlocked("first", LearningProgress.empty("first")));
        assertFalse(service.isUnlocked("second", LearningProgress.empty("first")));

        LearningProgress completedFirst = LearningProgress.empty("first")
                .record("first", 100, List.of(), "second");
        assertTrue(service.isUnlocked("second", completedFirst));
    }

    @Test
    void shouldRejectUnsupportedCheckTypes() throws Exception {
        assertThrows(IllegalStateException.class, () -> serviceWith(task("first", "unknown-check")));
    }

    @Test
    void shouldAcceptFrontmatterRegexCheckTypes() throws Exception {
        serviceWith(task("first", "frontmatter-regex"));
    }

    @Test
    void shouldRejectTasksWithoutRequiredChecks() throws Exception {
        LearningTaskDefinition task = new LearningTaskDefinition(
                "first",
                "First",
                "Summary",
                "beginner",
                "requirements",
                10,
                List.of("requirements"),
                "Objective",
                "Scenario",
                "project-agent",
                List.of(new LearningFileView("artifact.md", "starter")),
                List.of(new TaskCheckDefinition("optional", "min-length", "artifact.md", "3", false, 10, "Optional")));

        assertThrows(IllegalStateException.class, () -> serviceWith(task));
    }

    @Test
    void shouldRejectTasksWhoseChecksDoNotTotalOneHundredPoints() {
        assertThrows(IllegalStateException.class, () -> serviceWith(taskWithPoints("first", 99)));
    }

    @Test
    void shouldRejectStarterFilesThatExceedTheUtf8ByteLimit() {
        String oversizedInBytes = "学".repeat((TaskCheckService.MAX_FILE_BYTES / 3) + 1);
        assertTrue(oversizedInBytes.length() < TaskCheckService.MAX_FILE_CHARS);
        assertThrows(IllegalStateException.class, () -> serviceWith(taskWithStarterContent("first", oversizedInBytes)));
    }

    @Test
    void shouldUnlockBranchesOnlyAfterAllExplicitPrerequisitesAreComplete() throws Exception {
        LearningCatalogService service = serviceWith(
                task("first", "min-length"),
                taskWithPrerequisites("branch-a", List.of("first")),
                taskWithPrerequisites("branch-b", List.of("first")),
                taskWithPrerequisites("join", List.of("branch-a", "branch-b")));

        LearningProgress afterFirst = LearningProgress.empty("first")
                .record("first", 100, List.of(), "branch-a");
        assertTrue(service.isUnlocked("branch-a", afterFirst));
        assertTrue(service.isUnlocked("branch-b", afterFirst));
        assertFalse(service.isUnlocked("join", afterFirst));

        LearningProgress afterOneBranch = afterFirst.record("branch-a", 100, List.of(), "branch-b");
        assertFalse(service.isUnlocked("join", afterOneBranch));
        LearningProgress afterBothBranches = afterOneBranch.record("branch-b", 100, List.of(), "join");
        assertTrue(service.isUnlocked("join", afterBothBranches));
    }

    @Test
    void shouldRejectUnknownAndCyclicPrerequisites() {
        assertThrows(
                IllegalStateException.class,
                () -> serviceWith(taskWithPrerequisites("first", List.of("missing"))));
        assertThrows(
                IllegalStateException.class,
                () -> serviceWith(
                        taskWithPrerequisites("first", List.of("second")),
                        taskWithPrerequisites("second", List.of("first"))));
    }

    @Test
    void shouldReturnNoNextTaskWhenEverythingIsComplete() throws Exception {
        LearningCatalogService service = serviceWith(task("first", "min-length"), task("second", "min-length"));
        LearningProgress completed = LearningProgress.empty("first")
                .record("first", 100, List.of(), "second")
                .record("second", 100, List.of(), "second");

        assertEquals("", service.nextTaskId("second", completed));
    }

    @Test
    void productionCatalogShouldExposeVersionFourCurriculum() {
        LearningCatalogService service = new LearningCatalogService(
                objectMapper,
                Path.of("data", "learning-catalog.json").toString(),
                TaskCheckRegistryTestFactory.standard(objectMapper));
        LearningCatalogDefinition catalog = service.getCatalog();
        List<LearningTaskDefinition> tasks = catalog.modules().stream()
                .flatMap(module -> module.tasks().stream())
                .toList();

        assertEquals(4, catalog.version());
        assertEquals(8, catalog.modules().size());
        assertEquals(21, tasks.size());
        assertEquals(
                List.of(
                        "foundations",
                        "context-data",
                        "agent-systems",
                        "evaluation-agent",
                        "rag-engineering",
                        "genai-safety",
                        "protocol-interop",
                        "production-engineering"),
                catalog.modules().stream().map(LearningModuleDefinition::id).toList());
        assertEquals(
                List.of("evaluation-suite", "companion-agent"),
                catalog.modules().get(3).tasks().stream().map(LearningTaskDefinition::id).toList());
        assertEquals(
                List.of("observability-runbook", "release-safety", "architecture-collaboration"),
                catalog.modules().get(7).tasks().stream().map(LearningTaskDefinition::id).toList());
        assertTrue(tasks.stream().allMatch(task -> task.lesson().length() >= 300 && task.lesson().length() <= 600));
        assertTrue(tasks.stream().allMatch(task -> task.references().size() >= 1 && task.references().size() <= 3));
        assertTrue(tasks.stream().allMatch(task -> task.passingScore() == 100));
        assertTrue(tasks.stream().allMatch(task -> {
            if ("quiz".equals(task.evidenceMode())) {
                return task.checks().isEmpty()
                        && task.starterFiles().isEmpty()
                        && task.quizQuestions().stream().mapToInt(LearningQuizQuestion::points).sum() == 100;
            }
            return task.quizQuestions().isEmpty()
                    && task.checks().stream().mapToInt(TaskCheckDefinition::points).sum() == 100;
        }));
        LearningTaskDefinition requirements = taskById(tasks, "requirements-contract");
        assertEquals("quiz", requirements.evidenceMode());
        assertEquals(10, requirements.quizQuestions().size());
        assertEquals(
                List.of(
                        "first-move",
                        "user-scope",
                        "out-of-scope-user",
                        "business-outcome",
                        "input-ambiguity",
                        "data-boundary",
                        "risk-boundary",
                        "irreversible-request",
                        "missing-owner",
                        "acceptance-criteria"),
                requirements.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("define-contract", requirements.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition httpJson = taskById(tasks, "http-json-basics");
        assertEquals("quiz", httpJson.evidenceMode());
        assertEquals(10, httpJson.quizQuestions().size());
        assertEquals(
                List.of(
                        "method-choice",
                        "path-resource",
                        "json-role",
                        "status-contract",
                        "timeout-semantics",
                        "client-error",
                        "auth-error",
                        "idempotency-key",
                        "retry-safety",
                        "secret-boundary"),
                httpJson.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("post-plan", httpJson.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition modelContract = taskById(tasks, "model-response-contract");
        assertEquals("quiz", modelContract.evidenceMode());
        assertEquals(10, modelContract.quizQuestions().size());
        assertEquals(
                List.of(
                        "why-schema",
                        "required-fields",
                        "refusal-branch",
                        "three-failures",
                        "schema-not-truth",
                        "sampling-variance",
                        "run-metadata",
                        "numeric-fields",
                        "parsed-result",
                        "secret-prompt"),
                modelContract.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("shared-contract", modelContract.quizQuestions().get(0).correctOptionId());
        assertTrue(tasks.stream()
                .filter(task -> "import".equals(task.evidenceMode()))
                .allMatch(task -> List.of("/source", "/capturedAt", "/requestId").stream()
                        .allMatch(pointer -> task.checks().stream()
                                .anyMatch(check -> pointer.equals(check.rule())))));

        LearningTaskDefinition a2a = taskById(tasks, "a2a-collaboration");
        String delegationShape = a2a.checks().stream()
                .filter(check -> "tasks".equals(check.id()))
                .findFirst()
                .orElseThrow()
                .rule();
        assertTrue(List.of("messageParts", "idempotency", "cancellation", "retry", "delivery", "identity")
                .stream()
                .allMatch(delegationShape::contains));
        assertTrue(a2a.checks().stream().anyMatch(check -> "delivery-modes".equals(check.id())));
        assertTrue(a2a.checks().stream().anyMatch(check -> "lifecycle".equals(check.id())));

        LearningTaskDefinition observability = taskById(tasks, "observability-runbook");
        assertTrue(List.of(
                        "/trace_id",
                        "/provider",
                        "/model",
                        "/input_tokens:1:10000000",
                        "/output_tokens:1:10000000",
                        "/latency_ms:1:3600000",
                        "/correlations/tool_call_id",
                        "/correlations/mcp_request_id",
                        "/correlations/a2a_task_id")
                .stream()
                .allMatch(rule -> observability.checks().stream()
                        .anyMatch(check -> "trace-sample.json".equals(check.path()) && rule.equals(check.rule()))));
        assertTrue(observability.checks().stream().anyMatch(check -> "trace-redacted".equals(check.id())));

        LearningTaskDefinition skillAuthoring = taskById(tasks, "agent-skill-authoring");
        assertEquals(List.of("tool-contract", "prompt-context-design"), skillAuthoring.prerequisiteTaskIds());
        assertTrue(skillAuthoring.checks().stream().anyMatch(check -> "progressive-disclosure".equals(check.id())));
        List<TaskCheckDefinition> metadataChecks = skillAuthoring.checks().stream()
                .filter(check -> List.of("name", "description").contains(check.id()))
                .toList();
        assertEquals(2, metadataChecks.size());
        assertTrue(metadataChecks.stream().allMatch(check -> "SKILL.md".equals(check.path())
                        && "frontmatter-regex".equals(check.type())));
        assertTrue(skillAuthoring.checks().stream()
                .anyMatch(check -> "evaluation".equals(check.id()) && check.required()));
    }

    private LearningTaskDefinition taskById(List<LearningTaskDefinition> tasks, String taskId) {
        return tasks.stream().filter(task -> taskId.equals(task.id())).findFirst().orElseThrow();
    }

    private LearningCatalogService serviceWith(LearningTaskDefinition... tasks) throws Exception {
        LearningCatalogDefinition catalog = new LearningCatalogDefinition(
                1,
                List.of(new LearningModuleDefinition("module", "Module", "Summary", "beginner", List.of(tasks))));
        Path path = tempDir.resolve("catalog-" + System.nanoTime() + ".json");
        objectMapper.writeValue(path.toFile(), catalog);
        return new LearningCatalogService(
                objectMapper,
                path.toString(),
                TaskCheckRegistryTestFactory.standard(objectMapper));
    }

    private LearningTaskDefinition task(String id, String type) {
        return new LearningTaskDefinition(
                id,
                id,
                "Summary",
                "beginner",
                "requirements",
                10,
                List.of("requirements"),
                "Objective",
                "Scenario",
                "project-agent",
                List.of(new LearningFileView("artifact.md", "starter text")),
                List.of(new TaskCheckDefinition("content", type, "artifact.md", "3", true, 100, "Content")));
    }

    private LearningTaskDefinition taskWithPoints(String id, int points) {
        return new LearningTaskDefinition(
                id,
                id,
                "Summary",
                "beginner",
                "requirements",
                10,
                List.of("requirements"),
                "Objective",
                "Scenario",
                "project-agent",
                List.of(new LearningFileView("artifact.md", "starter text")),
                List.of(new TaskCheckDefinition("content", "min-length", "artifact.md", "3", true, points, "Content")));
    }

    private LearningTaskDefinition taskWithStarterContent(String id, String content) {
        return new LearningTaskDefinition(
                id,
                id,
                "Summary",
                "beginner",
                "requirements",
                10,
                List.of("requirements"),
                "Objective",
                "Scenario",
                "project-agent",
                List.of(new LearningFileView("artifact.md", content)),
                List.of(new TaskCheckDefinition("content", "min-length", "artifact.md", "3", true, 100, "Content")));
    }

    @Test
    void shouldAcceptQuizTasksWithoutStarterFiles() throws Exception {
        LearningCatalogService service = serviceWithQuizCatalog("specific-scene");
        assertEquals("quiz", service.getTask("first").evidenceMode());
        assertTrue(service.getTask("first").starterFiles().isEmpty());
        assertEquals("specific-scene", service.getTask("first").quizQuestions().get(0).correctOptionId());
    }

    @Test
    void shouldRejectQuizTasksWithInvalidOptions() {
        assertThrows(IllegalStateException.class, () -> serviceWithQuizCatalog("missing-option"));
    }

    private LearningTaskDefinition taskWithPrerequisites(String id, List<String> prerequisiteTaskIds) {
        return new LearningTaskDefinition(
                id,
                id,
                "Summary",
                "beginner",
                "requirements",
                10,
                List.of("requirements"),
                "Objective",
                "Scenario",
                "project-agent",
                List.of(new LearningFileView("artifact.md", "starter text")),
                List.of(new TaskCheckDefinition(
                        "content",
                        "min-length",
                        "artifact.md",
                        "3",
                        true,
                        100,
                        "Content")),
                "Lesson",
                List.of("artifact.md"),
                prerequisiteTaskIds,
                "document",
                List.of());
    }

    private LearningCatalogService serviceWithQuizCatalog(String correctOptionId) throws Exception {
        Path path = tempDir.resolve("quiz-catalog-" + System.nanoTime() + ".json");
        Files.writeString(path, """
                {
                  "version": 1,
                  "modules": [
                    {
                      "id": "module",
                      "title": "Module",
                      "summary": "Summary",
                      "level": "beginner",
                      "tasks": [
                        {
                          "id": "first",
                          "title": "first",
                          "summary": "Summary",
                          "level": "beginner",
                          "kind": "requirements",
                          "estimatedMinutes": 10,
                          "skills": ["requirements"],
                          "objective": "Objective",
                          "scenario": "Scenario",
                          "mentorAgentId": "project-agent",
                          "starterFiles": [],
                          "checks": [],
                          "lesson": "Lesson",
                          "deliverables": ["Complete the quiz"],
                          "prerequisiteTaskIds": [],
                          "evidenceMode": "quiz",
                          "references": [],
                          "passingScore": 100,
                          "quizQuestions": [
                            {
                              "id": "user-scope",
                              "prompt": "Which user is specific?",
                              "options": [
                                {"id": "all-users", "label": "All users"},
                                {"id": "specific-scene", "label": "A specific user"}
                              ],
                              "correctOptionId": "%s",
                              "points": 100,
                              "explanation": "Need a concrete user."
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """.formatted(correctOptionId));
        return new LearningCatalogService(
                objectMapper,
                path.toString(),
                TaskCheckRegistryTestFactory.standard(objectMapper));
    }
}
