package com.kiniu.game.engine;

import java.util.List;

public record TurnCritiqueSummary(
        String verdict,
        int focusScore,
        int castCoverageScore,
        int choicePressureScore,
        List<String> notes) {
}
