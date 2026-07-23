package com.kiniu.game.story;

import java.util.LinkedHashSet;
import java.util.List;

public record StoryEvent(
        String id,
        String sourceNodeId,
        String targetNodeId,
        String targetScene,
        String speakerId,
        String title,
        String narrative,
        List<String> choices,
        String sourceType,
        String directorSummary,
        List<String> spotlightAgentIds) {

    public StoryEvent {
        choices = choices == null ? List.of() : List.copyOf(choices);
        sourceType = sourceType == null || sourceType.isBlank() ? "seed" : sourceType;
        directorSummary = directorSummary == null ? "" : directorSummary;
        spotlightAgentIds = spotlightAgentIds == null ? List.of() : List.copyOf(spotlightAgentIds);
    }

    public static StoryEvent seed(
            String id,
            String sourceNodeId,
            String targetNodeId,
            String targetScene,
            String speakerId,
            String title,
            String narrative,
            List<String> choices) {
        return new StoryEvent(
                id,
                sourceNodeId,
                targetNodeId,
                targetScene,
                speakerId,
                title,
                narrative,
                choices,
                "seed",
                "",
                List.of(speakerId));
    }

    public static StoryEvent generated(
            String id,
            String sourceNodeId,
            String targetNodeId,
            String targetScene,
            String speakerId,
            String title,
            String narrative,
            List<String> choices,
            List<String> spotlightAgentIds) {
        return new StoryEvent(
                id,
                sourceNodeId,
                targetNodeId,
                targetScene,
                speakerId,
                title,
                narrative,
                choices,
                "generated",
                "",
                spotlightAgentIds);
    }

    public StoryEvent withDirection(String directorSummary, List<String> choices, List<String> spotlightAgentIds) {
        return new StoryEvent(
                id,
                sourceNodeId,
                targetNodeId,
                targetScene,
                speakerId,
                title,
                narrative,
                choices,
                sourceType,
                directorSummary,
                spotlightAgentIds);
    }

    public StoryEvent withSpeaker(String speakerId) {
        LinkedHashSet<String> spotlight = new LinkedHashSet<>();
        spotlight.add(speakerId);
        spotlight.addAll(spotlightAgentIds);
        return new StoryEvent(
                id,
                sourceNodeId,
                targetNodeId,
                targetScene,
                speakerId,
                title,
                narrative,
                choices,
                sourceType,
                directorSummary,
                List.copyOf(spotlight));
    }

    public boolean isGenerated() {
        return "generated".equalsIgnoreCase(sourceType);
    }
}
