package com.kiniu.game.dto;

import com.kiniu.game.state.WorldState;
import com.kiniu.game.story.StoryEvent;
import java.util.List;

public record GameResponse(
        String sessionId,
        String message,
        List<String> choices,
        List<BranchOptionView> branchOptions,
        WorldState state,
        List<AgentReplyView> agentReplies,
        String directorMessage,
        StoryEvent storyEvent,
        OrchestrationTraceView orchestration) {
}
