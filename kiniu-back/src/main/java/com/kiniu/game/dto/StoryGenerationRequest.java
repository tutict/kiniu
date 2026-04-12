package com.kiniu.game.dto;

public record StoryGenerationRequest(
        String premise,
        String setting,
        String tone,
        String chapterGoal,
        String protagonistName,
        String companionName,
        String rivalName) {
}
