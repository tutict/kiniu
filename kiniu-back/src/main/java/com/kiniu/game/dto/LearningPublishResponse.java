package com.kiniu.game.dto;

import com.kiniu.game.agent.Agent;
import com.kiniu.game.dto.AgentCatalogResponse;

public record LearningPublishResponse(
        String taskId,
        String attemptId,
        Agent agent,
        AgentCatalogResponse catalog) {
}