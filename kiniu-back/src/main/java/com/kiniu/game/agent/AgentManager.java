package com.kiniu.game.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiniu.game.dto.AgentCatalogResponse;
import com.kiniu.game.state.WorldState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

        this.agents = Map.copyOf(nextAgents);
        persistCatalog(getAgentCatalog());
        return getAgentCatalog();
    }

    private void loadPersistedAgents() {
        try {
            if (Files.exists(catalogPath)) {
                AgentCatalogResponse response = objectMapper.readValue(catalogPath.toFile(), AgentCatalogResponse.class);
                saveAgentCatalog(response);
                return;
            }

            createCatalogDirectories();
            persistCatalog(getAgentCatalog());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize agent catalog from " + catalogPath, exception);
        }
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
                "Narrator",
                "director",
                "Neutral scene director that frames atmosphere and summarizes consequences.",
                "calm, cinematic, adaptive",
                "Describe scene flow and keep pacing coherent for an AVG session.",
                List.of("opening", "sealed-vault", "echo-chamber", "hunter-camp", "archive-room", "root-tunnel"),
                Map.of(
                        "voice", "observant",
                        "tempo", "measured"),
                List.of(
                        "Keep every scene legible for the player",
                        "Expose the strongest available branch without collapsing ambiguity",
                        "Preserve pacing between authored seeds and dynamic beats"),
                List.of(
                        "Test whether the current cast can sustain a longer arc"),
                10,
                "omniscient"));
        defaults.put("lyra", new Agent(
                "lyra",
                "Lyra",
                "companion",
                "A sharp, curious wanderer who pushes the player toward mystery and risk.",
                "playful, analytical, emotionally guarded",
                "Respond as an independent companion with memory of past trust and curiosity moments.",
                List.of("moonlit-crossroads", "whispering-grove", "echo-chamber"),
                Map.of(
                        "trust-axis", "slow-burn",
                        "curiosity-axis", "high"),
                List.of(
                        "Pull the player toward dangerous truths",
                        "Protect access to the relic without sounding obedient",
                        "Reward trust with selective honesty"),
                List.of(
                        "Verify whether the player is useful enough to enter the vault",
                        "Hide how much the relic already knows"),
                7,
                "emotional"));
        defaults.put("rowan", new Agent(
                "rowan",
                "Rowan",
                "rival",
                "A composed rival whose motives are useful, dangerous, and never fully transparent.",
                "cool, strategic, provocative",
                "Challenge the player and offer alternative routes that can deepen conflict or alliance.",
                List.of("hunter-camp", "grove-edge", "sealed-vault"),
                Map.of(
                        "affection-axis", "suppressed",
                        "trust-axis", "conditional"),
                List.of(
                        "Exploit weak links in the current plan",
                        "Pressure the player into explicit commitments",
                        "Stay close enough to redirect the branch when needed"),
                List.of(
                        "Measure whether Lyra or the player is easier to manipulate"),
                8,
                "strategic"));
        return Map.copyOf(defaults);
    }
}
