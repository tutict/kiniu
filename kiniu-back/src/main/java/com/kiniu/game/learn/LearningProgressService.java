package com.kiniu.game.learn;

import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private long reconciledRevision = -1L;

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
        this.reconciledRevision = catalogService.revision();
    }

    public synchronized LearningProgress getProgress() {
        reconcileWithCatalogIfChanged();
        return progress;
    }

    public synchronized LearningProgress recordAttempt(String taskId, int score, List<String> skills) {
        reconcileWithCatalogIfChanged();
        LearningProgress recorded = progress.record(taskId, score, skills, taskId);
        progress = recorded.withCurrentTaskId(catalogService.nextTaskId(taskId, recorded));
        persist();
        return progress;
    }

    public synchronized LearningProgress reset() {
        progress = initialProgress();
        reconciledRevision = catalogService.revision();
        persist();
        return progress;
    }

    private LearningProgress loadProgress() {
        if (!Files.exists(progressPath)) {
            return initialProgress();
        }
        try {
            LearningProgress loaded = objectMapper.readValue(progressPath.toFile(), LearningProgress.class);
            validateSchema(loaded);
            return reconcile(loaded);
        } catch (RuntimeException exception) {
            log.error("Learning progress at {} is invalid and will be quarantined.", progressPath, exception);
            quarantineCorruptFile();
            return initialProgress();
        }
    }

    private LearningProgress initialProgress() {
        return LearningProgress.empty(catalogService.firstTaskId());
    }

    private void validateSchema(LearningProgress loaded) {
        if (loaded == null || loaded.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported learning progress schema.");
        }
    }

    /**
     * Drops references to tasks that no longer exist in the catalog and repairs the current task,
     * instead of discarding the whole file. A catalog edit must never wipe a learner's progress.
     */
    private LearningProgress reconcile(LearningProgress loaded) {
        Set<String> knownTaskIds = new HashSet<>();
        catalogService.getCatalog().modules().forEach(module ->
                module.tasks().forEach(task -> knownTaskIds.add(task.id())));

        List<String> completed = loaded.completedTaskIds().stream()
                .filter(knownTaskIds::contains)
                .toList();
        Map<String, Integer> scores = new LinkedHashMap<>();
        loaded.bestScores().forEach((taskId, score) -> {
            if (knownTaskIds.contains(taskId)) {
                scores.put(taskId, score);
            }
        });
        LearningProgress pruned = new LearningProgress(
                loaded.schemaVersion(),
                loaded.currentTaskId(),
                completed,
                scores,
                loaded.weakSkills());
        return normalizeCurrentTask(pruned);
    }

    private void reconcileWithCatalogIfChanged() {
        // Force a lazy reload check so the revision reflects the file on disk before we compare.
        catalogService.getCatalog();
        long revision = catalogService.revision();
        if (revision == reconciledRevision) {
            return;
        }
        LearningProgress reconciled = reconcile(progress);
        reconciledRevision = revision;
        if (reconciled.equals(progress)) {
            return;
        }
        progress = reconciled;
        persist();
        log.info("Reconciled learning progress with reloaded catalog (revision {}).", revision);
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
