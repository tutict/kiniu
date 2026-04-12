package com.kiniu.game.dto;

public record StoryGenerationResponse(
        StoryCatalogResponse story,
        AgentCatalogResponse agents,
        StoryAnalysisResponse analysis,
        String summary) {
}
