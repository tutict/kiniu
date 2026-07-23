package com.kiniu.game.learn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record LearningProgress(
        int schemaVersion,
        String currentTaskId,
        List<String> completedTaskIds,
        Map<String, Integer> bestScores,
        List<String> weakSkills) {

    public LearningProgress {
        currentTaskId = currentTaskId == null ? "" : currentTaskId.trim();
        completedTaskIds = completedTaskIds == null ? List.of() : List.copyOf(completedTaskIds);
        bestScores = bestScores == null ? Map.of() : Map.copyOf(bestScores);
        weakSkills = weakSkills == null ? List.of() : List.copyOf(weakSkills);
    }

    public static LearningProgress empty() {
        return new LearningProgress(1, "", List.of(), Map.of(), List.of());
    }

    public static LearningProgress empty(String firstTaskId) {
        return new LearningProgress(1, firstTaskId, List.of(), Map.of(), List.of());
    }

    public LearningProgress record(String taskId, int score, List<String> skills) {
        return record(taskId, score, skills, taskId);
    }

    public LearningProgress record(String taskId, int score, List<String> skills, String nextTaskId) {
        List<String> completed = new ArrayList<>(completedTaskIds);
        if (!completed.contains(taskId)) {
            completed.add(taskId);
        }

        Map<String, Integer> scores = new LinkedHashMap<>(bestScores);
        scores.merge(taskId, score, Math::max);

        List<String> weak = new ArrayList<>(weakSkills);
        if (score < 80 && skills != null) {
            for (String skill : skills) {
                if (skill != null && !skill.isBlank() && !weak.contains(skill)) {
                    weak.add(skill);
                }
            }
        }
        return new LearningProgress(schemaVersion, nextTaskId, completed, scores, weak);
    }

    public LearningProgress withCurrentTaskId(String taskId) {
        return new LearningProgress(schemaVersion, taskId, completedTaskIds, bestScores, weakSkills);
    }
}
