package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LearningProgressTests {

    @Test
    void shouldRecordCompletionAndWeakSkillsWithoutOverwritingBestScore() {
        LearningProgress progress = LearningProgress.empty()
                .record("companion-agent", 62, List.of("评测", "安全边界"))
                .record("companion-agent", 88, List.of("评测", "安全边界"));

        assertEquals(List.of("companion-agent"), progress.completedTaskIds());
        assertEquals(88, progress.bestScores().get("companion-agent"));
        assertTrue(progress.weakSkills().contains("评测"));
    }
}
