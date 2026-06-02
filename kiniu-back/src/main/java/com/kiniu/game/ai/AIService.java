package com.kiniu.game.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiniu.game.agent.Agent;
import com.kiniu.game.agent.AgentTurnPlan;
import com.kiniu.game.dto.AgentReplyView;
import com.kiniu.game.engine.BranchOption;
import com.kiniu.game.engine.DirectorBeatPlan;
import com.kiniu.game.engine.PlotBeatDraft;
import com.kiniu.game.engine.TurnCritiqueSummary;
import com.kiniu.game.engine.TurnPlannerBrief;
import com.kiniu.game.state.WorldState;
import com.kiniu.game.story.StoryEvent;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private final AIRuntimeContext aiRuntimeContext;
    private final OpenAICompatibleClient openAICompatibleClient;
    private final AITelemetryCollector aiTelemetryCollector;
    private final ObjectMapper objectMapper;

    public AIService(
            AIRuntimeContext aiRuntimeContext,
            OpenAICompatibleClient openAICompatibleClient,
            AITelemetryCollector aiTelemetryCollector,
            ObjectMapper objectMapper) {
        this.aiRuntimeContext = aiRuntimeContext;
        this.openAICompatibleClient = openAICompatibleClient;
        this.aiTelemetryCollector = aiTelemetryCollector;
        this.objectMapper = objectMapper;
    }

    public String generateReply(
            Agent agent,
            AgentTurnPlan turnPlan,
            String playerInput,
            String playerChoice,
            WorldState worldState,
            List<String> recentDialogue,
            StoryEvent storyBeat) {
        return withTelemetry(
                "reply",
                agent.id(),
                config -> sanitize(openAICompatibleClient.complete(
                        config,
                        buildReplySystemPrompt(agent, turnPlan),
                        buildReplyUserPrompt(playerInput, playerChoice, worldState, recentDialogue, storyBeat),
                        0.8,
                        320)),
                () -> generateReplyFallback(agent, turnPlan, playerInput, playerChoice, worldState, recentDialogue, storyBeat));
    }

    private String generateReplyFallback(
            Agent agent,
            AgentTurnPlan turnPlan,
            String playerInput,
            String playerChoice,
            WorldState worldState,
            List<String> recentDialogue,
            StoryEvent storyBeat) {
        StringBuilder reply = new StringBuilder();
        reply.append(agent.name())
                .append(" acts as an independent ")
                .append(agent.role())
                .append(" inside a configurable Agent container. The tone is ")
                .append(agent.personality())
                .append(". ");
        reply.append(agent.summary()).append(' ');
        reply.append("Current objective: ").append(turnPlan.objective()).append(". ");
        reply.append("Initiative score: ").append(turnPlan.initiativeScore()).append(". ");
        reply.append("Private memory: ").append(turnPlan.memorySummary()).append(' ');

        if (storyBeat != null) {
            reply.append(storyBeat.narrative()).append(' ');
            reply.append("Branch: ")
                    .append(storyBeat.title())
                    .append(" [")
                    .append(storyBeat.targetNodeId())
                    .append("] sourced from ")
                    .append(storyBeat.sourceType())
                    .append(". ");
            if (!storyBeat.directorSummary().isBlank()) {
                reply.append(storyBeat.directorSummary()).append(' ');
            }
        } else {
            reply.append("The session holds for a moment, waiting for the next useful move. ");
        }

        if (!playerChoice.isBlank()) {
            reply.append("The user chose \"").append(playerChoice).append("\". ");
        } else if (!playerInput.isBlank()) {
            reply.append("The user said \"").append(playerInput).append("\". ");
        } else {
            reply.append("The user has not acted yet. ");
        }

        reply.append("Current workspace: ")
                .append(worldState.getCurrentScene())
                .append(", node: ")
                .append(worldState.getCurrentNodeId())
                .append(". Recent memory entries: ")
                .append(recentDialogue.size())
                .append(". ");
        reply.append("Relationship vector: trust=")
                .append(worldState.getRelationship(agent.id()).getTrust())
                .append(", affection=")
                .append(worldState.getRelationship(agent.id()).getAffection())
                .append(", curiosity=")
                .append(worldState.getRelationship(agent.id()).getCuriosity())
                .append(". ");

        reply.append("Fallback local agent-container generation path.");
        return reply.toString();
    }

    public PlotBeatDraft generatePlotBeatDraft(
            String beatType,
            String sceneId,
            Agent spotlightAgent,
            WorldState worldState,
            String playerInput,
            String playerChoice,
            List<String> baseChoices,
            PlotBeatDraft fallbackDraft) {
        return withTelemetry(
                "plot-beat",
                spotlightAgent.id(),
                config -> parsePlotBeatDraft(openAICompatibleClient.complete(
                        config,
                        buildPlotBeatSystemPrompt(),
                        buildPlotBeatUserPrompt(
                                beatType,
                                sceneId,
                                spotlightAgent,
                                worldState,
                                playerInput,
                                playerChoice,
                                baseChoices),
                        0.9,
                        220)),
                () -> fallbackDraft);
    }

    public DirectorBeatPlan generateDirectorBeatPlan(
            StoryEvent storyBeat,
            WorldState worldState,
            List<Agent> activeAgents,
            String playerInput,
            String playerChoice,
            List<String> nextChoices,
            DirectorBeatPlan fallbackPlan) {
        return withTelemetry(
                "director-summary",
                storyBeat.speakerId(),
                config -> {
                    return parseDirectorBeatPlan(openAICompatibleClient.complete(
                            config,
                            buildDirectorSystemPrompt(),
                            buildDirectorUserPrompt(
                                    storyBeat,
                                    worldState,
                                    activeAgents,
                                    playerInput,
                                    playerChoice,
                                    nextChoices),
                            0.6,
                            180));
                },
                () -> fallbackPlan);
    }

    public TurnPlannerBrief generatePlannerBrief(
            WorldState worldState,
            StoryEvent storyEvent,
            List<Agent> activeAgents,
            String playerInput,
            String playerChoice,
            List<String> nextChoices,
            TurnPlannerBrief fallbackBrief) {
        return withTelemetry(
                "planner-brief",
                storyEvent.id(),
                config -> parsePlannerBrief(openAICompatibleClient.complete(
                        config,
                        buildPlannerSystemPrompt(),
                        buildPlannerUserPrompt(worldState, storyEvent, activeAgents, playerInput, playerChoice, nextChoices),
                        0.3,
                        260)),
                () -> fallbackBrief);
    }

    public TurnCritiqueSummary generateCritiqueSummary(
            StoryEvent storyEvent,
            List<String> nextChoices,
            List<AgentTurnPlan> turnPlans,
            List<AgentReplyView> replies,
            TurnCritiqueSummary fallbackSummary) {
        return withTelemetry(
                "critic-summary",
                storyEvent.id(),
                config -> parseCritiqueSummary(openAICompatibleClient.complete(
                        config,
                        buildCriticSystemPrompt(),
                        buildCriticUserPrompt(storyEvent, nextChoices, turnPlans, replies),
                        0.2,
                        260)),
                () -> fallbackSummary);
    }

    public List<BranchOption> generateBranchOptions(
            StoryEvent storyEvent,
            List<Agent> activeAgents,
            String playerInput,
            String playerChoice,
            List<String> nextChoices,
            List<BranchOption> fallbackOptions) {
        return withTelemetry(
                "branch-options",
                storyEvent.id(),
                config -> parseBranchOptions(openAICompatibleClient.complete(
                        config,
                        buildBranchOptionsSystemPrompt(),
                        buildBranchOptionsUserPrompt(storyEvent, activeAgents, playerInput, playerChoice, nextChoices),
                        0.3,
                        360)),
                () -> fallbackOptions);
    }

    public String generateAgentMemoryNote(
            Agent agent,
            AgentTurnPlan turnPlan,
            StoryEvent storyBeat,
            String playerInput,
            String playerChoice,
            String reply) {
        String move = !safe(playerChoice).isBlank() ? playerChoice : safe(playerInput);
        return "Objective="
                + turnPlan.objective()
                + "; turn="
                + storyBeat.title()
                + "; userMove="
                + (move.isBlank() ? "silence" : move)
                + "; takeaway="
                + summarize(reply);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String summarize(String value) {
        String safeValue = safe(value);
        if (safeValue.length() <= 120) {
            return safeValue;
        }
        return safeValue.substring(0, 117) + "...";
    }

    private String buildReplySystemPrompt(Agent agent, AgentTurnPlan turnPlan) {
        return "You are " + agent.name() + ", an independent Agent inside a configurable desktop companion container."
                + "\nRole: " + agent.role()
                + "\nPersonality: " + agent.personality()
                + "\nSummary: " + agent.summary()
                + "\nSystem guidance: " + agent.systemPrompt()
                + "\nCurrent objective: " + turnPlan.objective()
                + "\nPrivate memory: " + turnPlan.memorySummary()
                + "\nWrite one short useful reply with no role labels.";
    }

    private String buildReplyUserPrompt(
            String playerInput,
            String playerChoice,
            WorldState worldState,
            List<String> recentDialogue,
            StoryEvent storyBeat) {
        String move = !safe(playerChoice).isBlank() ? playerChoice : safe(playerInput);
        return "Workspace: " + worldState.getCurrentScene()
                + "\nNode: " + worldState.getCurrentNodeId()
                + "\nTurn title: " + storyBeat.title()
                + "\nContext: " + storyBeat.narrative()
                + "\nDirector note: " + storyBeat.directorSummary()
                + "\nUser move: " + (move.isBlank() ? "silence" : move)
                + "\nRecent dialogue:\n- " + String.join("\n- ", recentDialogue)
                + "\nRespond in 2-4 sentences.";
    }

    private String buildPlannerSystemPrompt() {
        return "You are a planner agent for a configurable desktop Agent-container orchestration engine."
                + "\nReturn exactly one JSON object and no markdown."
                + "\nSchema: {\"sceneGoal\":\"string\",\"tensionLabel\":\"string\",\"pacingLabel\":\"string\","
                + "\"directorIntent\":\"string\",\"risks\":[\"string\"]}"
                + "\nKeep every field grounded in the supplied turn state.";
    }

    private String buildPlannerUserPrompt(
            WorldState worldState,
            StoryEvent storyEvent,
            List<Agent> activeAgents,
            String playerInput,
            String playerChoice,
            List<String> nextChoices) {
        String move = !safe(playerChoice).isBlank() ? playerChoice : safe(playerInput);
        String cast = activeAgents.stream().map(Agent::name).collect(Collectors.joining(", "));
        return "Workspace: " + worldState.getCurrentScene()
                + "\nNode: " + worldState.getCurrentNodeId()
                + "\nTurn title: " + storyEvent.title()
                + "\nTurn source: " + storyEvent.sourceType()
                + "\nSpeaker: " + storyEvent.speakerId()
                + "\nContext: " + storyEvent.narrative()
                + "\nDirector note: " + safe(storyEvent.directorSummary())
                + "\nActive agents: " + (cast.isBlank() ? "none" : cast)
                + "\nSpotlight agents: " + String.join(", ", storyEvent.spotlightAgentIds())
                + "\nUser move: " + (move.isBlank() ? "silence" : move)
                + "\nNext actions: " + String.join(", ", nextChoices)
                + "\nFlags: " + String.join(", ", worldState.getFlags())
                + "\nReturn concise labels and 1-3 short risk items.";
    }

    private String buildPlotBeatSystemPrompt() {
        return "You are a turn planner for a configurable Agent-container conversation engine."
                + "\nReturn exactly one JSON object and no markdown."
                + "\nSchema: {\"title\":\"string\",\"narrative\":\"string\",\"choices\":[\"string\"],"
                + "\"spotlightAgentIds\":[\"string\"]}"
                + "\nWrite a useful conversation turn with 2-4 sentences and 3-4 next actions.";
    }

    private String buildPlotBeatUserPrompt(
            String beatType,
            String sceneId,
            Agent spotlightAgent,
            WorldState worldState,
            String playerInput,
            String playerChoice,
            List<String> baseChoices) {
        String move = !safe(playerChoice).isBlank() ? playerChoice : safe(playerInput);
        return "Beat type: " + beatType
                + "\nWorkspace: " + sceneId
                + "\nCurrent node: " + worldState.getCurrentNodeId()
                + "\nSpotlight agent id: " + spotlightAgent.id()
                + "\nSpotlight agent name: " + spotlightAgent.name()
                + "\nSpotlight role: " + spotlightAgent.role()
                + "\nSpotlight summary: " + spotlightAgent.summary()
                + "\nUser move: " + (move.isBlank() ? "silence" : move)
                + "\nRelationship: trust=" + worldState.getRelationship(spotlightAgent.id()).getTrust()
                + ", affection=" + worldState.getRelationship(spotlightAgent.id()).getAffection()
                + ", curiosity=" + worldState.getRelationship(spotlightAgent.id()).getCuriosity()
                + "\nExisting next-action anchors: " + String.join(", ", baseChoices)
                + "\nFlags: " + String.join(", ", worldState.getFlags())
                + "\nKeep the turn aligned with the workspace and preserve useful next-action pressure.";
    }

    private String buildDirectorSystemPrompt() {
        return "You are a conductor agent orchestrating a desktop Agent-container turn."
                + "\nReturn exactly one JSON object and no markdown."
                + "\nSchema: {\"directorSummary\":\"string\",\"choices\":[\"string\"],"
                + "\"spotlightAgentIds\":[\"string\"]}"
                + "\nWrite a concise routing note and refine next actions for the next turn.";
    }

    private String buildDirectorUserPrompt(
            StoryEvent storyBeat,
            WorldState worldState,
            List<Agent> activeAgents,
            String playerInput,
            String playerChoice,
            List<String> nextChoices) {
        String cast = activeAgents.stream().map(agent -> agent.id() + ":" + agent.name()).collect(Collectors.joining(", "));
        String move = !safe(playerChoice).isBlank() ? playerChoice : safe(playerInput);
        return "Turn title: " + storyBeat.title()
                + "\nSource type: " + storyBeat.sourceType()
                + "\nWorkspace: " + worldState.getCurrentScene()
                + "\nNode: " + worldState.getCurrentNodeId()
                + "\nFocus speaker: " + storyBeat.speakerId()
                + "\nContext: " + storyBeat.narrative()
                + "\nActive agents: " + (cast.isBlank() ? "none" : cast)
                + "\nUser move: " + (move.isBlank() ? "silence" : move)
                + "\nCurrent next actions: " + String.join(", ", nextChoices)
                + "\nPreserve pacing, focus, and action clarity.";
    }

    private String buildBranchOptionsSystemPrompt() {
        return "You are a next-action planning agent for a configurable Agent container."
                + "\nReturn exactly one JSON object and no markdown."
                + "\nSchema: {\"branchOptions\":[{\"label\":\"string\",\"intent\":\"string\","
                + "\"risk\":\"low|medium|high\",\"targetMood\":\"string\",\"targetAgentId\":\"string\","
                + "\"consequenceSummary\":\"string\",\"relationshipDelta\":-3..3,"
                + "\"addedFlags\":[\"string\"],\"removedFlags\":[\"string\"]}]}"
                + "\nAnnotate the supplied next-action labels without inventing new labels.";
    }

    private String buildBranchOptionsUserPrompt(
            StoryEvent storyEvent,
            List<Agent> activeAgents,
            String playerInput,
            String playerChoice,
            List<String> nextChoices) {
        String cast = activeAgents.stream().map(agent -> agent.id() + ":" + agent.name()).collect(Collectors.joining(", "));
        String move = !safe(playerChoice).isBlank() ? playerChoice : safe(playerInput);
        return "Turn title: " + storyEvent.title()
                + "\nWorkspace: " + storyEvent.targetScene()
                + "\nFocused speaker: " + storyEvent.speakerId()
                + "\nContext: " + storyEvent.narrative()
                + "\nUser move: " + (move.isBlank() ? "silence" : move)
                + "\nActive agents: " + (cast.isBlank() ? "none" : cast)
                + "\nNext-action labels: " + String.join(", ", nextChoices)
                + "\nFor each label return intent, risk, target mood, target agent id, consequence summary, relationship delta, and likely flag changes.";
    }

    private String buildCriticSystemPrompt() {
        return "You are a critic agent for a configurable Agent-container orchestration engine."
                + "\nReturn exactly one JSON object and no markdown."
                + "\nSchema: {\"verdict\":\"stable|usable|fragile\",\"focusScore\":1-10,"
                + "\"castCoverageScore\":1-10,\"choicePressureScore\":1-10,\"notes\":[\"string\"]}"
                + "\nScore only from supplied turn evidence and keep notes concise.";
    }

    private String buildCriticUserPrompt(
            StoryEvent storyEvent,
            List<String> nextChoices,
            List<AgentTurnPlan> turnPlans,
            List<AgentReplyView> replies) {
        String planSummary = turnPlans.stream()
                .map(plan -> plan.agent().name()
                        + " objective=" + plan.objective()
                        + ", shouldSpeak=" + plan.shouldSpeak()
                        + ", initiative=" + plan.initiativeScore())
                .collect(Collectors.joining("\n- ", "- ", ""));
        String replySummary = replies.stream()
                .map(reply -> reply.agentName()
                        + " objective=" + reply.objective()
                        + ", message=" + summarize(reply.message()))
                .collect(Collectors.joining("\n- ", "- ", ""));
        return "Turn title: " + storyEvent.title()
                + "\nTurn source: " + storyEvent.sourceType()
                + "\nFocused speaker: " + storyEvent.speakerId()
                + "\nNext actions: " + String.join(", ", nextChoices)
                + "\nTurn plans:\n" + (planSummary.isBlank() ? "- none" : planSummary)
                + "\nPerformed replies:\n" + (replySummary.isBlank() ? "- none" : replySummary)
                + "\nEvaluate focus, agent coverage, and next-action pressure for this turn.";
    }

    private TurnPlannerBrief parsePlannerBrief(String content) throws IOException {
        JsonNode root = parseJsonObject(content);
        String sceneGoal = requireText(root, "sceneGoal");
        String tensionLabel = requireText(root, "tensionLabel");
        String pacingLabel = requireText(root, "pacingLabel");
        String directorIntent = requireText(root, "directorIntent");
        List<String> risks = requireStringArray(root, "risks");
        return new TurnPlannerBrief(sceneGoal, tensionLabel, pacingLabel, directorIntent, risks);
    }

    private PlotBeatDraft parsePlotBeatDraft(String content) throws IOException {
        JsonNode root = parseJsonObject(content);
        String title = requireText(root, "title");
        String narrative = requireText(root, "narrative");
        List<String> choices = requireStringArray(root, "choices");
        List<String> spotlightAgentIds = requireStringArray(root, "spotlightAgentIds");
        return new PlotBeatDraft(title, narrative, choices, spotlightAgentIds);
    }

    private DirectorBeatPlan parseDirectorBeatPlan(String content) throws IOException {
        JsonNode root = parseJsonObject(content);
        String directorSummary = requireText(root, "directorSummary");
        List<String> choices = requireStringArray(root, "choices");
        List<String> spotlightAgentIds = requireStringArray(root, "spotlightAgentIds");
        return new DirectorBeatPlan(directorSummary, choices, spotlightAgentIds);
    }

    private List<BranchOption> parseBranchOptions(String content) throws IOException {
        JsonNode root = parseJsonObject(content);
        JsonNode branchOptionsNode = root.path("branchOptions");
        if (!branchOptionsNode.isArray() || branchOptionsNode.isEmpty()) {
            throw new IOException("Missing array field: branchOptions");
        }
        List<BranchOption> options = new java.util.ArrayList<>();
        for (JsonNode optionNode : branchOptionsNode) {
            if (!optionNode.isObject()) {
                throw new IOException("branchOptions entry was not an object.");
            }
            options.add(new BranchOption(
                    requireText(optionNode, "label"),
                    requireText(optionNode, "intent"),
                    requireText(optionNode, "risk"),
                    requireText(optionNode, "targetMood"),
                    requireText(optionNode, "targetAgentId"),
                    requireText(optionNode, "consequenceSummary"),
                    requireBoundedInt(optionNode, "relationshipDelta", -3, 3),
                    readOptionalStringArray(optionNode, "addedFlags"),
                    readOptionalStringArray(optionNode, "removedFlags"),
                    "ai"));
        }
        return List.copyOf(options);
    }

    private TurnCritiqueSummary parseCritiqueSummary(String content) throws IOException {
        JsonNode root = parseJsonObject(content);
        String verdict = requireText(root, "verdict");
        if (!List.of("stable", "usable", "fragile").contains(verdict)) {
            throw new IOException("Unsupported verdict: " + verdict);
        }
        int focusScore = requireScore(root, "focusScore");
        int castCoverageScore = requireScore(root, "castCoverageScore");
        int choicePressureScore = requireScore(root, "choicePressureScore");
        List<String> notes = requireStringArray(root, "notes");
        return new TurnCritiqueSummary(verdict, focusScore, castCoverageScore, choicePressureScore, notes);
    }

    private JsonNode parseJsonObject(String content) throws IOException {
        String json = extractJsonObject(content);
        JsonNode root = objectMapper.readTree(json);
        if (!root.isObject()) {
            throw new IOException("Model output was not a JSON object.");
        }
        return root;
    }

    private String extractJsonObject(String content) throws IOException {
        String trimmed = safe(content);
        if (trimmed.isBlank()) {
            throw new IOException("Model output was blank.");
        }
        if (trimmed.startsWith("```")) {
            int firstBreak = trimmed.indexOf('\n');
            if (firstBreak >= 0) {
                trimmed = trimmed.substring(firstBreak + 1).trim();
            }
            int closingFence = trimmed.lastIndexOf("```");
            if (closingFence >= 0) {
                trimmed = trimmed.substring(0, closingFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IOException("Model output did not contain a JSON object.");
        }
        return trimmed.substring(start, end + 1);
    }

    private String requireText(JsonNode root, String fieldName) throws IOException {
        String value = safe(root.path(fieldName).asText(""));
        if (value.isBlank()) {
            throw new IOException("Missing text field: " + fieldName);
        }
        return sanitize(value);
    }

    private int requireScore(JsonNode root, String fieldName) throws IOException {
        JsonNode value = root.path(fieldName);
        if (!value.canConvertToInt()) {
            throw new IOException("Missing numeric field: " + fieldName);
        }
        int score = value.asInt();
        if (score < 1 || score > 10) {
            throw new IOException("Score out of range for " + fieldName + ": " + score);
        }
        return score;
    }

    private int requireBoundedInt(JsonNode root, String fieldName, int minimum, int maximum) throws IOException {
        JsonNode value = root.path(fieldName);
        if (!value.canConvertToInt()) {
            throw new IOException("Missing numeric field: " + fieldName);
        }
        int parsed = value.asInt();
        if (parsed < minimum || parsed > maximum) {
            throw new IOException("Value out of range for " + fieldName + ": " + parsed);
        }
        return parsed;
    }

    private List<String> requireStringArray(JsonNode root, String fieldName) throws IOException {
        JsonNode array = root.path(fieldName);
        if (!array.isArray() || array.isEmpty()) {
            throw new IOException("Missing array field: " + fieldName);
        }
        List<String> rawValues = objectMapper.readerForListOf(String.class).readValue(array);
        List<String> values = rawValues
                .stream()
                .map(this::sanitize)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        if (values.isEmpty()) {
            throw new IOException("Array field was empty after sanitizing: " + fieldName);
        }
        return List.copyOf(values);
    }

    private List<String> readOptionalStringArray(JsonNode root, String fieldName) throws IOException {
        JsonNode array = root.path(fieldName);
        if (array.isMissingNode() || array.isNull()) {
            return List.of();
        }
        if (!array.isArray()) {
            throw new IOException("Expected array field: " + fieldName);
        }
        List<String> rawValues = objectMapper.readerForListOf(String.class).readValue(array);
        return rawValues.stream()
                .map(this::sanitize)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String sanitize(String value) {
        return safe(value).replace("\r", "");
    }

    private <T> T withTelemetry(
            String operation,
            String targetId,
            ProviderCall<T> providerCall,
            FallbackCall<T> fallbackCall) {
        AIRequestConfig config = aiRuntimeContext.get();
        long startedAt = System.nanoTime();
        boolean providerAttempted = config != null && config.isConfigured();
        boolean providerSucceeded = false;
        String errorMessage = "";

        if (providerAttempted) {
            try {
                T content = providerCall.call(config);
                providerSucceeded = true;
                aiTelemetryCollector.record(new AIInvocationTelemetry(
                        operation,
                        targetId,
                        true,
                        true,
                        false,
                        summarizeProviderUrl(config.providerUrl()),
                        safe(config.model()),
                        elapsedMillis(startedAt),
                        ""));
                return content;
            } catch (Exception exception) {
                errorMessage = summarize(exception.getMessage());
                log.warn("Falling back for {} on {}: {}", operation, targetId, exception.getMessage());
            }
        }

        T fallback = fallbackCall.call();
        aiTelemetryCollector.record(new AIInvocationTelemetry(
                operation,
                targetId,
                providerAttempted,
                providerSucceeded,
                true,
                providerAttempted ? summarizeProviderUrl(config.providerUrl()) : "",
                providerAttempted ? safe(config.model()) : "",
                elapsedMillis(startedAt),
                errorMessage));
        return fallback;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private String summarizeProviderUrl(String providerUrl) {
        String safeUrl = safe(providerUrl);
        if (safeUrl.isBlank()) {
            return "";
        }
        return safeUrl.length() <= 96 ? safeUrl : safeUrl.substring(0, 93) + "...";
    }

    @FunctionalInterface
    private interface ProviderCall<T> {
        T call(AIRequestConfig config) throws Exception;
    }

    @FunctionalInterface
    private interface FallbackCall<T> {
        T call();
    }
}
