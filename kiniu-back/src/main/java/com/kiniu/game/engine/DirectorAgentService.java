package com.kiniu.game.engine;

import com.kiniu.game.agent.Agent;
import com.kiniu.game.ai.AIService;
import com.kiniu.game.state.WorldState;
import com.kiniu.game.story.StoryEvent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DirectorAgentService {

    private final AIService aiService;

    public DirectorAgentService(AIService aiService) {
        this.aiService = aiService;
    }

    public StoryEvent directStoryBeat(
            WorldState worldState,
            StoryEvent storyBeat,
            List<Agent> activeAgents,
            String playerInput,
            String playerChoice,
            List<String> nextChoices) {
        List<String> fallbackSpotlightAgentIds = resolveSpotlightAgents(storyBeat, activeAgents);
        DirectorBeatPlan plan = aiService.generateDirectorBeatPlan(
                storyBeat,
                worldState,
                activeAgents,
                playerInput,
                playerChoice,
                nextChoices,
                new DirectorBeatPlan(
                        buildFallbackDirectorSummary(
                                storyBeat,
                                worldState,
                                activeAgents,
                                playerInput,
                                playerChoice,
                                nextChoices),
                        nextChoices,
                        fallbackSpotlightAgentIds));
        List<String> normalizedChoices = normalizeChoices(plan.choices(), nextChoices);
        List<String> spotlightAgentIds = normalizeSpotlightAgents(plan.spotlightAgentIds(), activeAgents, storyBeat.speakerId());
        String directorSummary = safe(plan.directorSummary()).isBlank()
                ? buildFallbackDirectorSummary(storyBeat, worldState, activeAgents, playerInput, playerChoice, normalizedChoices)
                : plan.directorSummary();
        return storyBeat.withDirection(directorSummary, normalizedChoices, spotlightAgentIds);
    }

    private List<String> resolveSpotlightAgents(StoryEvent storyBeat, List<Agent> activeAgents) {
        Set<String> spotlight = new LinkedHashSet<>();
        spotlight.add(storyBeat.speakerId());
        activeAgents.stream()
                .filter(agent -> !"narrator".equals(agent.id()))
                .limit(2)
                .map(Agent::id)
                .forEach(spotlight::add);
        return List.copyOf(spotlight);
    }

    private String buildFallbackDirectorSummary(
            StoryEvent storyBeat,
            WorldState worldState,
            List<Agent> activeAgents,
            String playerInput,
            String playerChoice,
            List<String> nextChoices) {
        String cast = activeAgents.stream().map(Agent::name).reduce((left, right) -> left + ", " + right).orElse("none");
        String move = !safe(playerChoice).isBlank() ? playerChoice : safe(playerInput);
        return "Director framing: source="
                + storyBeat.sourceType()
                + ", scene="
                + worldState.getCurrentScene()
                + ", active cast="
                + (cast.isBlank() ? "none" : cast)
                + ". Keep the next exchange centered on "
                + storyBeat.title()
                + " after the player move \""
                + (move.isBlank() ? "silence" : move)
                + "\". Offer branches: "
                + String.join(", ", nextChoices)
                + ". Fallback local directing path.";
    }

    private List<String> normalizeChoices(List<String> generatedChoices, List<String> fallbackChoices) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String choice : generatedChoices) {
            String sanitized = safe(choice);
            if (!sanitized.isBlank()) {
                normalized.add(sanitized);
            }
        }
        if (normalized.isEmpty()) {
            normalized.addAll(fallbackChoices);
        }
        return normalized.stream().limit(7).toList();
    }

    private List<String> normalizeSpotlightAgents(
            List<String> generatedSpotlightAgentIds,
            List<Agent> activeAgents,
            String speakerId) {
        Set<String> validAgentIds = activeAgents.stream()
                .map(Agent::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> normalized = new LinkedHashSet<>();
        normalized.add(speakerId);
        for (String agentId : generatedSpotlightAgentIds) {
            String sanitized = safe(agentId);
            if (!sanitized.isBlank() && validAgentIds.contains(sanitized)) {
                normalized.add(sanitized);
            }
        }
        return List.copyOf(normalized);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
