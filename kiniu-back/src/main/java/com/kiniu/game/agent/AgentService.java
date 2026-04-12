package com.kiniu.game.agent;

import com.kiniu.game.ai.AIService;
import com.kiniu.game.dto.AgentReplyView;
import com.kiniu.game.state.WorldState;
import com.kiniu.game.story.StoryEvent;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AgentService {

    private final AIService aiService;

    public AgentService(AIService aiService) {
        this.aiService = aiService;
    }

    public AgentReplyView generateResponse(
            AgentTurnPlan turnPlan,
            String playerInput,
            String playerChoice,
            WorldState worldState,
            List<String> recentDialogue,
            StoryEvent storyBeat) {
        Agent agent = turnPlan.agent();
        String reply = aiService.generateReply(
                agent,
                turnPlan,
                playerInput,
                playerChoice,
                worldState,
                recentDialogue,
                storyBeat);
        return new AgentReplyView(
                agent.id(),
                agent.name(),
                agent.role(),
                turnPlan.objective(),
                turnPlan.memorySummary(),
                turnPlan.initiativeScore(),
                reply);
    }

    public String generateMemoryNote(
            AgentTurnPlan turnPlan,
            StoryEvent storyBeat,
            String playerInput,
            String playerChoice,
            String reply) {
        return aiService.generateAgentMemoryNote(
                turnPlan.agent(),
                turnPlan,
                storyBeat,
                playerInput,
                playerChoice,
                reply);
    }
}
