package com.kiniu.game.dto;

import java.util.Map;

public record LearningCheckRequest(Map<String, String> files, Map<String, String> answers, String notes) {
}
