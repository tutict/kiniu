package com.kiniu.game.dto;

public record AgentReplyView(
        String agentId,
        String agentName,
        String role,
        String objective,
        String memorySummary,
        int initiativeScore,
        String message) {
}
