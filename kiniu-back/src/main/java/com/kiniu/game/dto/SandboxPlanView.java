package com.kiniu.game.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SandboxPlanView(
        String id,
        String sessionId,
        Instant createdAt,
        String sceneId,
        String nodeId,
        String title,
        String summary,
        List<BranchOptionView> steps,
        int totalRelationshipDelta,
        List<String> finalFlags,
        Map<String, Integer> finalAffinityScores) {
}
