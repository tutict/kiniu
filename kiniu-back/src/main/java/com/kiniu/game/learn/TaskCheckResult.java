package com.kiniu.game.learn;

public record TaskCheckResult(
        String checkId,
        boolean passed,
        boolean required,
        int points,
        String evidence,
        String message,
        String correctOptionId) {

    public TaskCheckResult {
        correctOptionId = correctOptionId == null ? "" : correctOptionId;
    }

    public TaskCheckResult(
            String checkId,
            boolean passed,
            boolean required,
            int points,
            String evidence,
            String message) {
        this(checkId, passed, required, points, evidence, message, "");
    }
}
