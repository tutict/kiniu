package com.kiniu.game.agent;

import com.kiniu.game.memory.MemoryService;
import com.kiniu.game.state.WorldState;
import com.kiniu.game.story.StoryEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestratorService {

    private static final int MAX_SPEAKING_AGENTS = 3;

    private final MemoryService memoryService;

    public AgentOrchestratorService(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    public List<AgentTurnPlan> planTurn(
            String sessionId,
            WorldState worldState,
            StoryEvent storyBeat,
            List<Agent> candidateAgents,
            String playerInput,
            String playerChoice) {
        List<AgentTurnPlan> rankedPlans = candidateAgents.stream()
                .map(agent -> buildPlan(sessionId, worldState, storyBeat, agent, playerInput, playerChoice))
                .sorted(Comparator.comparingInt(AgentTurnPlan::initiativeScore).reversed()
                        .thenComparing(plan -> plan.agent().name()))
                .toList();

        return java.util.stream.IntStream.range(0, rankedPlans.size())
                .mapToObj(index -> {
                    AgentTurnPlan plan = rankedPlans.get(index);
                    return new AgentTurnPlan(
                            plan.agent(),
                            plan.objective(),
                            plan.memorySummary(),
                            plan.initiativeScore(),
                            index < MAX_SPEAKING_AGENTS,
                            plan.scoreFactors());
                })
                .toList();
    }

    private AgentTurnPlan buildPlan(
            String sessionId,
            WorldState worldState,
            StoryEvent storyBeat,
            Agent agent,
            String playerInput,
            String playerChoice) {
        List<AgentScoreFactor> scoreFactors = scoreAgent(worldState, storyBeat, agent, playerInput, playerChoice);
        int score = scoreFactors.stream().mapToInt(AgentScoreFactor::delta).sum();
        String objective = chooseObjective(agent, storyBeat, worldState);
        String memorySummary = memoryService.summarizeAgentMemory(sessionId, agent.id(), 3);
        return new AgentTurnPlan(agent, objective, memorySummary, score, false, List.copyOf(scoreFactors));
    }

    private List<AgentScoreFactor> scoreAgent(
            WorldState worldState,
            StoryEvent storyBeat,
            Agent agent,
            String playerInput,
            String playerChoice) {
        List<AgentScoreFactor> factors = new ArrayList<>();
        factors.add(new AgentScoreFactor("base-initiative", agent.initiative(), "Base initiative from the agent profile."));

        if (agent.id().equals(storyBeat.speakerId())) {
            factors.add(new AgentScoreFactor("scene-speaker", 5, "Agent is the active speaker for this conversation turn."));
        }
        if (storyBeat.spotlightAgentIds().contains(agent.id())) {
            factors.add(new AgentScoreFactor("spotlight", 4, "Agent is already in the spotlight list."));
        }
        if (agent.activeScenes().contains(worldState.getCurrentScene())) {
            factors.add(new AgentScoreFactor("active-scene", 2, "Agent is configured for the current workspace."));
        }
        int relationshipScore = Math.max(0, worldState.getRelationship(agent.id()).aggregate());
        if (relationshipScore > 0) {
            factors.add(new AgentScoreFactor(
                    "relationship",
                    relationshipScore,
                    "Positive relationship state raises initiative."));
        }

        String combined = (safe(playerInput) + " " + safe(playerChoice)).toLowerCase();
        if (combined.contains(agent.id().toLowerCase()) || combined.contains(agent.name().toLowerCase())) {
            factors.add(new AgentScoreFactor("player-mentioned-agent", 3, "Player explicitly mentioned this agent."));
        }
        if ("generated".equals(storyBeat.sourceType()) && !"narrator".equals(agent.id())) {
            factors.add(new AgentScoreFactor(
                    "generated-beat-bonus",
                    1,
                    "Generated turns give specialist agents a small initiative boost."));
        }

        return List.copyOf(factors);
    }

    private String chooseObjective(Agent agent, StoryEvent storyBeat, WorldState worldState) {
        if (!agent.coreGoals().isEmpty()) {
            int indexSeed = Math.abs((worldState.getCurrentScene() + storyBeat.title()).hashCode());
            return agent.coreGoals().get(indexSeed % agent.coreGoals().size());
        }
        return "Sustain the session and react coherently.";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
