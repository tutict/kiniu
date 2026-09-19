package com.kiniu.game.learn;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record LearningQuizQuestion(
        String id,
        String prompt,
        List<LearningQuizOption> options,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String correctOptionId,
        int points,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String explanation) {

    public LearningQuizQuestion {
        id = id == null ? "" : id.trim();
        prompt = prompt == null ? "" : prompt.trim();
        options = options == null ? List.of() : List.copyOf(options);
        correctOptionId = correctOptionId == null ? "" : correctOptionId.trim();
        explanation = explanation == null ? "" : explanation.trim();
    }
}
