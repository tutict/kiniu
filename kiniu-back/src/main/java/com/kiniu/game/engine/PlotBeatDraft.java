package com.kiniu.game.engine;

import java.util.List;

public record PlotBeatDraft(
        String title,
        String narrative,
        List<String> choices,
        List<String> spotlightAgentIds) {

    public PlotBeatDraft {
        choices = choices == null ? List.of() : List.copyOf(choices);
        spotlightAgentIds = spotlightAgentIds == null ? List.of() : List.copyOf(spotlightAgentIds);
    }
}
