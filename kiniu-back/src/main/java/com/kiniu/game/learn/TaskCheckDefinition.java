package com.kiniu.game.learn;

public record TaskCheckDefinition(
        String id,
        String type,
        String path,
        String rule,
        boolean required,
        int points,
        String message) {
}
