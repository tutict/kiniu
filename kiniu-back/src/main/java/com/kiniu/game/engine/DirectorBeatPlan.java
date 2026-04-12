package com.kiniu.game.engine;

import java.util.List;

public record DirectorBeatPlan(
        String directorSummary,
        List<String> choices,
        List<String> spotlightAgentIds) {

    public DirectorBeatPlan {
        choices = choices == null ? List.of() : List.copyOf(choices);
        spotlightAgentIds = spotlightAgentIds == null ? List.of() : List.copyOf(spotlightAgentIds);
    }
}
