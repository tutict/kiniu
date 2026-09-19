package com.kiniu.game.learn;

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
        Integer passingScore) {

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
                100);
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
                100);
    }
}
