package com.kiniu.game.dto;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kiniu.game.learn.LearningQuizOption;
import com.kiniu.game.learn.LearningQuizQuestion;
import com.kiniu.game.learn.LearningTaskDefinition;
import com.kiniu.game.learn.TaskCheckDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Pins the presentation rules for the gloss flags. The backend is the single source of truth; the
 * frontend only reads the emitted flags, so these rules must cover every field a learner can see.
 */
class LearningTaskGlossTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldShowTheBoundaryGlossWhenOnlyACheckMessageMentionsTheBoundary() {
        LearningTaskDefinition task = task(
                "Summary",
                "Lesson",
                List.of(new TaskCheckDefinition("content", "min-length", "artifact.md", "3", true, 100, "这里说的边界要写清")),
                List.of());

        assertTrue(LearningTaskGloss.boundary(task));
    }

    @Test
    void shouldShowTheModelGlossWhenOnlyAQuizExplanationMentionsTheModel() {
        LearningTaskDefinition task = task(
                "Summary",
                "Lesson",
                List.of(),
                List.of(new LearningQuizQuestion(
                        "q",
                        "Prompt",
                        List.of(
                                new LearningQuizOption("a", "Option A"),
                                new LearningQuizOption("b", "Option B")),
                        "a",
                        100,
                        "模型就是背后负责回答的那个程序。")));

        assertTrue(LearningTaskGloss.model(task));
    }

    @Test
    void shouldNotShowAGlossWhenNothingMentionsTheTerm() {
        LearningTaskDefinition task = task("Summary", "Lesson", List.of(), List.of());

        assertTrue(!LearningTaskGloss.boundary(task));
        assertTrue(!LearningTaskGloss.model(task));
    }

    @Test
    void shouldSuppressAGlossWhenTheLessonAlreadyExplainsIt() {
        LearningTaskDefinition boundaryTask = task(
                "Summary",
                "这里说的边界，就是帮谁、做成什么样、不能做什么。",
                List.of(),
                List.of());
        LearningTaskDefinition modelTask = task(
                "Summary",
                "模型就是背后负责回答的那个程序。",
                List.of(),
                List.of());

        assertTrue(!LearningTaskGloss.boundary(boundaryTask));
        assertTrue(!LearningTaskGloss.model(modelTask));
    }

    @Test
    void shouldEmitBothFlagsInTheTaskViewJson() throws Exception {
        LearningTaskDefinition task = task(
                "Summary",
                "Lesson",
                List.of(),
                List.of(new LearningQuizQuestion(
                        "q",
                        "边界在哪",
                        List.of(
                                new LearningQuizOption("a", "模型就是背后负责回答的那个程序"),
                                new LearningQuizOption("b", "Option B")),
                        "a",
                        100,
                        "Explanation")));

        String json = objectMapper.writeValueAsString(LearningTaskView.from(task));

        assertTrue(json.contains("\"boundaryGloss\":true"), json);
        assertTrue(json.contains("\"modelGloss\":true"), json);
        assertTrue(json.contains("\"id\":\"task\""), json);
    }

    private LearningTaskDefinition task(
            String summary,
            String lesson,
            List<TaskCheckDefinition> checks,
            List<LearningQuizQuestion> quizQuestions) {
        return new LearningTaskDefinition(
                "task",
                "Task",
                summary,
                "beginner",
                "requirements",
                10,
                List.of("requirements"),
                "Objective",
                "Scenario",
                "project-agent",
                List.of(),
                checks,
                lesson,
                List.of(),
                List.of(),
                "document",
                List.of(),
                quizQuestions,
                100,
                false,
                "",
                "");
    }
}