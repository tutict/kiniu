package com.kiniu.game.dto;

import java.util.List;

public record OrchestrationCriticView(
        String verdict,
        int focusScore,
        int castCoverageScore,
        int choicePressureScore,
        List<String> notes) {
}
