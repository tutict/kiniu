package com.kiniu.game.engine;

import java.util.List;

public record BranchOption(
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

    public BranchOption {
        addedFlags = addedFlags == null ? List.of() : List.copyOf(addedFlags);
        removedFlags = removedFlags == null ? List.of() : List.copyOf(removedFlags);
    }
}
