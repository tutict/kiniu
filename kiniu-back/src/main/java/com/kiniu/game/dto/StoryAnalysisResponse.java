package com.kiniu.game.dto;

import java.util.List;

public record StoryAnalysisResponse(
        String entryNodeId,
        int totalNodes,
        int totalChoices,
        List<String> reachableNodeIds,
        List<String> unreachableNodeIds,
        List<String> endingNodeIds,
        List<String> sceneIds,
        int errorCount,
        int warningCount,
        List<StoryIssueView> issues) {
}
