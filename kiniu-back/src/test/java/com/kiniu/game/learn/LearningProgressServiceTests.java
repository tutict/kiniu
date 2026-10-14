package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
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

    @Test
    void shouldRecommendTheFirstUnlockedBranchAfterAnAttempt() throws Exception {
        LearningCatalogService catalogService = branchCatalogService();
        LearningProgressService service = new LearningProgressService(
                objectMapper,
                catalogService,
                tempDir.resolve("branch-progress.json"));

        LearningProgress updated = service.recordAttempt("foundation", 100, List.of("requirements"));

        assertEquals("data", updated.currentTaskId());
        assertTrue(catalogService.isUnlocked("tools", updated));
        assertTrue(catalogService.isUnlocked("data", updated));
    }

    @Test
    void shouldExposeAnEmptyCurrentTaskWhenTheCatalogIsComplete() throws Exception {
        LearningProgressService service = new LearningProgressService(
                objectMapper,
                catalogService(),
                tempDir.resolve("completed-progress.json"));

        service.recordAttempt("first", 100, List.of("requirements"));
        service.recordAttempt("second", 100, List.of("data"));
        LearningProgress completed = service.recordAttempt("third", 100, List.of("release"));

        assertEquals("", completed.currentTaskId());
        assertEquals(List.of("first", "second", "third"), completed.completedTaskIds());
    }

    @Test
    void shouldKeepProgressWhenACatalogEditDropsACompletedTask() throws Exception {
        Path catalogPath = tempDir.resolve("editable-catalog.json");
        writeCatalog(catalogPath, List.of("first", "second", "third"));
        LearningCatalogService catalogService = new LearningCatalogService(
                objectMapper, catalogPath.toString(), TaskCheckRegistryTestFactory.standard(objectMapper));
        Path progressPath = tempDir.resolve("editable-progress.json");
        LearningProgressService service = new LearningProgressService(objectMapper, catalogService, progressPath);

        service.recordAttempt("first", 100, List.of("requirements"));
        assertTrue(service.getProgress().completedTaskIds().contains("first"));

        // Edit the catalog to drop the completed task, then hot-reload.
        writeCatalog(catalogPath, List.of("second", "third"));
        Files.setLastModifiedTime(catalogPath, FileTime.fromMillis(
                Files.getLastModifiedTime(catalogPath).toMillis() + 5_000));
        catalogService.getCatalog();

        // The in-memory progress is reconciled instead of being discarded.
        LearningProgress reconciled = service.getProgress();
        assertFalse(reconciled.completedTaskIds().contains("first"));
        assertEquals("second", reconciled.currentTaskId());

        // A restart must not quarantine the file.
        LearningProgressService restarted = new LearningProgressService(objectMapper, catalogService, progressPath);
        assertFalse(restarted.getProgress().completedTaskIds().contains("first"));
        assertTrue(Files.list(tempDir).noneMatch(p -> p.getFileName().toString().contains(".corrupt-")));
    }

    private void writeCatalog(Path path, List<String> ids) throws Exception {
        List<LearningTaskDefinition> tasks = ids.stream().map(this::task).toList();
        LearningCatalogDefinition catalog = new LearningCatalogDefinition(
                1,
                List.of(new LearningModuleDefinition("module", "Module", "Summary", "beginner", tasks)));
        objectMapper.writeValue(path.toFile(), catalog);
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
        return new LearningCatalogService(
                objectMapper,
                path.toString(),
                TaskCheckRegistryTestFactory.standard(objectMapper));
    }

    private LearningCatalogService branchCatalogService() throws Exception {
        LearningTaskDefinition foundation = task("foundation");
        LearningTaskDefinition data = task("data", List.of("foundation"));
        LearningTaskDefinition tools = task("tools", List.of("foundation"));
        LearningCatalogDefinition catalog = new LearningCatalogDefinition(
                1,
                List.of(new LearningModuleDefinition(
                        "module",
                        "Module",
                        "Summary",
                        "beginner",
                        List.of(foundation, data, tools))));
        Path path = tempDir.resolve("branch-catalog.json");
        objectMapper.writeValue(path.toFile(), catalog);
        return new LearningCatalogService(
                objectMapper,
                path.toString(),
                TaskCheckRegistryTestFactory.standard(objectMapper));
    }

    private LearningTaskDefinition task(String id) {
        return task(id, List.of());
    }

    private LearningTaskDefinition task(String id, List<String> prerequisites) {
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
                List.of(new TaskCheckDefinition("content", "min-length", "artifact.md", "3", true, 100, "Content")),
                "Lesson",
                List.of("Artifact"),
                prerequisites,
                "document",
                List.of());
    }
}
