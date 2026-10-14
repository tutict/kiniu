package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the boundary-gloss rule. The backend is the single source of truth for whether the gloss is
 * shown; the frontend only reads the emitted flag, so this rule must cover every field a learner
 * can see.
 */
class LearningTaskDefinitionGlossTests {

    @Test
    void shouldShowTheGlossWhenOnlyACheckMessageMentionsTheBoundary() {
        LearningTaskDefinition task = taskWithCheckMessage("这里说的边界要写清");

        assertTrue(task.boundaryGloss());
    }

    @Test
    void shouldNotShowTheGlossWhenNothingMentionsTheBoundary() {
        LearningTaskDefinition task = taskWithCheckMessage("写清帮谁、做成什么样、不能做什么");

        assertFalse(task.boundaryGloss());
    }

    @Test
    void shouldSuppressTheGlossWhenTheLessonAlreadyExplainsTheBoundary() {
        LearningTaskDefinition task = new LearningTaskDefinition(
                "task",
                "Task",
                "Summary",
                "beginner",
                "requirements",
                10,
                List.of("requirements"),
                "Objective",
                "Scenario",
                "project-agent",
                List.of(),
                List.of(),
                "这里说的边界，就是帮谁、做成什么样、不能做什么。",
                List.of(),
                List.of(),
                "document",
                List.of(),
                List.of(),
                100,
                false,
                "",
                "");

        assertFalse(task.boundaryGloss());
    }

    private LearningTaskDefinition taskWithCheckMessage(String message) {
        return new LearningTaskDefinition(
                "task",
                "Task",
                "Summary",
                "beginner",
                "requirements",
                10,
                List.of("requirements"),
                "Objective",
                "Scenario",
                "project-agent",
                List.of(),
                List.of(new TaskCheckDefinition("content", "min-length", "artifact.md", "3", true, 100, message)));
    }
}