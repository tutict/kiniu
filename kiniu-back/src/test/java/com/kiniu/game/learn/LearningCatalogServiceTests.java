package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    void productionCatalogShouldExposeVersionThreeCurriculum() {
        LearningCatalogService service = new LearningCatalogService(
                objectMapper,
                Path.of("data", "learning-catalog.json").toString());
        LearningCatalogDefinition catalog = service.getCatalog();
        List<LearningTaskDefinition> tasks = catalog.modules().stream()
                .flatMap(module -> module.tasks().stream())
                .toList();

        assertEquals(3, catalog.version());
        assertEquals(8, catalog.modules().size());
        assertEquals(20, tasks.size());
        assertTrue(tasks.stream().allMatch(task -> task.lesson().length() >= 300 && task.lesson().length() <= 600));
        assertTrue(tasks.stream().allMatch(task -> task.checks().stream()
                .mapToInt(TaskCheckDefinition::points)
                .sum() == 100));
        assertTrue(tasks.stream()
                .filter(task -> "import".equals(task.evidenceMode()))
                .allMatch(task -> task.checks().stream()
                        .anyMatch(check -> "/capturedAt".equals(check.rule()))));
    }

    private LearningCatalogService serviceWith(LearningTaskDefinition... tasks) throws Exception {
        LearningCatalogDefinition catalog = new LearningCatalogDefinition(
                1,
                List.of(new LearningModuleDefinition("module", "Module", "Summary", "beginner", List.of(tasks))));
        Path path = tempDir.resolve("catalog-" + System.nanoTime() + ".json");
        objectMapper.writeValue(path.toFile(), catalog);
        return new LearningCatalogService(objectMapper, path.toString());
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
}
