package com.kiniu.game.dto;

public record StoryAnalysisRequest(
        StoryCatalogResponse story,
        AgentCatalogResponse agents) {
}
