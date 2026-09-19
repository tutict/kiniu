package com.kiniu.game.learn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class QuizCheckService {

    static final int MAX_ANSWER_CHARS = 200;

    public List<TaskCheckResult> check(LearningTaskDefinition task, Map<String, String> answers) {
        if (!"quiz".equals(task.evidenceMode()) || task.quizQuestions().isEmpty()) {
            throw new IllegalArgumentException("Task is not a quiz.");
        }
        Map<String, String> safeAnswers = normalizeAnswers(answers);
        Set<String> questionIds = task.quizQuestions().stream()
                .map(LearningQuizQuestion::id)
                .collect(Collectors.toSet());
        if (!questionIds.containsAll(safeAnswers.keySet())) {
            throw new IllegalArgumentException("Quiz submission contains an unknown question.");
        }
        if (safeAnswers.size() != task.quizQuestions().size()
                || task.quizQuestions().stream().anyMatch(question -> !safeAnswers.containsKey(question.id()))) {
            throw new IllegalArgumentException("Answer every quiz question before submitting.");
        }

        return task.quizQuestions().stream()
                .map(question -> evaluate(question, safeAnswers.get(question.id())))
                .toList();
    }

    public int score(List<TaskCheckResult> results) {
        int total = results.stream().mapToInt(result -> Math.max(0, result.points())).sum();
        int earned = results.stream()
                .filter(TaskCheckResult::passed)
                .mapToInt(result -> Math.max(0, result.points()))
                .sum();
        return total == 0 ? 0 : Math.round((earned * 100f) / total);
    }

    public boolean passed(LearningTaskDefinition task, List<TaskCheckResult> results) {
        return !results.isEmpty() && score(results) >= task.passingScore();
    }

    private Map<String, String> normalizeAnswers(Map<String, String> answers) {
        Map<String, String> safeAnswers = new LinkedHashMap<>();
        if (answers == null) {
            return safeAnswers;
        }
        answers.forEach((rawKey, rawValue) -> {
            String key = rawKey == null ? "" : rawKey.trim();
            String value = rawValue == null ? "" : rawValue.trim();
            if (key.isBlank() || value.isBlank()) {
                throw new IllegalArgumentException("Quiz answers must include a question and an option.");
            }
            if (key.length() > MAX_ANSWER_CHARS || value.length() > MAX_ANSWER_CHARS) {
                throw new IllegalArgumentException("Quiz answer is too long.");
            }
            if (safeAnswers.put(key, value) != null) {
                throw new IllegalArgumentException("Quiz submission contains a duplicate question.");
            }
        });
        return safeAnswers;
    }

    private TaskCheckResult evaluate(LearningQuizQuestion question, String answerId) {
        LearningQuizOption selected = question.options().stream()
                .filter(option -> option.id().equals(answerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Quiz submission contains an unknown option."));
        boolean correct = question.correctOptionId().equals(selected.id());
        return new TaskCheckResult(
                question.id(),
                correct,
                true,
                question.points(),
                "你的选择：" + selected.label(),
                question.explanation());
    }
}
