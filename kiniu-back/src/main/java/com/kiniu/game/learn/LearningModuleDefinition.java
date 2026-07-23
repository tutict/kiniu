package com.kiniu.game.learn;

import java.util.List;

public record LearningModuleDefinition(
        String id,
        String title,
        String summary,
        String level,
        List<LearningTaskDefinition> tasks) {

    public LearningModuleDefinition {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
