package com.kiniu.game.dto;

import java.util.List;

public record StoryNodeView(
        String id,
        String sceneId,
        String title,
        String speakerId,
        String narrative,
        List<String> tags,
        List<String> enterFlags,
        java.util.Map<String, Integer> enterAffinityChanges,
        List<StoryChoiceView> choices) {
}
