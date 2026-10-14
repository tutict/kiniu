package com.kiniu.game.dto;

/**
 * One question the learner most recently answered wrong and has not yet corrected, for the review
 * list. {@code evidence} and {@code message} are the same strings the check response already
 * returns, so the review never reveals more than a graded submission does.
 */
public record LearningReviewItemView(
        String taskId,
        String taskTitle,
        String questionId,
        String prompt,
        String evidence,
        String message,
        String attemptedAt) {
}
