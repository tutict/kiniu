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
        List<TaskCheckDefinition> checks) {

    public LearningTaskDefinition {
        skills = skills == null ? List.of() : List.copyOf(skills);
        starterFiles = starterFiles == null ? List.of() : List.copyOf(starterFiles);
        checks = checks == null ? List.of() : List.copyOf(checks);
    }
}
