package com.kiniu.game.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiniu.game.agent.Agent;
import com.kiniu.game.dto.AgentReplyView;
import com.kiniu.game.dto.BranchOptionView;
import com.kiniu.game.dto.OrchestrationTraceView;
import com.kiniu.game.dto.SandboxPlanRequest;
import com.kiniu.game.dto.SandboxPlanView;
import com.kiniu.game.dto.SessionExportResponse;
import com.kiniu.game.dto.SessionTurnView;
import com.kiniu.game.state.WorldState;
import com.kiniu.game.story.StoryEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SessionArchiveService {

    private final ObjectMapper objectMapper;
    private final Path exportDirectory;
    private final ConcurrentMap<String, SessionExportResponse> sessionExports = new ConcurrentHashMap<>();

    @Autowired
    public SessionArchiveService(
            ObjectMapper objectMapper,
            @Value("${game.sessions.export-path:data/session-exports}") String exportDirectory) {
        this.objectMapper = objectMapper;
        this.exportDirectory = Paths.get(exportDirectory).toAbsolutePath();
    }

    public void recordTurn(
            String sessionId,
            String playerInput,
            String playerChoice,
            WorldState worldState,
            StoryEvent storyEvent,
            List<String> presentedChoices,
            List<BranchOptionView> presentedBranchOptions,
            List<Agent> activeAgents,
            List<AgentReplyView> agentReplies,
            String directorMessage,
            String combinedSummary,
            OrchestrationTraceView orchestration) {
        SessionExportResponse previousExport = normalizeExport(sessionExports.get(sessionId));
        List<SessionTurnView> turns = previousExport == null
                ? new ArrayList<>()
                : new ArrayList<>(previousExport.turns());
        List<SandboxPlanView> sandboxPlans = previousExport == null
                ? new ArrayList<>()
                : new ArrayList<>(previousExport.sandboxPlans());

        String parentTurnId = turns.isEmpty() ? null : turns.get(turns.size() - 1).id();
        SessionTurnView turn = new SessionTurnView(
                "turn-" + turns.size(),
                parentTurnId,
                Instant.now(),
                playerInput,
                playerChoice,
                worldState.getCurrentScene(),
                worldState.getCurrentNodeId(),
                storyEvent.id(),
                storyEvent,
                directorMessage,
                combinedSummary,
                presentedChoices,
                presentedBranchOptions,
                List.copyOf(agentReplies),
                worldState.snapshot(),
                orchestration);
        turns.add(turn);

        SessionExportResponse exportResponse = new SessionExportResponse(
                sessionId,
                Instant.now(),
                worldState.snapshot(),
                List.copyOf(activeAgents),
                List.copyOf(turns),
                List.copyOf(sandboxPlans));
        sessionExports.put(sessionId, exportResponse);
        persistExport(exportResponse);
    }

    public SessionExportResponse saveSandboxPlan(String sessionId, SandboxPlanRequest request) {
        SessionExportResponse previousExport = normalizeExport(sessionExports.get(sessionId));
        if (previousExport == null) {
            previousExport = getSessionExport(sessionId);
        }

        List<SandboxPlanView> sandboxPlans = new ArrayList<>(previousExport.sandboxPlans());
        sandboxPlans.add(0, new SandboxPlanView(
                "sandbox-" + Instant.now().toEpochMilli(),
                sessionId,
                Instant.now(),
                blankIfNull(request.sceneId()),
                blankIfNull(request.nodeId()),
                blankIfNull(request.title()),
                blankIfNull(request.summary()),
                List.copyOf(request.steps() == null ? List.of() : request.steps()),
                request.totalRelationshipDelta(),
                List.copyOf(request.finalFlags() == null ? List.of() : request.finalFlags()),
                Map.copyOf(request.finalAffinityScores() == null ? Map.of() : request.finalAffinityScores())));

        SessionExportResponse exportResponse = new SessionExportResponse(
                previousExport.sessionId(),
                Instant.now(),
                previousExport.currentState(),
                List.copyOf(previousExport.agents()),
                List.copyOf(previousExport.turns()),
                List.copyOf(sandboxPlans));
        sessionExports.put(sessionId, exportResponse);
        persistExport(exportResponse);
        return exportResponse;
    }

    public SessionExportResponse getSessionExport(String sessionId) {
        SessionExportResponse exportResponse = normalizeExport(sessionExports.get(sessionId));
        if (exportResponse != null) {
            sessionExports.put(sessionId, exportResponse);
            return exportResponse;
        }

        Path exportPath = exportDirectory.resolve(sessionId + ".json");
        if (!Files.exists(exportPath)) {
            throw new IllegalArgumentException("No exported session found for " + sessionId);
        }

        try {
            SessionExportResponse loaded = normalizeExport(objectMapper.readValue(exportPath.toFile(), SessionExportResponse.class));
            sessionExports.put(sessionId, loaded);
            return loaded;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read session export for " + sessionId, exception);
        }
    }

    private void persistExport(SessionExportResponse exportResponse) {
        try {
            Files.createDirectories(exportDirectory);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(exportDirectory.resolve(exportResponse.sessionId() + ".json").toFile(), exportResponse);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist session export for " + exportResponse.sessionId(), exception);
        }
    }

    private SessionExportResponse normalizeExport(SessionExportResponse exportResponse) {
        if (exportResponse == null) {
            return null;
        }

        return new SessionExportResponse(
                exportResponse.sessionId(),
                exportResponse.updatedAt(),
                exportResponse.currentState(),
                List.copyOf(exportResponse.agents() == null ? List.of() : exportResponse.agents()),
                List.copyOf(exportResponse.turns() == null ? List.of() : exportResponse.turns()),
                List.copyOf(exportResponse.sandboxPlans() == null ? List.of() : exportResponse.sandboxPlans()));
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
