package com.kiniu.game.dto;

import java.util.List;

public record BranchOptionView(
        String label,
        String intent,
        String risk,
        String targetMood,
        String targetAgentId,
        String consequenceSummary,
        int relationshipDelta,
        List<String> addedFlags,
        List<String> removedFlags,
        String source) {
}
