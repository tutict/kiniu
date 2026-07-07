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
import com.kiniu.game.security.SessionIdValidator;
import com.kiniu.game.state.WorldState;
import com.kiniu.game.story.StoryEngine;
import com.kiniu.game.story.StoryEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GameEngine {

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
    private final SessionIdValidator sessionIdValidator;
    private final int maxSessionStates;
    private final Map<String, WorldState> sessionStates = new LinkedHashMap<>(16, 0.75f, true);

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
            StoryEngine storyEngine,
            SessionIdValidator sessionIdValidator,
            @Value("${game.sessions.max-state-sessions:3}") int maxSessionStates) {
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
        this.sessionIdValidator = sessionIdValidator;
        this.maxSessionStates = Math.max(1, maxSessionStates);
    }

    public GameResponse next(GameRequest request) {
        String sessionId = normalizeSessionId(request.sessionId());
        String input = normalizeText(request.input());
        String choice = normalizeText(request.choice());

        WorldState state = getSessionState(sessionId);
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

    private synchronized WorldState getSessionState(String sessionId) {
        WorldState state = sessionStates.computeIfAbsent(sessionId, key -> WorldState.initial());
        while (sessionStates.size() > maxSessionStates) {
            String eldestSessionId = sessionStates.keySet().iterator().next();
            sessionStates.remove(eldestSessionId);
        }
        return state;
    }

    private void updateWorldState(WorldState state, String input, String choice) {
        state.addFlag("session-started");

        String combined = (input + " " + choice).toLowerCase();
        if (containsAny(combined, "陪聊", "闲聊", "状态", "情绪", "companion", "chat")) {
            state.adjustRelationship("companion", 1, 1, 0);
            state.addFlag("mode-companion");
        }
        if (containsAny(combined, "java", "jvm", "spring", "并发", "线程", "八股", "面试", "interview")) {
            state.adjustRelationship("java-rag-interviewer", 1, 0, 1);
            state.addFlag("mode-interview");
        }
        if (containsAny(combined, "rag", "embedding", "向量", "召回", "重排", "检索", "知识库", "文档")) {
            state.adjustRelationship("knowledge-curator", 1, 0, 1);
            state.addFlag("mode-knowledge");
        }
        if (containsAny(combined, "项目", "代码", "bug", "架构", "任务", "计划", "project", "code")) {
            state.adjustRelationship("project-agent", 1, 0, 1);
            state.addFlag("mode-project");
        }
        if (containsAny(combined, "写作", "文章", "改稿", "提纲", "表达", "writing", "draft")) {
            state.adjustRelationship("writing-coach", 1, 0, 1);
            state.addFlag("mode-writing");
        }
        if (containsAny(combined, "help", "帮助", "建议", "下一步", "plan", "review", "复盘", "总结")) {
            state.adjustRelationship("narrator", 1, 0, 1);
        }
        if (containsAny(combined, "doubt", "refuse", "不对", "换一个", "别", "不要")) {
            state.addFlag("needs-reroute");
        }
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
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
        return sessionIdValidator.normalize(sessionId);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
