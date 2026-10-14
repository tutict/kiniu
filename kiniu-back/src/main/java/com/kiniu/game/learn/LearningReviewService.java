package com.kiniu.game.learn;

import com.kiniu.game.dto.LearningAttemptView;
import com.kiniu.game.dto.LearningReviewItemView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Read-only views over the learner's own attempt history: a per-task summary and a cross-task list
 * of questions they most recently got wrong.
 */
@Service
public class LearningReviewService {

    private final LearningAttemptService attemptService;
    private final LearningCatalogService catalogService;

    public LearningReviewService(LearningAttemptService attemptService, LearningCatalogService catalogService) {
        this.attemptService = attemptService;
        this.catalogService = catalogService;
    }

    /** Recent attempts for one task, newest first, capped at {@code limit}. */
    public List<LearningAttemptView> attemptsForTask(String taskId, int limit) {
        return attemptService.listForTask(taskId).stream()
                .limit(Math.max(0, limit))
                .map(LearningAttemptView::from)
                .toList();
    }

    /**
     * Questions whose most recent attempt was wrong and that have not been answered correctly since.
     * Newest miss first. A question the learner has already corrected is dropped, and questions whose
     * task or prompt no longer exists in the catalog are skipped.
     */
    public List<LearningReviewItemView> review() {
        Set<String> seen = new HashSet<>();
        List<LearningReviewItemView> items = new ArrayList<>();
        for (LearningAttempt attempt : attemptService.listAll()) {
            LearningTaskDefinition task = findTask(attempt.taskId());
            if (task == null) {
                continue;
            }
            for (TaskCheckResult result : attempt.results()) {
                if (!seen.add(attempt.taskId() + "::" + result.checkId())) {
                    continue;
                }
                if (result.passed()) {
                    continue;
                }
                String prompt = promptFor(task, result.checkId());
                if (prompt == null) {
                    continue;
                }
                items.add(new LearningReviewItemView(
                        attempt.taskId(),
                        task.title(),
                        result.checkId(),
                        prompt,
                        result.evidence(),
                        result.message(),
                        attempt.createdAt()));
            }
        }
        return List.copyOf(items);
    }

    private LearningTaskDefinition findTask(String taskId) {
        return catalogService.getCatalog().modules().stream()
                .flatMap(module -> module.tasks().stream())
                .filter(task -> task.id().equals(taskId))
                .findFirst()
                .orElse(null);
    }

    private String promptFor(LearningTaskDefinition task, String questionId) {
        return task.quizQuestions().stream()
                .filter(question -> question.id().equals(questionId))
                .map(LearningQuizQuestion::prompt)
                .findFirst()
                .orElse(null);
    }
}
