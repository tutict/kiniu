package com.kiniu.game.engine;

import com.kiniu.game.agent.Agent;
import com.kiniu.game.ai.AIService;
import com.kiniu.game.state.WorldState;
import com.kiniu.game.story.StoryEvent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class PlotAgentService {

    private final AtomicLong generatedBeatSequence = new AtomicLong();
    private final AIService aiService;

    public PlotAgentService(AIService aiService) {
        this.aiService = aiService;
    }

    public StoryEvent resolveStoryBeat(
            WorldState worldState,
            String playerInput,
            String playerChoice,
            Optional<StoryEvent> seededEvent,
            List<Agent> contextAgents) {
        if (seededEvent.isPresent()) {
            return seededEvent.get();
        }

        Agent spotlightAgent = selectSpotlightAgent(worldState, contextAgents);
        String beatType = determineBeatType(worldState, playerInput, playerChoice, spotlightAgent);
        String targetScene = determineScene(worldState, spotlightAgent);
        String sourceNodeId = worldState.getCurrentNodeId();
        String generatedNodeId = "generated." + sanitize(targetScene) + "." + generatedBeatSequence.incrementAndGet();

        worldState.setCurrentScene(targetScene);
        worldState.setCurrentNodeId(generatedNodeId);
        worldState.addFlag("generated-plot-active");
        worldState.addFlag("generated-beat:" + beatType);

        String fallbackTitle = titleFor(beatType, spotlightAgent);
        List<String> fallbackChoices = buildGeneratedChoices(beatType, spotlightAgent, worldState);
        PlotBeatDraft draft = aiService.generatePlotBeatDraft(
                beatType,
                targetScene,
                spotlightAgent,
                worldState,
                playerInput,
                playerChoice,
                fallbackChoices,
                new PlotBeatDraft(
                        fallbackTitle,
                        buildFallbackNarrative(
                                beatType,
                                targetScene,
                                spotlightAgent,
                                worldState,
                                playerInput,
                                playerChoice,
                                fallbackChoices),
                        fallbackChoices,
                        List.of(spotlightAgent.id())));
        List<String> choices = normalizeChoices(draft.choices(), fallbackChoices);
        List<String> spotlightAgentIds = normalizeSpotlightAgentIds(
                draft.spotlightAgentIds(),
                contextAgents,
                spotlightAgent.id());
        String title = safe(draft.title()).isBlank() ? fallbackTitle : draft.title();
        String narrative = safe(draft.narrative()).isBlank()
                ? buildFallbackNarrative(
                        beatType,
                        targetScene,
                        spotlightAgent,
                        worldState,
                        playerInput,
                        playerChoice,
                        choices)
                : draft.narrative();

        return StoryEvent.generated(
                "plot-" + generatedBeatSequence.get(),
                sourceNodeId,
                generatedNodeId,
                targetScene,
                spotlightAgent.id(),
                title,
                narrative,
                choices,
                spotlightAgentIds);
    }

    private String buildFallbackNarrative(
            String beatType,
            String sceneId,
            Agent spotlightAgent,
            WorldState worldState,
            String playerInput,
            String playerChoice,
            List<String> choices) {
        String playerMove = !safe(playerChoice).isBlank() ? playerChoice : safe(playerInput);
        return "Placeholder PlotAgent beat: type="
                + beatType
                + ", scene="
                + sceneId
                + ", spotlight="
                + spotlightAgent.name()
                + ". The player steers the scene with \""
                + (playerMove.isBlank() ? "silence" : playerMove)
                + "\" while the relationship vector remains trust="
                + worldState.getRelationship(spotlightAgent.id()).getTrust()
                + ", affection="
                + worldState.getRelationship(spotlightAgent.id()).getAffection()
                + ", curiosity="
                + worldState.getRelationship(spotlightAgent.id()).getCuriosity()
                + ". Suggested branch anchors: "
                + String.join(", ", choices)
                + ". Fallback local plot-planning path.";
    }

    private Agent selectSpotlightAgent(WorldState worldState, List<Agent> contextAgents) {
        return contextAgents.stream()
                .filter(agent -> !"narrator".equals(agent.id()))
                .max((left, right) -> Integer.compare(
                        worldState.getRelationship(left.id()).aggregate(),
                        worldState.getRelationship(right.id()).aggregate()))
                .orElseGet(() -> contextAgents.isEmpty()
                        ? new Agent(
                                "narrator",
                                "Narrator",
                                "director",
                                "Fallback narrator",
                                "calm",
                                "Keep the scene coherent.",
                                List.of(worldState.getCurrentScene()),
                                java.util.Map.of(),
                                List.of("Keep the scene coherent."),
                                List.of(),
                                10,
                                "omniscient")
                        : contextAgents.get(0));
    }

    private String determineBeatType(WorldState worldState, String playerInput, String playerChoice, Agent spotlightAgent) {
        String combined = (safe(playerInput) + " " + safe(playerChoice)).toLowerCase();
        int trust = worldState.getRelationship(spotlightAgent.id()).getTrust();
        int curiosity = worldState.getRelationship(spotlightAgent.id()).getCuriosity();

        if (containsAny(combined, "plan", "route", "ambush", "prepare", "strategy")) {
            return "tactical";
        }
        if (containsAny(combined, "doubt", "refuse", "lie", "leave", "against")) {
            return "conflict";
        }
        if (containsAny(combined, "who", "why", "ask", "truth", "memory") || curiosity >= 2) {
            return "discovery";
        }
        if (containsAny(combined, "trust", "help", "ally", "protect", "stay") || trust >= 2) {
            return "alliance";
        }
        return "pivot";
    }

    private String determineScene(WorldState worldState, Agent spotlightAgent) {
        if (spotlightAgent.activeScenes().contains(worldState.getCurrentScene())) {
            return worldState.getCurrentScene();
        }
        if (!spotlightAgent.activeScenes().isEmpty()) {
            return spotlightAgent.activeScenes().get(0);
        }
        return worldState.getCurrentScene();
    }

    private List<String> buildGeneratedChoices(String beatType, Agent spotlightAgent, WorldState worldState) {
        Set<String> choices = new LinkedHashSet<>();
        String agentName = spotlightAgent.name();

        switch (beatType) {
            case "tactical" -> {
                choices.add("Let " + agentName + " outline the next move");
                choices.add("Probe the safest route before committing");
                choices.add("Force the scene into a decisive confrontation");
            }
            case "conflict" -> {
                choices.add("Challenge " + agentName + " directly");
                choices.add("Pull back and observe before responding");
                choices.add("Offer a conditional alliance instead of full trust");
            }
            case "discovery" -> {
                choices.add("Ask " + agentName + " for the hidden context");
                choices.add("Inspect the newest clue in the scene");
                choices.add("Record the revelation for later branching");
            }
            case "alliance" -> {
                choices.add("Trust " + agentName + " with the initiative");
                choices.add("Share a personal risk to deepen the bond");
                choices.add("Coordinate a joint move before the window closes");
            }
            default -> {
                choices.add("Ask the cast to reframe the situation");
                choices.add("Shift the emotional tone of the scene");
                choices.add("Let the next branch emerge from observation");
            }
        }

        if (worldState.hasFlag("lantern-attuned")) {
            choices.add("Trace the lantern resonance into the next branch");
        }
        if (worldState.hasFlag("overheard-hunters")) {
            choices.add("Turn the hunters' intel into leverage");
        }

        return choices.stream().limit(4).toList();
    }

    private String titleFor(String beatType, Agent spotlightAgent) {
        return switch (beatType) {
            case "tactical" -> "Tactical Reframe";
            case "conflict" -> spotlightAgent.name() + " Pushes Back";
            case "discovery" -> "An Unstable Revelation";
            case "alliance" -> "Shared Momentum";
            default -> "Improvised Crosscurrent";
        };
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
        return normalized.stream().limit(4).toList();
    }

    private List<String> normalizeSpotlightAgentIds(
            List<String> generatedSpotlightAgentIds,
            List<Agent> contextAgents,
            String fallbackSpotlightAgentId) {
        Set<String> validAgentIds = contextAgents.stream().map(Agent::id).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> normalized = new LinkedHashSet<>();
        normalized.add(fallbackSpotlightAgentId);
        for (String agentId : generatedSpotlightAgentIds) {
            String sanitized = safe(agentId);
            if (!sanitized.isBlank() && validAgentIds.contains(sanitized)) {
                normalized.add(sanitized);
            }
        }
        return List.copyOf(normalized);
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String sanitize(String value) {
        return safe(value).replaceAll("[^a-zA-Z0-9]+", "-");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
