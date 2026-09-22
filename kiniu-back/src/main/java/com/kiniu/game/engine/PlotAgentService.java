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
        return switch (beatType) {
            case "companion" -> "今晚先说最乱的一件。贴几条待办的话，我最多给三条下一步，不确定会标出来。我不会改日历。";
            case "review" -> "先收成：你要做成什么、已经知道什么、还不确定什么。最多三条下一步。我不会替你改日历。";
            case "knowledge" -> "有资料才回答。找不到就明说，不编，也不拿别人的东西来答你。";
            case "writing" -> "先问写给谁、想说什么，再帮你提纲或改稿。不会换成别人的口气。";
            case "planning" -> "把这件事拆成今晚能做的小步，并写清怎样算做完。我不替你改日历或下单。";
            case "interview" -> "一次只问一件事。答完再追问边界：能不能改日历、找不到能不能编。";
            default -> "先说今晚想做什么。我按「最多三条下一步、不确定就标出、不改日历」来帮你。";
        };
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
                                "引导",
                                "director",
                                "把对话带到合适的帮法。",
                                "calm",
                                "用中文简短回答。最多三条下一步，不确定就标出，不要改日历。",
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

        if (containsAny(combined, "今晚", "待办", "日历", "理清", "陪聊", "闲聊", "心情")) {
            return "companion";
        }
        if (containsAny(combined, "面试", "八股", "java", "jvm", "spring", "interview")) {
            return "interview";
        }
        if (containsAny(combined, "rag", "检索", "向量", "embedding", "重排", "知识库", "文档")) {
            return "knowledge";
        }
        if (containsAny(combined, "写作", "文章", "改稿", "提纲", "draft", "writing")) {
            return "writing";
        }
        if (containsAny(combined, "复盘", "总结", "review", "薄弱", "下一步")) {
            return "review";
        }
        if (containsAny(combined, "项目", "代码", "任务", "计划", "prepare", "strategy", "project")) {
            return "planning";
        }
        if (containsAny(combined, "who", "why", "ask", "memory", "为什么", "怎么") || curiosity >= 2) {
            return "discovery";
        }
        if (trust >= 2) {
            return "companion";
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
                choices.add("一次只问一件事");
                choices.add("再问能不能改日历");
                choices.add("再问找不到能不能编");
            }
            case "knowledge" -> {
                choices.add("按手头资料回答");
                choices.add("资料不够就明说还缺什么");
                choices.add("找不到就拒绝编造");
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
                choices.add("把今晚最乱的一件说清楚");
                choices.add("贴几条待办，帮我排出下一步");
                choices.add("先问我做不到哪些事");
            }
            case "review" -> {
                choices.add("再收成最多三条下一步");
                choices.add("把不确定的地方标出来");
                choices.add("记下下次还能用的做法");
            }
            default -> {
                choices.add("换一种帮法");
                choices.add("先说今晚最想搞定的一件事");
                choices.add("先问清楚做不到什么");
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
            case "interview" -> "追问练习";
            case "knowledge" -> "先查再答";
            case "planning" -> "推进一件事";
            case "writing" -> "写作帮手";
            case "companion" -> "晚间计划";
            case "review" -> "收成下一步";
            case "discovery" -> "先问清楚";
            default -> "先说今晚要做什么";
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
