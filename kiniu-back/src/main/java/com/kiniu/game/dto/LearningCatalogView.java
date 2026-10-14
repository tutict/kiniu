package com.kiniu.game.dto;

import com.kiniu.game.learn.LearningCatalogDefinition;
import java.util.List;

public record LearningCatalogView(int version, List<LearningModuleView> modules) {

    public static LearningCatalogView from(LearningCatalogDefinition catalog) {
        return new LearningCatalogView(
                catalog.version(),
                catalog.modules().stream().map(LearningModuleView::from).toList());
    }
}