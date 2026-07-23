package com.kiniu.game.dto;

import java.util.Map;

public record LearningCheckRequest(Map<String, String> files, String notes) {
}
