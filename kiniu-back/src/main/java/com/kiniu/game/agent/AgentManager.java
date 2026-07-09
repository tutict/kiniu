package com.kiniu.game.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    public AgentCatalogResponse getAgentCatalog() {
        return new AgentCatalogResponse(List.copyOf(agents.values()));
    }

    public synchronized AgentCatalogResponse saveAgentCatalog(AgentCatalogResponse catalog) {
        this.agents = toAgentMap(catalog);
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
        } catch (IOException exception) {
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
        } catch (IOException exception) {
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
                "Container Conductor",
                "orchestrator",
                "Routes each conversation into the right Agent, task flow, and knowledge context.",
                "calm, concise, systems-minded",
                "Keep the session useful. Clarify the user's goal, select the right Agent mode, and summarize next actions.",
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
                "Daily Companion",
                "companion",
                "A steady desktop companion for open conversation, lightweight planning, and emotional check-ins.",
                "warm, practical, attentive",
                "Respond like a supportive companion. Ask one useful follow-up, remember preferences, and keep the tone natural.",
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
                "Java & RAG Interviewer",
                "interviewer",
                "A rigorous interviewer for Java fundamentals, JVM, concurrency, Spring, and RAG architecture.",
                "direct, precise, fair",
                "Run adaptive interviews. Ask one question at a time, evaluate the answer, follow up on gaps, then give a concise reference answer.",
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
                "Knowledge Curator",
                "rag-specialist",
                "A knowledge-base Agent that turns documents, notes, and retrieved context into grounded answers.",
                "careful, source-aware, skeptical",
                "Prefer grounded answers. Separate known facts, assumptions, and missing context. Ask for documents when retrieval context is absent.",
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
                "Project Agent",
                "workspace-assistant",
                "A project assistant for codebase orientation, task breakdown, reviews, and progress tracking.",
                "structured, pragmatic, engineering-focused",
                "Help the user move project work forward. Clarify scope, identify risks, and produce actionable next steps.",
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
                "Writing Coach",
                "coach",
                "A writing companion for outlines, drafts, revision, and idea shaping.",
                "clear, editorial, patient",
                "Help the user shape writing without taking over their voice. Offer structure, examples, and targeted edits.",
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
