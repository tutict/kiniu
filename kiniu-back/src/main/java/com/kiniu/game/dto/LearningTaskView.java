package com.kiniu.game.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.kiniu.game.learn.LearningTaskDefinition;

/**
 * Learner-facing view of a learning task: the domain task plus the gloss flags the UI needs.
 *
 * <p>The task is unwrapped so the JSON shape is unchanged; the two flags are the presentation
 * decision made by {@link LearningTaskGloss}.
 */
public record LearningTaskView(
        @JsonUnwrapped LearningTaskDefinition task,
        boolean boundaryGloss,
        boolean modelGloss) {

    public static LearningTaskView from(LearningTaskDefinition task) {
        return new LearningTaskView(task, LearningTaskGloss.boundary(task), LearningTaskGloss.model(task));
    }
}