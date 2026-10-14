package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kiniu.game.dto.LearningAttemptView;
import com.kiniu.game.dto.LearningReviewItemView;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LearningReviewServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    private LearningAttemptService attempts;
    private LearningReviewService reviewService;

    @BeforeEach
    void setUp() {
        attempts = new LearningAttemptService(objectMapper, tempDir.resolve("attempts.json"));
        reviewService = new LearningReviewService(attempts, catalogService());
    }

    @Test
    void shouldListOnlyTheMostRecentWrongAnswerPerQuestion() {
        attempts.record("first", false, 0, Map.of(), List.of(
                wrong("q1", "解释一"),
                wrong("q2", "解释二")), "");
        attempts.record("first", true, 100, Map.of(), List.of(
                right("q1"),
                wrong("q2", "解释二")), "");

        List<LearningReviewItemView> review = reviewService.review();

        assertEquals(1, review.size());
        assertEquals("q2", review.get(0).questionId());
        assertEquals("first", review.get(0).taskId());
        assertEquals("第一课", review.get(0).taskTitle());
        assertEquals("第二题问什么", review.get(0).prompt());
        assertTrue(review.get(0).message().contains("解释二"));
    }

    @Test
    void shouldDropAQuestionOnceItHasBeenAnsweredCorrectly() {
        attempts.record("first", false, 0, Map.of(), List.of(wrong("q1", "解释一")), "");
        attempts.record("first", true, 100, Map.of(), List.of(right("q1")), "");

        assertTrue(reviewService.review().isEmpty());
    }

    @Test
    void shouldOrderMissesNewestFirst() {
        attempts.record("first", false, 0, Map.of(), List.of(wrong("q1", "解释一")), "");
        attempts.record("second", false, 0, Map.of(), List.of(wrong("s1", "解释二")), "");

        List<LearningReviewItemView> review = reviewService.review();

        assertEquals(List.of("s1", "q1"), review.stream().map(LearningReviewItemView::questionId).toList());
    }

    @Test
    void shouldSkipQuestionsWhoseTaskNoLongerExistsInTheCatalog() {
        attempts.record("removed-task", false, 0, Map.of(), List.of(wrong("q1", "解释一")), "");

        assertTrue(reviewService.review().isEmpty());
    }

    @Test
    void shouldReturnAnEmptyReviewWhenThereAreNoAttempts() {
        assertTrue(reviewService.review().isEmpty());
    }

    @Test
    void shouldSummarizeAttemptsForATaskNewestFirstAndCapped() {
        for (int index = 0; index < 25; index++) {
            attempts.record("first", index % 2 == 0, index, Map.of(), List.of(wrong("q1", "解释一")), "");
        }

        List<LearningAttemptView> history = reviewService.attemptsForTask("first", 20);

        assertEquals(20, history.size());
        assertEquals(24, history.get(0).score());
        assertEquals(1, history.get(0).totalCount());
        assertEquals(0, history.get(0).correctCount());
    }

    private TaskCheckResult wrong(String questionId, String message) {
        return new TaskCheckResult(questionId, false, true, 100, "你选的是：A。题目要的是：B。", message);
    }

    private TaskCheckResult right(String questionId) {
        return new TaskCheckResult(questionId, true, true, 100, "你选的是：B", "解释");
    }

    private LearningCatalogService catalogService() {
        try {
            Path path = tempDir.resolve("review-catalog-" + System.nanoTime() + ".json");
            Files.writeString(path, """
                    {
                      "version": 1,
                      "modules": [
                        {
                          "id": "module",
                          "title": "Module",
                          "summary": "Summary",
                          "level": "beginner",
                          "tasks": [
                            {
                              "id": "first",
                              "title": "第一课",
                              "summary": "Summary",
                              "level": "beginner",
                              "kind": "requirements",
                              "estimatedMinutes": 10,
                              "skills": ["requirements"],
                              "objective": "Objective",
                              "scenario": "Scenario",
                              "mentorAgentId": "project-agent",
                              "starterFiles": [],
                              "checks": [],
                              "lesson": "Lesson",
                              "deliverables": ["Complete the quiz"],
                              "prerequisiteTaskIds": [],
                              "evidenceMode": "quiz",
                              "references": [],
                              "passingScore": 100,
                              "quizQuestions": [
                                {
                                  "id": "q1",
                                  "prompt": "第一题问什么",
                                  "options": [
                                    {"id": "a", "label": "选 A"},
                                    {"id": "b", "label": "选 B"}
                                  ],
                                  "correctOptionId": "b",
                                  "points": 50,
                                  "explanation": "解释一"
                                },
                                {
                                  "id": "q2",
                                  "prompt": "第二题问什么",
                                  "options": [
                                    {"id": "a", "label": "选 A"},
                                    {"id": "b", "label": "选 B"}
                                  ],
                                  "correctOptionId": "b",
                                  "points": 50,
                                  "explanation": "解释二"
                                }
                              ]
                            },
                            {
                              "id": "second",
                              "title": "第二课",
                              "summary": "Summary",
                              "level": "beginner",
                              "kind": "requirements",
                              "estimatedMinutes": 10,
                              "skills": ["requirements"],
                              "objective": "Objective",
                              "scenario": "Scenario",
                              "mentorAgentId": "project-agent",
                              "starterFiles": [],
                              "checks": [],
                              "lesson": "Lesson",
                              "deliverables": ["Complete the quiz"],
                              "prerequisiteTaskIds": ["first"],
                              "evidenceMode": "quiz",
                              "references": [],
                              "passingScore": 100,
                              "quizQuestions": [
                                {
                                  "id": "s1",
                                  "prompt": "第二题问什么",
                                  "options": [
                                    {"id": "a", "label": "选 A"},
                                    {"id": "b", "label": "选 B"}
                                  ],
                                  "correctOptionId": "b",
                                  "points": 100,
                                  "explanation": "解释二"
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                    """);
            return new LearningCatalogService(
                    objectMapper, path.toString(), TaskCheckRegistryTestFactory.standard(objectMapper));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
