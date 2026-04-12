package com.kiniu.game.dto;

import com.kiniu.game.state.WorldState;
import com.kiniu.game.story.StoryEvent;
import java.time.Instant;
import java.util.List;

public record SessionTurnView(
        String id,
        String parentTurnId,
        Instant timestamp,
        String playerInput,
        String playerChoice,
        String sceneId,
        String nodeId,
        String storyEventId,
        StoryEvent storyEvent,
        String directorMessage,
        String summary,
        List<String> presentedChoices,
        List<BranchOptionView> presentedBranchOptions,
        List<AgentReplyView> agentReplies,
        WorldState stateSnapshot,
        OrchestrationTraceView orchestration) {
}
