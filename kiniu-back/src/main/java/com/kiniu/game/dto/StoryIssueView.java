package com.kiniu.game.dto;

public record StoryIssueView(
        String severity,
        String code,
        String message,
        String nodeId,
        String choiceId) {
}
