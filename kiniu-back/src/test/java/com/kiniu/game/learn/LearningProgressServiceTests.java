package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LearningProgressServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void shouldAdvanceToNextTaskAndPersistAtomically() throws Exception {
        LearningCatalogService catalogService = catalogService();
        Path progressPath = tempDir.resolve("progress.json");
        LearningProgressService service = new LearningProgressService(objectMapper, catalogService, progressPath);

        assertEquals("first", service.getProgress().currentTaskId());

        LearningProgress updated = service.recordAttempt("first", 100, List.of("requirements"));

        assertEquals("second", updated.currentTaskId());
        assertEquals(updated, objectMapper.readValue(progressPath.toFile(), LearningProgress.class));
        assertTrue(Files.list(tempDir).noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
    }

    @Test
    void shouldQuarantineCorruptProgressInsteadOfSilentlyOverwritingIt() throws Exception {
        LearningCatalogService catalogService = catalogService();
        Path progressPath = tempDir.resolve("progress.json");
        Files.writeString(progressPath, "{not-json");

        LearningProgressService service = new LearningProgressService(objectMapper, catalogService, progressPath);

        assertEquals("first", service.getProgress().currentTaskId());
        assertTrue(Files.list(tempDir)
                .anyMatch(path -> path.getFileName().toString().startsWith("progress.json.corrupt-")));
    }

    @Test
    void shouldKeepTheFurthestCurrentTaskWhenEarlierWorkIsRepeated() throws Exception {
        LearningProgressService service = new LearningProgressService(
                objectMapper,
                catalogService(),
                tempDir.resolve("forward-only-progress.json"));

        service.recordAttempt("first", 100, List.of("requirements"));
        service.recordAttempt("second", 100, List.of("data"));
        LearningProgress repeated = service.recordAttempt("first", 100, List.of("requirements"));

        assertEquals("third", repeated.currentTaskId());
    }

    private LearningCatalogService catalogService() throws Exception {
        LearningTaskDefinition first = task("first");
        LearningTaskDefinition second = task("second");
        LearningTaskDefinition third = task("third");
        LearningCatalogDefinition catalog = new LearningCatalogDefinition(
                1,
                List.of(new LearningModuleDefinition(
                        "module",
                        "Module",
                        "Summary",
                        "beginner",
                        List.of(first, second, third))));
        Path path = tempDir.resolve("catalog.json");
        objectMapper.writeValue(path.toFile(), catalog);
        return new LearningCatalogService(objectMapper, path.toString());
    }

    private LearningTaskDefinition task(String id) {
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
                List.of(new TaskCheckDefinition("content", "min-length", "artifact.md", "3", true, 100, "Content")));
    }
}
