package com.kiniu.game.dto;

import com.kiniu.game.learn.LearningModuleDefinition;
import java.util.List;

public record LearningModuleView(
        String id,
        String title,
        String summary,
        String level,
        List<LearningTaskView> tasks) {

    public static LearningModuleView from(LearningModuleDefinition module) {
        return new LearningModuleView(
                module.id(),
                module.title(),
                module.summary(),
                module.level(),
                module.tasks().stream().map(LearningTaskView::from).toList());
    }
}