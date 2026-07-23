package com.kiniu.game.learn;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LearningProgressService {

    private static final Logger log = LoggerFactory.getLogger(LearningProgressService.class);
    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final LearningCatalogService catalogService;
    private final Path progressPath;
    private LearningProgress progress;

    @Autowired
    public LearningProgressService(
            ObjectMapper objectMapper,
            LearningCatalogService catalogService,
            @Value("${game.learning.progress-path:data/learning-progress.json}") String progressPath) {
        this(objectMapper, catalogService, Paths.get(progressPath));
    }

    LearningProgressService(ObjectMapper objectMapper, LearningCatalogService catalogService, Path progressPath) {
        this.objectMapper = objectMapper;
        this.catalogService = catalogService;
        this.progressPath = progressPath.toAbsolutePath().normalize();
        this.progress = loadProgress();
    }

    public synchronized LearningProgress getProgress() {
        return progress;
    }

    public synchronized LearningProgress recordAttempt(String taskId, int score, List<String> skills) {
        LearningProgress recorded = progress.record(taskId, score, skills, taskId);
        progress = recorded.withCurrentTaskId(catalogService.nextTaskId(taskId, recorded));
        persist();
        return progress;
    }

    public synchronized LearningProgress reset() {
        progress = initialProgress();
        persist();
        return progress;
    }

    private LearningProgress loadProgress() {
        if (!Files.exists(progressPath)) {
            return initialProgress();
        }
        try {
            LearningProgress loaded = objectMapper.readValue(progressPath.toFile(), LearningProgress.class);
            validateLoadedProgress(loaded);
            return normalizeCurrentTask(loaded);
        } catch (IOException | RuntimeException exception) {
            log.error("Learning progress at {} is invalid and will be quarantined.", progressPath, exception);
            quarantineCorruptFile();
            return initialProgress();
        }
    }

    private LearningProgress initialProgress() {
        return LearningProgress.empty(catalogService.firstTaskId());
    }

    private void validateLoadedProgress(LearningProgress loaded) {
        if (loaded == null || loaded.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported learning progress schema.");
        }
        Set<String> catalogTaskIds = new HashSet<>();
        catalogService.getCatalog().modules().forEach(module ->
                module.tasks().forEach(task -> catalogTaskIds.add(task.id())));
        if (!loaded.currentTaskId().isBlank() && !catalogTaskIds.contains(loaded.currentTaskId())) {
            throw new IllegalStateException("Learning progress references an unknown current task.");
        }
        if (!catalogTaskIds.containsAll(loaded.completedTaskIds())
                || !catalogTaskIds.containsAll(loaded.bestScores().keySet())) {
            throw new IllegalStateException("Learning progress references unknown completed tasks.");
        }
    }

    private LearningProgress normalizeCurrentTask(LearningProgress loaded) {
        boolean currentIsUsable = !loaded.currentTaskId().isBlank()
                && !loaded.completedTaskIds().contains(loaded.currentTaskId())
                && catalogService.isUnlocked(loaded.currentTaskId(), loaded);
        if (currentIsUsable) {
            return loaded;
        }
        String fallback = loaded.completedTaskIds().isEmpty()
                ? catalogService.firstTaskId()
                : loaded.completedTaskIds().get(loaded.completedTaskIds().size() - 1);
        return loaded.withCurrentTaskId(catalogService.nextTaskId(fallback, loaded));
    }

    private void persist() {
        try {
            AtomicJsonStore.write(objectMapper, progressPath, progress);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist learning progress to " + progressPath, exception);
        }
    }

    private void quarantineCorruptFile() {
        try {
            String suffix = ".corrupt-" + Instant.now().toEpochMilli();
            Path quarantine = progressPath.resolveSibling(progressPath.getFileName() + suffix);
            Files.move(progressPath, quarantine, StandardCopyOption.REPLACE_EXISTING);
            log.warn("Moved invalid learning progress to {}.", quarantine);
        } catch (IOException exception) {
            log.error("Failed to quarantine invalid learning progress at {}.", progressPath, exception);
        }
    }
}
