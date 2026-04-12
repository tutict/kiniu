package com.kiniu.game.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GameRequest(String sessionId, String input, String choice) {
}
