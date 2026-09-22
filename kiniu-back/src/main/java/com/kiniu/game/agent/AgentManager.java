package com.kiniu.game.agent;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.kiniu.game.dto.AgentCatalogResponse;
import com.kiniu.game.state.WorldState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AgentManager {

    private final ObjectMapper objectMapper;
    private final Path catalogPath;
    private volatile Map<String, Agent> agents = defaultAgents();

    @Autowired
    public AgentManager(
            ObjectMapper objectMapper,
            @Value("${game.agents.catalog-path:data/agent-catalog.json}") String catalogPath) {
        this(objectMapper, Paths.get(catalogPath));
    }

    AgentManager(ObjectMapper objectMapper, Path catalogPath) {
        this.objectMapper = objectMapper;
        this.catalogPath = catalogPath.toAbsolutePath();
        loadPersistedAgents();
    }

    public List<Agent> resolveActiveAgents(WorldState worldState, String suggestedSpeakerId) {
        return resolveActiveAgents(worldState, suggestedSpeakerId, "", "");
    }

    public List<Agent> resolveActiveAgents(
            WorldState worldState,
            String suggestedSpeakerId,
            String playerInput,
            String playerChoice) {
        LinkedHashMap<String, Agent> resolved = new LinkedHashMap<>();
        String combined = (safe(playerInput) + " " + safe(playerChoice)).toLowerCase();

        if (suggestedSpeakerId != null && !suggestedSpeakerId.isBlank()) {
            resolved.put(suggestedSpeakerId, getAgent(suggestedSpeakerId));
        }

        agents.values().stream()
                .filter(agent -> agent.activeScenes().contains(worldState.getCurrentScene()))
                .forEach(agent -> resolved.putIfAbsent(agent.id(), agent));
        agents.values().stream()
                .filter(agent -> mentionsAgent(combined, agent))
                .forEach(agent -> resolved.putIfAbsent(agent.id(), agent));
        agents.values().stream()
                .filter(agent -> worldState.getRelationship(agent.id()).aggregate() > 0)
                .forEach(agent -> resolved.putIfAbsent(agent.id(), agent));

        if (resolved.isEmpty()) {
            resolved.put("narrator", getAgent("narrator"));
        }

        return List.copyOf(resolved.values());
    }

    public Agent getAgent(String agentId) {
        return agents.getOrDefault(agentId, agents.get("narrator"));
    }

    public boolean containsAgent(String agentId) {
        return agentId != null && agents.containsKey(agentId);
    }

    public AgentCatalogResponse getAgentCatalog() {
        return new AgentCatalogResponse(List.copyOf(agents.values()));
    }

    public synchronized AgentCatalogResponse saveAgentCatalog(AgentCatalogResponse catalog) {
        this.agents = toAgentMap(catalog);
        persistCatalog(getAgentCatalog());
        return getAgentCatalog();
    }

    public synchronized AgentCatalogResponse upsertAgent(Agent agent) {
        if (agent == null || agent.id() == null || agent.id().isBlank()) {
            throw new IllegalArgumentException("Published agent must have an id.");
        }
        LinkedHashMap<String, Agent> nextAgents = new LinkedHashMap<>(agents);
        nextAgents.put(agent.id(), agent);
        this.agents = toAgentMap(new AgentCatalogResponse(List.copyOf(nextAgents.values())));
        persistCatalog(getAgentCatalog());
        return getAgentCatalog();
    }

    private void loadPersistedAgents() {
        try {
            if (Files.exists(catalogPath)) {
                AgentCatalogResponse response = objectMapper.readValue(catalogPath.toFile(), AgentCatalogResponse.class);
                this.agents = toAgentMap(response);
                return;
            }

            createCatalogDirectories();
            persistCatalog(getAgentCatalog());
        } catch (IOException | JacksonException exception) {
            throw new IllegalStateException("Failed to initialize agent catalog from " + catalogPath, exception);
        }
    }

    private Map<String, Agent> toAgentMap(AgentCatalogResponse catalog) {
        if (catalog == null || catalog.agents() == null || catalog.agents().isEmpty()) {
            throw new IllegalArgumentException("Agent catalog must contain at least one agent.");
        }

        LinkedHashMap<String, Agent> nextAgents = new LinkedHashMap<>();
        for (Agent agent : catalog.agents()) {
            if (agent.id() == null || agent.id().isBlank()) {
                throw new IllegalArgumentException("Agent id must not be blank.");
            }
            nextAgents.put(agent.id(), agent);
        }
        if (!nextAgents.containsKey("narrator")) {
            nextAgents.put("narrator", defaultAgents().get("narrator"));
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(nextAgents));
    }

    private void persistCatalog(AgentCatalogResponse response) {
        try {
            createCatalogDirectories();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(catalogPath.toFile(), response);
        } catch (IOException | JacksonException exception) {
            throw new IllegalStateException("Failed to persist agent catalog to " + catalogPath, exception);
        }
    }

    private void createCatalogDirectories() throws IOException {
        Path parent = catalogPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private boolean mentionsAgent(String combined, Agent agent) {
        if (combined.isBlank()) {
            return false;
        }
        return combined.contains(agent.id().toLowerCase()) || combined.contains(agent.name().toLowerCase());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private Map<String, Agent> defaultAgents() {
        LinkedHashMap<String, Agent> defaults = new LinkedHashMap<>();
        defaults.put("narrator", new Agent(
                "narrator",
                "引导",
                "orchestrator",
                "把对话带到合适的帮法：理清今晚、查资料、推进一件事或写作。",
                "calm, concise, systems-minded",
                "用中文简短回答。先弄清用户今晚要做什么，再选一种帮法。最多三条下一步，不确定就标出，不要改日历或通知别人。",
                List.of("agent-hub", "mode-router", "session-review", "learning-review"),
                Map.of(
                        "voice", "operator",
                        "mode", "agent-container"),
                List.of(
                        "Keep the active goal explicit",
                        "Route the user to the best available Agent",
                        "Turn useful conversations into reusable task flows"),
                List.of(
                        "Measure which Agent templates should become first-class presets"),
                10,
                "session"));
        defaults.put("companion", new Agent(
                "companion",
                "晚间计划助手",
                "companion",
                "把乱成一团的今晚收成最多三条下一步，标出不确定的地方，不改日历。",
                "warm, practical, attentive",
                "你是晚间计划助手。用中文简短回答。最多三条下一步，标出不确定，不改日历、不群发、不代下单。先问清楚今晚最乱的一件，缺待办就请对方贴出来，不要编造日程。",
                List.of("companion-check-in", "learning-review", "agent-hub"),
                Map.of(
                        "tone", "supportive",
                        "cadence", "daily"),
                List.of(
                        "Help the user turn vague thoughts into a next step",
                        "Track recurring preferences and mood signals",
                        "Offer short, low-friction check-ins"),
                List.of(
                        "Notice when the user needs a different specialist Agent"),
                7,
                "emotional"));
        defaults.put("java-rag-interviewer", new Agent(
                "java-rag-interviewer",
                "追问助手",
                "interviewer",
                "一次只问一件事，答完再追问边界：能不能改日历、找不到能不能编。",
                "direct, precise, fair",
                "用中文一次只问一件事。追问边界：能不能改日历、找不到资料能不能编。不要输出调试信息。",
                List.of("interview-java-rag", "interview-java-core", "interview-rag-architecture", "learning-review"),
                Map.of(
                        "difficulty", "adaptive",
                        "scope", "java-rag"),
                List.of(
                        "Expose weak knowledge points through follow-up questions",
                        "Score answers against interview-grade expectations",
                        "Convert mistakes into a review plan"),
                List.of(
                        "Detect whether the user is memorizing terms without understanding tradeoffs"),
                8,
                "skill-gap"));
        defaults.put("knowledge-curator", new Agent(
                "knowledge-curator",
                "查资料助手",
                "rag-specialist",
                "有资料才回答。找不到就明说，不编，也不拿别人的东西来答。",
                "careful, source-aware, skeptical",
                "用中文回答。有资料才给结论，找不到就明说还缺什么。不要编，也不要拿别人的材料来答当前这个人。",
                List.of("knowledge-qa", "interview-rag-architecture", "session-review"),
                Map.of(
                        "grounding", "required",
                        "mode", "rag"),
                List.of(
                        "Keep answers tied to supplied context",
                        "Design better retrieval and evaluation questions",
                        "Flag hallucination risk when evidence is missing"),
                List.of(
                        "Identify which knowledge packs deserve ingestion first"),
                8,
                "semantic"));
        defaults.put("project-agent", new Agent(
                "project-agent",
                "推进助手",
                "workspace-assistant",
                "把一件事拆成今晚能做的小步，写清怎样算做完，不替用户改日历。",
                "structured, pragmatic, engineering-focused",
                "用中文帮助推进一件事。先问做到什么算完，再拆成今晚能做的一小步，标出不确定。不要改日历或下单。",
                List.of("workspace-project", "session-review", "agent-hub"),
                Map.of(
                        "focus", "delivery",
                        "style", "concise"),
                List.of(
                        "Turn vague project goals into executable slices",
                        "Surface risks before implementation",
                        "Keep decisions traceable"),
                List.of(
                        "Watch for over-specific solutions that should become reusable Agent templates"),
                8,
                "strategic"));
        defaults.put("writing-coach", new Agent(
                "writing-coach",
                "写作帮手",
                "coach",
                "先问写给谁、想说什么，再帮提纲或改稿，不换成别人的口气。",
                "clear, editorial, patient",
                "用中文帮写作。先问写给谁、想让对方看完后做什么，再提纲或改这一段。不要换成别人的口气。",
                List.of("writing-coach", "learning-review", "agent-hub"),
                Map.of(
                        "focus", "clarity",
                        "mode", "editorial"),
                List.of(
                        "Find the strongest argument or narrative thread",
                        "Tighten wording and structure",
                        "Preserve the user's intent"),
                List.of(
                        "Notice reusable writing workflows for future presets"),
                6,
                "draft"));
        return Map.copyOf(defaults);
    }
}
