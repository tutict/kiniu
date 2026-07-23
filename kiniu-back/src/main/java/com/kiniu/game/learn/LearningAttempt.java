package com.kiniu.game.learn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record LearningAttempt(
        String attemptId,
        String taskId,
        String createdAt,
        boolean passed,
        int score,
        Map<String, String> files,
        List<TaskCheckResult> results,
        String notes) {

    public LearningAttempt {
        attemptId = attemptId == null ? "" : attemptId.trim();
        taskId = taskId == null ? "" : taskId.trim();
        createdAt = createdAt == null ? "" : createdAt.trim();
        files = files == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(files));
        results = results == null ? List.of() : List.copyOf(results);
        notes = notes == null ? "" : notes;
    }
}