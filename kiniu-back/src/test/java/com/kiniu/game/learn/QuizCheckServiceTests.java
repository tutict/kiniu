package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuizCheckServiceTests {

    private final QuizCheckService service = new QuizCheckService();

    @Test
    void shouldScoreCompleteScenarioAnswersAndApplyThePassingThreshold() {
        LearningTaskDefinition task = quizTask(50);

        List<TaskCheckResult> results = service.check(task, Map.of(
                "user-scenario", "specific-user",
                "business-outcome", "vague-goal"));

        assertEquals(2, results.size());
        assertTrue(results.get(0).passed());
        assertFalse(results.get(1).passed());
        assertEquals(50, service.score(results));
        assertFalse(service.passed(quizTask(80), results));
        assertTrue(service.passed(task, results));
        assertEquals("specific-user", results.get(0).correctOptionId());
        assertTrue(results.get(0).message().contains("具体用户"));
        assertTrue(results.get(1).evidence().contains("应选"));
        List<TaskCheckResult> passing = service.check(task, Map.of(
                "user-scenario", "specific-user",
                "business-outcome", "observable-outcome"));
        assertEquals(100, service.score(passing));
        assertTrue(service.passed(quizTask(80), passing));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.check(task, Map.of(
                        "user-scenario", "not-an-option",
                        "business-outcome", "observable-outcome")));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.check(task, Map.of("user-scenario", "specific-user")));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.check(task, Map.of(
                        "user-scenario", "specific-user",
                        "business-outcome", "x".repeat(QuizCheckService.MAX_ANSWER_CHARS + 1))));
    }

    private LearningTaskDefinition quizTask(int passingScore) {
        return new LearningTaskDefinition(
                "requirements-contract",
                "Scenario quiz",
                "Summary",
                "beginner",
                "requirements-quiz",
                10,
                List.of("requirements"),
                "Objective",
                "Scenario",
                "project-agent",
                List.of(),
                List.of(),
                "Lesson",
                List.of("Complete the quiz"),
                List.of(),
                "quiz",
                List.of(),
                List.of(
                        new LearningQuizQuestion(
                                "user-scenario",
                                "Which user definition is actionable?",
                                List.of(
                                        new LearningQuizOption("all-users", "All users"),
                                        new LearningQuizOption("specific-user", "A specific user in a concrete scene")),
                                "specific-user",
                                50,
                                "需求契约必须明确具体用户与使用场景。"),
                        new LearningQuizQuestion(
                                "business-outcome",
                                "Which goal is observable?",
                                List.of(
                                        new LearningQuizOption("vague-goal", "Make it smarter"),
                                        new LearningQuizOption("observable-outcome", "Return three actions in five seconds")),
                                "observable-outcome",
                                50,
                                "业务目标必须描述可观察的结果。")),
                passingScore);
    }
}
