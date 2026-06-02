package com.kiniu.game.engine;

import com.kiniu.game.agent.Agent;
import com.kiniu.game.ai.AIService;
import com.kiniu.game.state.WorldState;
import com.kiniu.game.story.StoryEvent;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TurnPlannerService {

    private final AIService aiService;

    public TurnPlannerService(AIService aiService) {
        this.aiService = aiService;
    }

    public TurnPlannerBrief plan(
            WorldState worldState,
            StoryEvent storyEvent,
            List<Agent> activeAgents,
            String playerInput,
            String playerChoice,
            List<String> nextChoices) {
        TurnPlannerBrief fallbackBrief = buildFallbackBrief(
                worldState,
                storyEvent,
                activeAgents,
                playerInput,
                playerChoice,
                nextChoices);
        return aiService.generatePlannerBrief(
                worldState,
                storyEvent,
                activeAgents,
                playerInput,
                playerChoice,
                nextChoices,
                fallbackBrief);
    }

    private TurnPlannerBrief buildFallbackBrief(
            WorldState worldState,
            StoryEvent storyEvent,
            List<Agent> activeAgents,
            String playerInput,
            String playerChoice,
            List<String> nextChoices) {
        String playerMove = !safe(playerChoice).isBlank() ? playerChoice : safe(playerInput);
        String sceneGoal = "Advance " + storyEvent.title() + " in workspace " + worldState.getCurrentScene()
                + " after the user move \"" + (playerMove.isBlank() ? "silence" : playerMove) + "\".";
        String tensionLabel = resolveTension(worldState, storyEvent);
        String pacingLabel = resolvePacing(nextChoices, storyEvent);
        String directorIntent = buildDirectorIntent(storyEvent, activeAgents);
        List<String> risks = buildRisks(worldState, storyEvent, activeAgents, nextChoices);
        return new TurnPlannerBrief(sceneGoal, tensionLabel, pacingLabel, directorIntent, risks);
    }

    private String resolveTension(WorldState worldState, StoryEvent storyEvent) {
        if ("generated".equals(storyEvent.sourceType())) {
            return "volatile";
        }
        if (worldState.getFlags().contains("generated-conversation-active")) {
            return "unstable";
        }
        if (storyEvent.spotlightAgentIds().size() > 1) {
            return "ensemble-pressure";
        }
        return "focused";
    }

    private String resolvePacing(List<String> nextChoices, StoryEvent storyEvent) {
        if (nextChoices.size() >= 5) {
            return "branch-heavy";
        }
        if ("generated".equals(storyEvent.sourceType())) {
            return "improvised";
        }
        return "measured";
    }

    private String buildDirectorIntent(StoryEvent storyEvent, List<Agent> activeAgents) {
        String cast = activeAgents.stream().map(Agent::name).reduce((left, right) -> left + ", " + right).orElse("none");
        return "Keep the exchange centered on " + storyEvent.title()
                + ", preserve spotlight on " + storyEvent.speakerId()
                + ", and use active agents " + cast + " without flattening the user's decision space.";
    }

    private List<String> buildRisks(
            WorldState worldState,
            StoryEvent storyEvent,
            List<Agent> activeAgents,
            List<String> nextChoices) {
        List<String> risks = new ArrayList<>();
        if (activeAgents.size() > 3) {
            risks.add("Too many active agents may dilute ownership and voice separation.");
        }
        if (nextChoices.size() < 2) {
            risks.add("Choice pressure is low because the next-action fan-out is narrow.");
        }
        if ("generated".equals(storyEvent.sourceType())) {
            risks.add("Generated turn should be anchored back into a reusable task flow soon.");
        }
        if (worldState.getFlags().contains("generated-conversation-active")) {
            risks.add("The session is still carrying a generated conversation flag and may drift.");
        }
        if (risks.isEmpty()) {
            risks.add("No immediate structural risks detected in the current turn.");
        }
        return List.copyOf(risks);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
