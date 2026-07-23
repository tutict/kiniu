package com.kiniu.game.dto;

import com.kiniu.game.learn.LearningProgress;
import com.kiniu.game.learn.TaskCheckResult;
import java.util.List;

public record LearningCheckResponse(
        String attemptId,
        String taskId,
        boolean passed,
        int score,
        List<TaskCheckResult> results,
        LearningProgress progress) {
}
