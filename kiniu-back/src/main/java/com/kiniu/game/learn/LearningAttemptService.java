package com.kiniu.game.learn;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LearningAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LearningAttemptService.class);
    private static final int MAX_ATTEMPTS = 200;

    private final ObjectMapper objectMapper;
    private final Path attemptsPath;
    private List<LearningAttempt> attempts;

    @Autowired
    public LearningAttemptService(
            ObjectMapper objectMapper,
            @Value("${game.learning.attempts-path:data/learning-attempts.json}") String attemptsPath) {
        this(objectMapper, Paths.get(attemptsPath));
    }

    LearningAttemptService(ObjectMapper objectMapper, Path attemptsPath) {
        this.objectMapper = objectMapper;
        this.attemptsPath = attemptsPath.toAbsolutePath().normalize();
        this.attempts = load();
    }

    public synchronized LearningAttempt record(
            String taskId,
            boolean passed,
            int score,
            Map<String, String> files,
            List<TaskCheckResult> results,
            String notes) {
        LearningAttempt attempt = new LearningAttempt(
                "attempt-" + UUID.randomUUID(),
                taskId,
                Instant.now().toString(),
                passed,
                score,
                files,
                results,
                notes);
        List<LearningAttempt> next = new ArrayList<>(attempts);
        next.add(attempt);
        if (next.size() > MAX_ATTEMPTS) {
            next = new ArrayList<>(next.subList(next.size() - MAX_ATTEMPTS, next.size()));
        }
        attempts = List.copyOf(next);
        persist();
        return attempt;
    }

    public synchronized LearningAttempt get(String attemptId) {
        return attempts.stream()
                .filter(attempt -> attempt.attemptId().equals(attemptId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown learning attempt: " + attemptId));
    }

    public synchronized LearningAttempt getForTask(String attemptId, String taskId) {
        LearningAttempt attempt = get(attemptId);
        if (!attempt.taskId().equals(taskId)) {
            throw new IllegalArgumentException("Learning attempt does not belong to this task.");
        }
        return attempt;
    }

    public synchronized List<LearningAttempt> listForTask(String taskId) {
        return attempts.stream()
                .filter(attempt -> attempt.taskId().equals(taskId))
                .sorted(Comparator.comparing(LearningAttempt::createdAt).reversed())
                .toList();
    }

    private List<LearningAttempt> load() {
        if (!Files.exists(attemptsPath)) {
            return List.of();
        }
        try {
            List<LearningAttempt> loaded = objectMapper.readValue(
                    attemptsPath.toFile(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, LearningAttempt.class));
            if (loaded == null || loaded.isEmpty()) {
                return List.of();
            }
            int start = Math.max(0, loaded.size() - MAX_ATTEMPTS);
            return List.copyOf(loaded.subList(start, loaded.size()));
        } catch (IOException | RuntimeException exception) {
            log.error("Learning attempts at {} are invalid and will be quarantined.", attemptsPath, exception);
            quarantineCorruptFile();
            return List.of();
        }
    }

    private void quarantineCorruptFile() {
        try {
            Path quarantine = attemptsPath.resolveSibling(
                    attemptsPath.getFileName() + ".corrupt-" + Instant.now().toEpochMilli());
            Files.move(attemptsPath, quarantine, StandardCopyOption.REPLACE_EXISTING);
            log.warn("Moved invalid learning attempts to {}.", quarantine);
        } catch (IOException exception) {
            log.error("Failed to quarantine invalid learning attempts at {}.", attemptsPath, exception);
        }
    }

    private void persist() {
        try {
            AtomicJsonStore.write(objectMapper, attemptsPath, attempts);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist learning attempts to " + attemptsPath, exception);
        }
    }
}