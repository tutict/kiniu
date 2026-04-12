package com.kiniu.game.agent;

import java.util.List;
import java.util.Map;

public record Agent(
        String id,
        String name,
        String role,
        String summary,
        String personality,
        String systemPrompt,
        List<String> activeScenes,
        Map<String, String> personalityParameters,
        List<String> coreGoals,
        List<String> hiddenMotives,
        int initiative,
        String memoryStyle) {

    public Agent {
        activeScenes = activeScenes == null ? List.of() : List.copyOf(activeScenes);
        personalityParameters = personalityParameters == null ? Map.of() : Map.copyOf(personalityParameters);
        coreGoals = coreGoals == null ? List.of() : List.copyOf(coreGoals);
        hiddenMotives = hiddenMotives == null ? List.of() : List.copyOf(hiddenMotives);
        memoryStyle = memoryStyle == null || memoryStyle.isBlank() ? "episodic" : memoryStyle;
    }
}
