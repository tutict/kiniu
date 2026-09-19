package com.kiniu.game.learn;

public record LearningQuizOption(String id, String label) {

    public LearningQuizOption {
        id = id == null ? "" : id.trim();
        label = label == null ? "" : label.trim();
    }
}
