package com.kiniu.game.dto;

import java.util.List;

public record OrchestrationAgentPlanView(
        String agentId,
        String agentName,
        String role,
        boolean shouldSpeak,
        int initiativeScore,
        String objective,
        String memorySummary,
        List<ScoreFactorView> scoreFactors) {
}
