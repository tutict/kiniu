package com.kiniu.game.dto;

import java.util.List;

public record OrchestrationTraceView(
        String storyEventId,
        String storyTitle,
        String storySourceType,
        String sceneId,
        String focusAgentId,
        OrchestrationPlannerView planner,
        OrchestrationCriticView critic,
        List<AIInvocationView> aiInvocations,
        List<String> spotlightAgentIds,
        List<String> activeAgentIds,
        List<String> speakingAgentIds,
        List<String> nextChoices,
        List<BranchOptionView> nextBranchOptions,
        List<OrchestrationAgentPlanView> plans) {
}
