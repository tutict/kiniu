package com.kiniu.game.agent;

public record AgentTurnPlan(
        Agent agent,
        String objective,
        String memorySummary,
        int initiativeScore,
        boolean shouldSpeak,
        java.util.List<AgentScoreFactor> scoreFactors) {
}
