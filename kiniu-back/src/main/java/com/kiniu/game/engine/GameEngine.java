package com.kiniu.game.engine;

import com.kiniu.game.agent.Agent;
import com.kiniu.game.agent.AgentManager;
import com.kiniu.game.agent.AgentOrchestratorService;
import com.kiniu.game.agent.AgentService;
import com.kiniu.game.agent.AgentTurnPlan;
import com.kiniu.game.ai.AITelemetryCollector;
import com.kiniu.game.dto.AgentReplyView;
import com.kiniu.game.dto.BranchOptionView;
import com.kiniu.game.dto.GameRequest;
import com.kiniu.game.dto.GameResponse;
import com.kiniu.game.dto.OrchestrationTraceView;
import com.kiniu.game.memory.MemoryService;
import com.kiniu.game.state.WorldState;
import com.kiniu.game.story.StoryEngine;
import com.kiniu.game.story.StoryEvent;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class GameEngine {

    private static final String DEFAULT_SESSION_ID = "default-session";

    private final AgentService agentService;
    private final AgentManager agentManager;
    private final AgentOrchestratorService agentOrchestratorService;
    private final PlotAgentService plotAgentService;
    private final DirectorAgentService directorAgentService;
    private final DynamicBranchService dynamicBranchService;
    private final BranchOptionPlannerService branchOptionPlannerService;
    private final TurnPlannerService turnPlannerService;
    private final TurnCriticService turnCriticService;
    private final OrchestrationTraceFactory orchestrationTraceFactory;
    private final AITelemetryCollector aiTelemetryCollector;
    private final SessionArchiveService sessionArchiveService;
    private final MemoryService memoryService;
    private final StoryEngine storyEngine;
    private final ConcurrentMap<String, WorldState> sessionStates = new ConcurrentHashMap<>();

    public GameEngine(
            AgentService agentService,
            AgentManager agentManager,
            AgentOrchestratorService agentOrchestratorService,
            PlotAgentService plotAgentService,
            DirectorAgentService directorAgentService,
            DynamicBranchService dynamicBranchService,
            BranchOptionPlannerService branchOptionPlannerService,
            TurnPlannerService turnPlannerService,
            TurnCriticService turnCriticService,
            OrchestrationTraceFactory orchestrationTraceFactory,
            AITelemetryCollector aiTelemetryCollector,
            SessionArchiveService sessionArchiveService,
            MemoryService memoryService,
            StoryEngine storyEngine) {
        this.agentService = agentService;
        this.agentManager = agentManager;
        this.agentOrchestratorService = agentOrchestratorService;
        this.plotAgentService = plotAgentService;
        this.directorAgentService = directorAgentService;
        this.dynamicBranchService = dynamicBranchService;
        this.branchOptionPlannerService = branchOptionPlannerService;
        this.turnPlannerService = turnPlannerService;
        this.turnCriticService = turnCriticService;
        this.orchestrationTraceFactory = orchestrationTraceFactory;
        this.aiTelemetryCollector = aiTelemetryCollector;
        this.sessionArchiveService = sessionArchiveService;
        this.memoryService = memoryService;
        this.storyEngine = storyEngine;
    }

    public GameResponse next(GameRequest request) {
        String sessionId = normalizeSessionId(request.sessionId());
        String input = normalizeText(request.input());
        String choice = normalizeText(request.choice());

        WorldState state = sessionStates.computeIfAbsent(sessionId, key -> WorldState.initial());
        updateWorldState(state, input, choice);

        memoryService.storeDialogue(sessionId, "Player", buildPlayerTurn(input, choice));

        Optional<StoryEvent> seededEvent = storyEngine.findTriggeredEvent(state, input, choice);
        String speakerId = seededEvent.map(StoryEvent::speakerId)
                .orElseGet(() -> storyEngine.getCurrentSpeakerId(state));
        List<Agent> contextAgents = agentManager.resolveActiveAgents(state, speakerId, input, choice);
        StoryEvent storyBeat = plotAgentService.resolveStoryBeat(state, input, choice, seededEvent, contextAgents);
        List<Agent> activeAgents = agentManager.resolveActiveAgents(state, storyBeat.speakerId(), input, choice);
        List<String> nextChoices = dynamicBranchService.generateChoices(state, storyBeat, activeAgents);
        StoryEvent directedBeat =
                directorAgentService.directStoryBeat(state, storyBeat, activeAgents, input, choice, nextChoices);
        List<String> presentedChoices = List.copyOf(directedBeat.choices());
        List<BranchOption> branchOptions =
                branchOptionPlannerService.annotateChoices(directedBeat, activeAgents, input, choice, presentedChoices);
        List<BranchOptionView> branchOptionViews = branchOptions.stream()
                .map(option -> new BranchOptionView(
                        option.label(),
                        option.intent(),
                        option.risk(),
                        option.targetMood(),
                        option.targetAgentId(),
                        option.consequenceSummary(),
                        option.relationshipDelta(),
                        option.addedFlags(),
                        option.removedFlags(),
                        option.source()))
                .toList();
        TurnPlannerBrief plannerBrief =
                turnPlannerService.plan(state, directedBeat, activeAgents, input, choice, presentedChoices);
        List<AgentTurnPlan> turnPlans =
                agentOrchestratorService.planTurn(sessionId, state, directedBeat, activeAgents, input, choice);
        List<AgentTurnPlan> speakingPlans = turnPlans.stream().filter(AgentTurnPlan::shouldSpeak).toList();

        memoryService.storeDialogue(sessionId, "Director", directedBeat.directorSummary());

        List<String> recentDialogue = memoryService.getRecentDialogue(sessionId);
        List<AgentReplyView> agentReplies = speakingPlans.stream()
                .map(plan -> agentService.generateResponse(
                        plan,
                        input,
                        choice,
                        state.snapshot(),
                        recentDialogue,
                directedBeat))
                .toList();
        TurnCritiqueSummary critiqueSummary =
                turnCriticService.critique(directedBeat, presentedChoices, turnPlans, agentReplies);
        OrchestrationTraceView orchestration = orchestrationTraceFactory.create(
                directedBeat,
                activeAgents,
                presentedChoices,
                branchOptions,
                turnPlans,
                agentReplies,
                plannerBrief,
                critiqueSummary,
                aiTelemetryCollector.snapshot());

        agentReplies.forEach(reply -> memoryService.storeDialogue(sessionId, reply.agentName(), reply.message()));
        for (int index = 0; index < speakingPlans.size() && index < agentReplies.size(); index++) {
            AgentTurnPlan plan = speakingPlans.get(index);
            AgentReplyView reply = agentReplies.get(index);
            memoryService.storeAgentMemory(
                    sessionId,
                    plan.agent().id(),
                    agentService.generateMemoryNote(plan, directedBeat, input, choice, reply.message()));
        }

        String agentMessage = agentReplies.stream()
                .map(reply -> reply.agentName() + ": " + reply.message())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("No agent replied.");
        String combinedMessage = buildCombinedMessage(directedBeat, agentMessage);

        sessionArchiveService.recordTurn(
                sessionId,
                input,
                choice,
                state,
                directedBeat,
                presentedChoices,
                branchOptionViews,
                speakingPlans.stream().map(AgentTurnPlan::agent).toList(),
                agentReplies,
                directedBeat.directorSummary(),
                combinedMessage,
                orchestration);

        return new GameResponse(
                sessionId,
                combinedMessage,
                presentedChoices,
                branchOptionViews,
                state.snapshot(),
                agentReplies,
                directedBeat.directorSummary(),
                directedBeat,
                orchestration);
    }

    private void updateWorldState(WorldState state, String input, String choice) {
        state.addFlag("session-started");

        String combined = (input + " " + choice).toLowerCase();
        if (combined.contains("trust") || combined.contains("help") || combined.contains("follow")) {
            state.adjustRelationship("lyra", 1, 0, 0);
        }
        if (combined.contains("doubt") || combined.contains("refuse") || combined.contains("leave")) {
            state.adjustRelationship("lyra", -1, 0, 1);
        }
        if (combined.contains("care") || combined.contains("stay") || combined.contains("protect")) {
            state.adjustRelationship("lyra", 0, 1, 0);
        }
        if (combined.contains("ask") || combined.contains("why") || combined.contains("who")) {
            state.adjustRelationship("lyra", 0, 0, 1);
            state.adjustRelationship("rowan", 0, 0, 1);
        }
        if (combined.contains("rowan")) {
            state.adjustRelationship("rowan", 1, 0, 1);
        }
        if (combined.contains("lyra")) {
            state.adjustRelationship("lyra", 1, 1, 0);
        }
    }

    private String buildCombinedMessage(StoryEvent storyBeat, String agentMessage) {
        StringBuilder combinedMessage = new StringBuilder();
        combinedMessage.append(storyBeat.title())
                .append('\n')
                .append(storyBeat.narrative());

        if (!storyBeat.directorSummary().isBlank()) {
            combinedMessage.append("\n\n").append(storyBeat.directorSummary());
        }
        if (!agentMessage.isBlank()) {
            combinedMessage.append("\n\n").append(agentMessage);
        }

        return combinedMessage.toString();
    }

    private String buildPlayerTurn(String input, String choice) {
        if (!choice.isBlank() && !input.isBlank()) {
            return "input=\"" + input + "\", choice=\"" + choice + "\"";
        }
        if (!choice.isBlank()) {
            return "choice=\"" + choice + "\"";
        }
        if (!input.isBlank()) {
            return "input=\"" + input + "\"";
        }
        return "The player waits in silence.";
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return DEFAULT_SESSION_ID;
        }
        return sessionId.trim();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
