package com.kiniu.game.engine;

import java.util.List;

public record TurnPlannerBrief(
        String sceneGoal,
        String tensionLabel,
        String pacingLabel,
        String directorIntent,
        List<String> risks) {
}
