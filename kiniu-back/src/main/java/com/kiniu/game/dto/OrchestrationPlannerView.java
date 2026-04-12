package com.kiniu.game.dto;

import java.util.List;

public record OrchestrationPlannerView(
        String sceneGoal,
        String tensionLabel,
        String pacingLabel,
        String directorIntent,
        List<String> risks) {
}
