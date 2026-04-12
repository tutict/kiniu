package com.kiniu.game.dto;

import java.util.List;
import java.util.Map;

public record StoryChoiceView(
        String id,
        String label,
        String description,
        String targetNodeId,
        List<String> requiredFlags,
        List<String> blockedFlags,
        Map<String, Integer> minimumAffinity,
        List<String> keywords,
        List<String> flagsToAdd,
        Map<String, Integer> affinityChanges) {
}
