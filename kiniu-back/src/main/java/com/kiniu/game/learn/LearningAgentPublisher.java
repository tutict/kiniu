package com.kiniu.game.learn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiniu.game.agent.Agent;
import com.kiniu.game.agent.AgentManager;
import com.kiniu.game.dto.AgentCatalogResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LearningAgentPublisher {

    private static final int MAX_POLICY_CHARS = 4_000;
    private static final int MAX_ARRAY_ITEMS = 20;
    private static final int MAX_ARRAY_ITEM_CHARS = 500;

    private final ObjectMapper objectMapper;
    private final AgentManager agentManager;

    public LearningAgentPublisher(ObjectMapper objectMapper, AgentManager agentManager) {
        this.objectMapper = objectMapper;
        this.agentManager = agentManager;
    }

    public PublishedLearningAgent publish(LearningTaskDefinition task, LearningAttempt attempt) {
        if (!"agent-project".equals(task.kind()) || !attempt.passed()) {
            throw new IllegalArgumentException("Only a passed Agent project attempt can be published.");
        }
        String content = attempt.files().get("agent.json");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("The passed attempt does not contain agent.json.");
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            String name = requiredText(root, "name");
            if (name.length() > 120) {
                throw new IllegalArgumentException("name is too long.");
            }
            List<String> goals = requiredTextArray(root, "coreGoals", 2);
            List<String> boundaries = requiredTextArray(root, "boundaries", 2);
            String memoryPolicy = requiredText(root, "memoryPolicy");
            String failurePolicy = requiredText(root, "failurePolicy");
            String id = normalizeAgentId(root.path("id").asText("student-companion"));

            Agent agent = new Agent(
                    id,
                    name,
                    "companion",
                    "A learner-built companion Agent with explicit goals, memory policy, boundaries, and failure handling.",
                    root.path("personality").asText("warm, practical, bounded"),
                    buildSystemPrompt(goals, boundaries, memoryPolicy, failurePolicy),
                    List.of("agent-hub", "companion-check-in", "learning-review"),
                    Map.of(
                            "source", "kiniu-learn",
                            "memoryPolicy", memoryPolicy,
                            "failurePolicy", failurePolicy),
                    goals,
                    boundaries.stream().map(boundary -> "Never violate: " + boundary).toList(),
                    7,
                    "policy-driven");
            AgentCatalogResponse catalog = agentManager.upsertAgent(agent);
            return new PublishedLearningAgent(agent, catalog);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("agent.json cannot be published: " + exception.getMessage(), exception);
        }
    }

    private String buildSystemPrompt(
            List<String> goals,
            List<String> boundaries,
            String memoryPolicy,
            String failurePolicy) {
        return "Pursue these goals:\n- " + String.join("\n- ", goals)
                + "\nRespect these boundaries:\n- " + String.join("\n- ", boundaries)
                + "\nMemory policy: " + memoryPolicy
                + "\nFailure policy: " + failurePolicy
                + "\nDo not claim success without evidence.";
    }

    private String requiredText(JsonNode root, String field) {
        String value = root.path(field).asText("").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        if (value.length() > MAX_POLICY_CHARS) {
            throw new IllegalArgumentException(field + " is too long.");
        }
        return value;
    }

    private List<String> requiredTextArray(JsonNode root, String field, int minimum) {
        JsonNode node = root.path(field);
        if (!node.isArray()) {
            throw new IllegalArgumentException(field + " must be an array.");
        }
        List<String> values = node.valueStream()
                .filter(JsonNode::isTextual)
                .map(value -> value.asText("").trim())
                .filter(value -> !value.isBlank())
                .toList();
        if (values.size() > MAX_ARRAY_ITEMS
                || values.stream().anyMatch(value -> value.length() > MAX_ARRAY_ITEM_CHARS)) {
            throw new IllegalArgumentException(field + " contains too many or oversized values.");
        }
        if (values.size() < minimum) {
            throw new IllegalArgumentException(field + " must contain at least " + minimum + " non-blank values.");
        }
        return values;
    }

    private String normalizeAgentId(String candidate) {
        String normalized = candidate == null ? "" : candidate.trim().toLowerCase();
        if (!normalized.matches("student-[a-z0-9][a-z0-9-]{1,55}")) {
            throw new IllegalArgumentException("Published Agent id must use the student- namespace.");
        }
        return normalized;
    }

    public record PublishedLearningAgent(Agent agent, AgentCatalogResponse catalog) {
    }
}
