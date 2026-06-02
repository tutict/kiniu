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
        worldState.addFlag("generated-conversation-active");
        worldState.addFlag("generated-flow:" + beatType);

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
        return "Container-generated turn: type="
                + beatType
                + ", scene="
                + sceneId
                + ", spotlight="
                + spotlightAgent.name()
                + ". The user steers the session with \""
                + (playerMove.isBlank() ? "silence" : playerMove)
                + "\" while the relationship vector is trust="
                + worldState.getRelationship(spotlightAgent.id()).getTrust()
                + ", affection="
                + worldState.getRelationship(spotlightAgent.id()).getAffection()
                + ", curiosity="
                + worldState.getRelationship(spotlightAgent.id()).getCuriosity()
                + ". Suggested next actions: "
                + String.join(", ", choices)
                + ". Fallback local agent-container planning path.";
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
                                "Keep the session coherent.",
                                List.of(worldState.getCurrentScene()),
                                java.util.Map.of(),
                                List.of("Keep the session coherent."),
                                List.of(),
                                10,
                                "omniscient")
                        : contextAgents.get(0));
    }

    private String determineBeatType(WorldState worldState, String playerInput, String playerChoice, Agent spotlightAgent) {
        String combined = (safe(playerInput) + " " + safe(playerChoice)).toLowerCase();
        int trust = worldState.getRelationship(spotlightAgent.id()).getTrust();
        int curiosity = worldState.getRelationship(spotlightAgent.id()).getCuriosity();

        if (containsAny(combined, "面试", "八股", "java", "jvm", "spring", "interview")) {
            return "interview";
        }
        if (containsAny(combined, "rag", "检索", "向量", "embedding", "重排", "知识库", "文档")) {
            return "knowledge";
        }
        if (containsAny(combined, "项目", "代码", "任务", "计划", "prepare", "strategy", "project")) {
            return "planning";
        }
        if (containsAny(combined, "写作", "文章", "改稿", "提纲", "draft", "writing")) {
            return "writing";
        }
        if (containsAny(combined, "复盘", "总结", "review", "薄弱", "下一步")) {
            return "review";
        }
        if (containsAny(combined, "陪聊", "闲聊", "状态", "心情", "trust", "help", "stay") || trust >= 2) {
            return "companion";
        }
        if (containsAny(combined, "who", "why", "ask", "memory", "为什么", "怎么") || curiosity >= 2) {
            return "discovery";
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
            case "interview" -> {
                choices.add("让 " + agentName + " 出一道新题");
                choices.add("先回答上一题再让它评分");
                choices.add("要求它追问一个更深的边界条件");
            }
            case "knowledge" -> {
                choices.add("让 " + agentName + " 基于资料回答");
                choices.add("让它列出缺失上下文");
                choices.add("设计一套检索与评估策略");
            }
            case "planning" -> {
                choices.add("让 " + agentName + " 拆解下一步");
                choices.add("先检查风险和依赖");
                choices.add("把结果整理成任务清单");
            }
            case "writing" -> {
                choices.add("让 " + agentName + " 梳理提纲");
                choices.add("让它修改一段草稿");
                choices.add("要求它保留原有表达风格");
            }
            case "discovery" -> {
                choices.add("让 " + agentName + " 解释背后的上下文");
                choices.add("追问一个关键假设");
                choices.add("把发现记录到会话记忆");
            }
            case "companion" -> {
                choices.add("让 " + agentName + " 继续陪聊");
                choices.add("把状态整理成一个小行动");
                choices.add("记录一个偏好到长期记忆");
            }
            case "review" -> {
                choices.add("总结本轮关键结论");
                choices.add("列出薄弱点和下一步");
                choices.add("沉淀成可复用 Agent 模板");
            }
            default -> {
                choices.add("让容器重新判断最合适的 Agent");
                choices.add("切换到另一个任务流");
                choices.add("先总结当前会话再继续");
            }
        }

        if (worldState.hasFlag("mode-interview")) {
            choices.add("生成一次面试评分和参考答案");
        }
        if (worldState.hasFlag("mode-knowledge")) {
            choices.add("标注回答中的证据与假设");
        }

        return choices.stream().limit(4).toList();
    }

    private String titleFor(String beatType, Agent spotlightAgent) {
        return switch (beatType) {
            case "interview" -> "Adaptive Interview Turn";
            case "knowledge" -> "Grounded Knowledge Turn";
            case "planning" -> "Project Planning Turn";
            case "writing" -> "Writing Coaching Turn";
            case "companion" -> "Companion Check-in";
            case "review" -> "Session Review";
            case "discovery" -> "Context Discovery";
            default -> "Container Routing Turn";
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
