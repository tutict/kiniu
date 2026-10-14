package com.kiniu.game.dto;

import com.kiniu.game.learn.LearningAttempt;
import com.kiniu.game.learn.TaskCheckResult;

/**
 * Summary of a single attempt for the learner-facing history. Deliberately omits the raw submitted
 * answers and the per-question evidence; the review list carries what is needed to re-read a miss.
 */
public record LearningAttemptView(
        String attemptId,
        String createdAt,
        boolean passed,
        int score,
        int correctCount,
        int totalCount) {

    public static LearningAttemptView from(LearningAttempt attempt) {
        int total = attempt.results().size();
        int correct = (int) attempt.results().stream().filter(TaskCheckResult::passed).count();
        return new LearningAttemptView(
                attempt.attemptId(),
                attempt.createdAt(),
                attempt.passed(),
                attempt.score(),
                correct,
                total);
    }
}
