package com.kiniu.game.learn;

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
}