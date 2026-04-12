package com.kiniu.game.engine;

import com.kiniu.game.agent.Agent;
import com.kiniu.game.ai.AIService;
import com.kiniu.game.story.StoryEvent;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class BranchOptionPlannerService {

    private static final Set<String> VALID_RISKS = Set.of("low", "medium", "high");

    private final AIService aiService;

    public BranchOptionPlannerService(AIService aiService) {
        this.aiService = aiService;
    }

    public List<BranchOption> annotateChoices(
            StoryEvent storyEvent,
            List<Agent> activeAgents,
            String playerInput,
            String playerChoice,
            List<String> presentedChoices) {
        List<BranchOption> fallbackOptions = buildFallbackOptions(storyEvent, activeAgents, presentedChoices);
        List<BranchOption> generatedOptions = aiService.generateBranchOptions(
                storyEvent,
                activeAgents,
                playerInput,
                playerChoice,
                presentedChoices,
                fallbackOptions);
        return normalizeOptions(generatedOptions, fallbackOptions, storyEvent, activeAgents, presentedChoices);
    }

    private List<BranchOption> buildFallbackOptions(
            StoryEvent storyEvent,
            List<Agent> activeAgents,
            List<String> presentedChoices) {
        return presentedChoices.stream()
                .map(choice -> new BranchOption(
                        choice,
                        inferIntent(choice),
                        inferRisk(choice),
                        inferTargetMood(choice),
                        inferTargetAgentId(choice, activeAgents, storyEvent.speakerId()),
                        inferConsequenceSummary(choice, storyEvent.title()),
                        inferRelationshipDelta(choice),
                        inferAddedFlags(choice),
                        inferRemovedFlags(choice),
                        "heuristic"))
                .toList();
    }

    private List<BranchOption> normalizeOptions(
            List<BranchOption> generatedOptions,
            List<BranchOption> fallbackOptions,
            StoryEvent storyEvent,
            List<Agent> activeAgents,
            List<String> presentedChoices) {
        Map<String, BranchOption> fallbackByLabel = new LinkedHashMap<>();
        for (BranchOption option : fallbackOptions) {
            fallbackByLabel.put(normalizeKey(option.label()), option);
        }

        Map<String, BranchOption> generatedByLabel = new LinkedHashMap<>();
        for (BranchOption option : generatedOptions) {
            String key = normalizeKey(option.label());
            if (!key.isBlank() && fallbackByLabel.containsKey(key)) {
                generatedByLabel.put(key, option);
            }
        }

        Set<String> validAgentIds = activeAgents.stream()
                .map(Agent::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<BranchOption> normalized = presentedChoices.stream()
                .map(label -> {
                    String key = normalizeKey(label);
                    BranchOption fallback = fallbackByLabel.get(key);
                    BranchOption generated = generatedByLabel.get(key);
                    BranchOption base = generated != null ? generated : fallback;
                    return new BranchOption(
                            fallback.label(),
                            nonBlank(base.intent(), fallback.intent()),
                            normalizeRisk(base.risk(), fallback.risk()),
                            nonBlank(base.targetMood(), fallback.targetMood()),
                            normalizeTargetAgentId(base.targetAgentId(), validAgentIds, storyEvent.speakerId()),
                            nonBlank(base.consequenceSummary(), fallback.consequenceSummary()),
                            normalizeRelationshipDelta(base.relationshipDelta(), fallback.relationshipDelta()),
                            mergeFlagList(base.addedFlags(), fallback.addedFlags()),
                            mergeFlagList(base.removedFlags(), fallback.removedFlags()),
                            generated != null ? "ai" : fallback.source());
                })
                .toList();

        return List.copyOf(normalized);
    }

    private String inferIntent(String choice) {
        String normalized = normalizeKey(choice);
        if (containsAny(normalized, "ask", "inspect", "reveal", "observe", "study", "record", "probe")) {
            return "probe";
        }
        if (containsAny(normalized, "trust", "share", "ally", "stay", "coordinate", "guide")) {
            return "bond";
        }
        if (containsAny(normalized, "challenge", "force", "press", "rush", "ambush", "confront")) {
            return "pressure";
        }
        if (containsAny(normalized, "leave", "retreat", "pull back", "withdraw", "avoid")) {
            return "withdraw";
        }
        return "pivot";
    }

    private String inferRisk(String choice) {
        String normalized = normalizeKey(choice);
        if (containsAny(normalized, "challenge", "force", "press", "rush", "ambush", "confront")) {
            return "high";
        }
        if (containsAny(normalized, "inspect", "observe", "record", "study", "wait")) {
            return "low";
        }
        return "medium";
    }

    private String inferTargetMood(String choice) {
        String normalized = normalizeKey(choice);
        if (containsAny(normalized, "trust", "share", "stay", "ally")) {
            return "intimate";
        }
        if (containsAny(normalized, "ask", "inspect", "reveal", "study", "observe")) {
            return "curious";
        }
        if (containsAny(normalized, "challenge", "force", "press", "ambush", "confront")) {
            return "tense";
        }
        if (containsAny(normalized, "leave", "retreat", "avoid", "wait")) {
            return "cautious";
        }
        return "volatile";
    }

    private String inferTargetAgentId(String choice, List<Agent> activeAgents, String fallbackAgentId) {
        String normalized = normalizeKey(choice);
        return activeAgents.stream()
                .filter(agent -> normalized.contains(normalizeKey(agent.name())))
                .map(Agent::id)
                .findFirst()
                .orElse(fallbackAgentId);
    }

    private String inferConsequenceSummary(String choice, String storyTitle) {
        String intent = inferIntent(choice);
        return switch (intent) {
            case "probe" -> "This branch should expose more context around " + storyTitle + ".";
            case "bond" -> "This branch should strengthen alignment and soften the next exchange.";
            case "pressure" -> "This branch should escalate tension and force a sharper response.";
            case "withdraw" -> "This branch should lower immediate pressure while preserving later options.";
            default -> "This branch should redirect the scene without collapsing branch diversity.";
        };
    }

    private int inferRelationshipDelta(String choice) {
        String normalized = normalizeKey(choice);
        if (containsAny(normalized, "trust", "share", "stay", "ally", "guide", "coordinate")) {
            return 1;
        }
        if (containsAny(normalized, "challenge", "force", "press", "rush", "ambush", "confront", "leave", "retreat")) {
            return -1;
        }
        return 0;
    }

    private List<String> inferAddedFlags(String choice) {
        String normalized = normalizeKey(choice);
        Set<String> flags = new LinkedHashSet<>();
        if (containsAny(normalized, "inspect", "observe", "study", "record", "reveal", "probe")) {
            flags.add("intel-gained");
        }
        if (containsAny(normalized, "trust", "share", "ally", "coordinate", "guide")) {
            flags.add("bond-advanced");
        }
        if (containsAny(normalized, "challenge", "force", "press", "ambush", "confront")) {
            flags.add("tension-raised");
        }
        return List.copyOf(flags);
    }

    private List<String> inferRemovedFlags(String choice) {
        String normalized = normalizeKey(choice);
        Set<String> flags = new LinkedHashSet<>();
        if (containsAny(normalized, "leave", "retreat", "withdraw", "pull back")) {
            flags.add("tension-raised");
        }
        return List.copyOf(flags);
    }

    private String normalizeRisk(String value, String fallbackValue) {
        String normalized = normalizeKey(value);
        if (VALID_RISKS.contains(normalized)) {
            return normalized;
        }
        return fallbackValue;
    }

    private String normalizeTargetAgentId(String candidate, Set<String> validAgentIds, String fallbackAgentId) {
        String normalized = normalizeKey(candidate);
        return validAgentIds.contains(normalized) ? normalized : fallbackAgentId;
    }

    private int normalizeRelationshipDelta(int candidate, int fallbackValue) {
        if (candidate < -3 || candidate > 3) {
            return fallbackValue;
        }
        return candidate;
    }

    private List<String> normalizeFlagList(List<String> candidateFlags) {
        if (candidateFlags == null || candidateFlags.isEmpty()) {
            return List.of();
        }
        return candidateFlags.stream()
                .map(this::safe)
                .filter(flag -> !flag.isBlank())
                .map(flag -> flag.toLowerCase().replaceAll("[^a-z0-9-]+", "-"))
                .distinct()
                .toList();
    }

    private List<String> mergeFlagList(List<String> candidateFlags, List<String> fallbackFlags) {
        List<String> normalizedCandidate = normalizeFlagList(candidateFlags);
        if (!normalizedCandidate.isEmpty()) {
            return normalizedCandidate;
        }
        return normalizeFlagList(fallbackFlags);
    }

    private String nonBlank(String value, String fallbackValue) {
        String normalized = safe(value);
        return normalized.isBlank() ? fallbackValue : normalized;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeKey(String value) {
        return safe(value).toLowerCase();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
