package com.kiniu.game.story;

import com.kiniu.game.agent.Agent;
import com.kiniu.game.dto.AgentCatalogResponse;
import com.kiniu.game.dto.StoryAnalysisResponse;
import com.kiniu.game.dto.StoryCatalogResponse;
import com.kiniu.game.dto.StoryChoiceView;
import com.kiniu.game.dto.StoryIssueView;
import com.kiniu.game.dto.StoryNodeView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class StoryCompilerService {

    public StoryAnalysisResponse analyze(StoryCatalogResponse catalog) {
        return analyze(catalog, null);
    }

    public StoryAnalysisResponse analyze(StoryCatalogResponse catalog, AgentCatalogResponse agentCatalog) {
        List<StoryIssueView> issues = new ArrayList<>();
        if (catalog == null) {
            issues.add(new StoryIssueView("error", "catalog.null", "Story catalog must not be null.", null, null));
            return new StoryAnalysisResponse(
                    "",
                    0,
                    0,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    1,
                    0,
                    List.copyOf(issues));
        }

        List<StoryNodeView> nodes = catalog.nodes() == null ? List.of() : catalog.nodes();
        Map<String, Agent> agentsById = agentCatalog == null || agentCatalog.agents() == null
                ? Map.of()
                : agentCatalog.agents().stream().collect(java.util.stream.Collectors.toMap(
                        Agent::id,
                        agent -> agent,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));

        if (safe(catalog.entryNodeId()).isBlank()) {
            issues.add(new StoryIssueView("error", "entry.blank", "Entry node id must not be blank.", null, null));
        }
        if (nodes.isEmpty()) {
            issues.add(new StoryIssueView(
                    "error", "nodes.empty", "Story catalog must contain at least one node.", null, null));
        }

        java.util.LinkedHashMap<String, StoryNodeView> nodeMap = new java.util.LinkedHashMap<>();
        for (StoryNodeView node : nodes) {
            if (node == null) {
                issues.add(new StoryIssueView("error", "node.null", "Catalog contains a null node.", null, null));
                continue;
            }

            String nodeId = safe(node.id());
            if (nodeId.isBlank()) {
                issues.add(new StoryIssueView("error", "node.id.blank", "Node id must not be blank.", null, null));
                continue;
            }
            if (nodeMap.putIfAbsent(nodeId, node) != null) {
                issues.add(new StoryIssueView(
                        "error", "node.id.duplicate", "Duplicate node id \"" + nodeId + "\".", nodeId, null));
            }

            if (safe(node.title()).isBlank()) {
                issues.add(new StoryIssueView(
                        "warning", "node.title.blank", "Node title is blank.", nodeId, null));
            }
            if (safe(node.narrative()).isBlank()) {
                issues.add(new StoryIssueView(
                        "warning", "node.narrative.blank", "Node narrative is blank.", nodeId, null));
            }
            if (safe(node.sceneId()).isBlank()) {
                issues.add(new StoryIssueView(
                        "warning", "node.scene.blank", "Node scene id is blank.", nodeId, null));
            }
            if (safe(node.speakerId()).isBlank()) {
                issues.add(new StoryIssueView(
                        "warning", "node.speaker.blank", "Node speaker id is blank.", nodeId, null));
            } else if (!agentsById.isEmpty() && !agentsById.containsKey(node.speakerId())) {
                issues.add(new StoryIssueView(
                        "warning",
                        "node.speaker.unknown",
                        "Node speaker \"" + node.speakerId() + "\" is missing from the current agent catalog.",
                        nodeId,
                        null));
            }
        }

        if (!safe(catalog.entryNodeId()).isBlank() && !nodeMap.containsKey(catalog.entryNodeId())) {
            issues.add(new StoryIssueView(
                    "error",
                    "entry.missing",
                    "Entry node \"" + catalog.entryNodeId() + "\" does not exist in the node list.",
                    catalog.entryNodeId(),
                    null));
        }

        Set<String> sceneIds = new LinkedHashSet<>();
        Set<String> endingNodeIds = new LinkedHashSet<>();
        int totalChoices = 0;

        for (StoryNodeView node : nodeMap.values()) {
            String nodeId = node.id();
            sceneIds.add(safe(node.sceneId()));

            List<StoryChoiceView> choices = node.choices() == null ? List.of() : node.choices();
            totalChoices += choices.size();
            if (choices.isEmpty()) {
                endingNodeIds.add(nodeId);
            }

            Set<String> choiceIds = new LinkedHashSet<>();
            for (StoryChoiceView choice : choices) {
                String choiceId = safe(choice.id());
                if (choiceId.isBlank()) {
                    issues.add(new StoryIssueView(
                            "error", "choice.id.blank", "Choice id must not be blank.", nodeId, null));
                } else if (!choiceIds.add(choiceId)) {
                    issues.add(new StoryIssueView(
                            "error",
                            "choice.id.duplicate",
                            "Duplicate choice id \"" + choiceId + "\" inside node \"" + nodeId + "\".",
                            nodeId,
                            choiceId));
                }

                if (safe(choice.label()).isBlank()) {
                    issues.add(new StoryIssueView(
                            "warning", "choice.label.blank", "Choice label is blank.", nodeId, choiceId));
                }

                String targetNodeId = safe(choice.targetNodeId());
                if (targetNodeId.isBlank()) {
                    issues.add(new StoryIssueView(
                            "error", "choice.target.blank", "Choice target node id is blank.", nodeId, choiceId));
                } else if (!nodeMap.containsKey(targetNodeId)) {
                    issues.add(new StoryIssueView(
                            "error",
                            "choice.target.missing",
                            "Choice points to missing target node \"" + targetNodeId + "\".",
                            nodeId,
                            choiceId));
                } else if (targetNodeId.equals(nodeId)) {
                    issues.add(new StoryIssueView(
                            "warning",
                            "choice.target.self",
                            "Choice loops back to the same node.",
                            nodeId,
                            choiceId));
                }

                Set<String> requiredFlags = Set.copyOf(choice.requiredFlags() == null ? List.of() : choice.requiredFlags());
                Set<String> blockedFlags = Set.copyOf(choice.blockedFlags() == null ? List.of() : choice.blockedFlags());
                requiredFlags.stream()
                        .filter(blockedFlags::contains)
                        .sorted()
                        .forEach(flag -> issues.add(new StoryIssueView(
                                "error",
                                "choice.flag.conflict",
                                "Choice requires and blocks the same flag \"" + flag + "\".",
                                nodeId,
                                choiceId)));
            }

            if (choices.isEmpty() && !hasTerminalTag(node)) {
                issues.add(new StoryIssueView(
                        "warning",
                        "node.deadend.untagged",
                        "Node has no outgoing choices and is not tagged as an ending or handoff.",
                        nodeId,
                        null));
            }
        }

        Set<String> reachableNodeIds = traverseReachableNodes(catalog.entryNodeId(), nodeMap);
        List<String> unreachableNodeIds = nodeMap.keySet().stream()
                .filter(nodeId -> !reachableNodeIds.contains(nodeId))
                .sorted()
                .toList();

        unreachableNodeIds.forEach(nodeId -> issues.add(new StoryIssueView(
                "warning",
                "node.unreachable",
                "Node is unreachable from the current entry node.",
                nodeId,
                null)));

        List<StoryIssueView> orderedIssues = issues.stream()
                .sorted(Comparator.comparingInt(this::severityRank)
                        .thenComparing(StoryIssueView::code)
                        .thenComparing(issue -> safe(issue.nodeId()))
                        .thenComparing(issue -> safe(issue.choiceId())))
                .toList();

        long errorCount = orderedIssues.stream().filter(issue -> "error".equals(issue.severity())).count();
        long warningCount = orderedIssues.stream().filter(issue -> "warning".equals(issue.severity())).count();

        return new StoryAnalysisResponse(
                safe(catalog.entryNodeId()),
                nodeMap.size(),
                totalChoices,
                reachableNodeIds.stream().sorted().toList(),
                unreachableNodeIds,
                endingNodeIds.stream().sorted().toList(),
                sceneIds.stream().filter(sceneId -> !sceneId.isBlank()).sorted().toList(),
                (int) errorCount,
                (int) warningCount,
                orderedIssues);
    }

    private Set<String> traverseReachableNodes(String entryNodeId, Map<String, StoryNodeView> nodeMap) {
        if (entryNodeId == null || entryNodeId.isBlank() || !nodeMap.containsKey(entryNodeId)) {
            return Set.of();
        }

        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(entryNodeId);
        visited.add(entryNodeId);

        while (!queue.isEmpty()) {
            StoryNodeView node = nodeMap.get(queue.removeFirst());
            if (node == null || node.choices() == null) {
                continue;
            }
            for (StoryChoiceView choice : node.choices()) {
                String target = safe(choice.targetNodeId());
                if (!target.isBlank() && nodeMap.containsKey(target) && visited.add(target)) {
                    queue.addLast(target);
                }
            }
        }

        return visited;
    }

    private boolean hasTerminalTag(StoryNodeView node) {
        List<String> tags = node.tags() == null ? List.of() : node.tags();
        return tags.stream()
                .map(tag -> tag == null ? "" : tag.trim().toLowerCase())
                .anyMatch(tag -> tag.equals("ending") || tag.equals("chapter-end") || tag.equals("handoff"));
    }

    private int severityRank(StoryIssueView issue) {
        return switch (safe(issue.severity())) {
            case "error" -> 0;
            case "warning" -> 1;
            default -> 2;
        };
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
