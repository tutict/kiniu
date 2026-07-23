package com.kiniu.game.learn;

import java.util.List;

public record LearningCatalogDefinition(int version, List<LearningModuleDefinition> modules) {

    public LearningCatalogDefinition {
        modules = modules == null ? List.of() : List.copyOf(modules);
    }
}
