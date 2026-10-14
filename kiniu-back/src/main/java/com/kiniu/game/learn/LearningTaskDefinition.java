package com.kiniu.game.learn;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record LearningTaskDefinition(
        String id,
        String title,
        String summary,
        String level,
        String kind,
        int estimatedMinutes,
        List<String> skills,
        String objective,
        String scenario,
        String mentorAgentId,
        List<LearningFileView> starterFiles,
        List<TaskCheckDefinition> checks,
        String lesson,
        List<String> deliverables,
        List<String> prerequisiteTaskIds,
        String evidenceMode,
        List<LearningReference> references,
        List<LearningQuizQuestion> quizQuestions,
        Integer passingScore,
        Boolean elective,
        String tonightPrompt,
        String takeaway) {

    public LearningTaskDefinition {
        skills = skills == null ? List.of() : List.copyOf(skills);
        starterFiles = starterFiles == null ? List.of() : List.copyOf(starterFiles);
        checks = checks == null ? List.of() : List.copyOf(checks);
        lesson = lesson == null ? "" : lesson.trim();
        deliverables = deliverables == null ? List.of() : List.copyOf(deliverables);
        prerequisiteTaskIds = prerequisiteTaskIds == null ? List.of() : List.copyOf(prerequisiteTaskIds);
        evidenceMode = evidenceMode == null || evidenceMode.isBlank() ? "document" : evidenceMode.trim();
        references = references == null ? List.of() : List.copyOf(references);
        quizQuestions = quizQuestions == null ? List.of() : List.copyOf(quizQuestions);
        passingScore = passingScore == null || passingScore <= 0 ? 100 : passingScore;
        elective = Boolean.TRUE.equals(elective);
        tonightPrompt = tonightPrompt == null ? "" : tonightPrompt.trim();
        takeaway = takeaway == null ? "" : takeaway.trim();
    }

    /**
     * Whether the learner-facing view should show the boundary gloss. This is the single source of
     * truth for that decision, emitted as {@code boundaryGloss} in the catalog response; the
     * frontend reads the flag and must not re-derive it. Every field the learner can see is
     * scanned, so the answer cannot drift from the content.
     */
    @JsonProperty(value = "boundaryGloss", access = JsonProperty.Access.READ_ONLY)
    public boolean boundaryGloss() {
        if (lesson.contains("这里说的边界")) {
            return false;
        }
        if (mentionsBoundary(summary)
                || mentionsBoundary(objective)
                || mentionsBoundary(scenario)
                || mentionsBoundary(lesson)
                || mentionsBoundary(takeaway)
                || deliverables.stream().anyMatch(this::mentionsBoundary)
                || checks.stream().anyMatch(check -> mentionsBoundary(check.message()))) {
            return true;
        }
        for (LearningQuizQuestion question : quizQuestions) {
            if (mentionsBoundary(question.prompt()) || mentionsBoundary(question.explanation())) {
                return true;
            }
            for (LearningQuizOption option : question.options()) {
                if (mentionsBoundary(option.label())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean mentionsBoundary(String text) {
        return text != null && text.contains("边界");
    }

    public LearningTaskDefinition(
            String id,
            String title,
            String summary,
            String level,
            String kind,
            int estimatedMinutes,
            List<String> skills,
            String objective,
            String scenario,
            String mentorAgentId,
            List<LearningFileView> starterFiles,
            List<TaskCheckDefinition> checks,
            String lesson,
            List<String> deliverables,
            List<String> prerequisiteTaskIds,
            String evidenceMode,
            List<LearningReference> references,
            List<LearningQuizQuestion> quizQuestions,
            Integer passingScore) {
        this(
                id,
                title,
                summary,
                level,
                kind,
                estimatedMinutes,
                skills,
                objective,
                scenario,
                mentorAgentId,
                starterFiles,
                checks,
                lesson,
                deliverables,
                prerequisiteTaskIds,
                evidenceMode,
                references,
                quizQuestions,
                passingScore,
                false,
                "",
                "");
    }

    public LearningTaskDefinition(
            String id,
            String title,
            String summary,
            String level,
            String kind,
            int estimatedMinutes,
            List<String> skills,
            String objective,
            String scenario,
            String mentorAgentId,
            List<LearningFileView> starterFiles,
            List<TaskCheckDefinition> checks,
            String lesson,
            List<String> deliverables,
            List<String> prerequisiteTaskIds,
            String evidenceMode,
            List<LearningReference> references,
            List<LearningQuizQuestion> quizQuestions,
            Integer passingScore,
            Boolean elective) {
        this(
                id,
                title,
                summary,
                level,
                kind,
                estimatedMinutes,
                skills,
                objective,
                scenario,
                mentorAgentId,
                starterFiles,
                checks,
                lesson,
                deliverables,
                prerequisiteTaskIds,
                evidenceMode,
                references,
                quizQuestions,
                passingScore,
                elective,
                "",
                "");
    }

    public LearningTaskDefinition(
            String id,
            String title,
            String summary,
            String level,
            String kind,
            int estimatedMinutes,
            List<String> skills,
            String objective,
            String scenario,
            String mentorAgentId,
            List<LearningFileView> starterFiles,
            List<TaskCheckDefinition> checks,
            String lesson,
            List<String> deliverables,
            List<String> prerequisiteTaskIds,
            String evidenceMode,
            List<LearningReference> references) {
        this(
                id,
                title,
                summary,
                level,
                kind,
                estimatedMinutes,
                skills,
                objective,
                scenario,
                mentorAgentId,
                starterFiles,
                checks,
                lesson,
                deliverables,
                prerequisiteTaskIds,
                evidenceMode,
                references,
                List.of(),
                100,
                false,
                "",
                "");
    }

    public LearningTaskDefinition(
            String id,
            String title,
            String summary,
            String level,
            String kind,
            int estimatedMinutes,
            List<String> skills,
            String objective,
            String scenario,
            String mentorAgentId,
            List<LearningFileView> starterFiles,
            List<TaskCheckDefinition> checks) {
        this(
                id,
                title,
                summary,
                level,
                kind,
                estimatedMinutes,
                skills,
                objective,
                scenario,
                mentorAgentId,
                starterFiles,
                checks,
                "",
                List.of(),
                List.of(),
                "document",
                List.of(),
                List.of(),
                100,
                false,
                "",
                "");
    }
}
