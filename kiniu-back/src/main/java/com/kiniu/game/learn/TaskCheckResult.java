package com.kiniu.game.learn;

public record TaskCheckResult(
        String checkId,
        boolean passed,
        boolean required,
        int points,
        String evidence,
        String message) {
}
