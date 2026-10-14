package com.kiniu.game.dto;

import com.kiniu.game.learn.LearningQuizOption;
import com.kiniu.game.learn.LearningQuizQuestion;
import com.kiniu.game.learn.LearningTaskDefinition;

/**
 * Presentation rules for the learner-facing gloss bubbles.
 *
 * <p>Whether a gloss is shown is a display decision, not part of the learning domain, so it lives
 * here instead of on {@link LearningTaskDefinition}. The catalog response and the frontend (which
 * reads the emitted flag) both go through this single rule, so the two cannot drift apart.
 */
public final class LearningTaskGloss {

    private static final String BOUNDARY = "边界";
    private static final String MODEL = "模型";
    private static final String BOUNDARY_ALREADY_EXPLAINED = "这里说的边界";
    private static final String MODEL_ALREADY_EXPLAINED = "模型就是";

    private LearningTaskGloss() {
    }

    public static boolean boundary(LearningTaskDefinition task) {
        return show(task, BOUNDARY, BOUNDARY_ALREADY_EXPLAINED);
    }

    public static boolean model(LearningTaskDefinition task) {
        return show(task, MODEL, MODEL_ALREADY_EXPLAINED);
    }

    /**
     * Shows the gloss when any learner-visible field mentions the term, unless the lesson already
     * explains it. Every field the learner can see is scanned, including check messages and quiz
     * explanations, so the flag cannot drift from the content.
     */
    private static boolean show(LearningTaskDefinition task, String term, String alreadyExplained) {
        if (task.lesson().contains(alreadyExplained)) {
            return false;
        }
        if (mentions(task.summary(), term)
                || mentions(task.objective(), term)
                || mentions(task.scenario(), term)
                || mentions(task.lesson(), term)
                || mentions(task.takeaway(), term)
                || task.deliverables().stream().anyMatch(text -> mentions(text, term))
                || task.checks().stream().anyMatch(check -> mentions(check.message(), term))) {
            return true;
        }
        for (LearningQuizQuestion question : task.quizQuestions()) {
            if (mentions(question.prompt(), term) || mentions(question.explanation(), term)) {
                return true;
            }
            for (LearningQuizOption option : question.options()) {
                if (mentions(option.label(), term)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean mentions(String text, String term) {
        return text != null && text.contains(term);
    }
}