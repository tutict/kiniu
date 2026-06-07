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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SessionArchiveService {

    private final ObjectMapper objectMapper;
    private final Path exportDirectory;
    private final int maxCachedArchives;
    private final Map<String, SessionArchiveState> sessionArchives = new LinkedHashMap<>(16, 0.75f, true);

    @Autowired
    public SessionArchiveService(
            ObjectMapper objectMapper,
            @Value("${game.sessions.export-path:data/session-exports}") String exportDirectory,
            @Value("${game.sessions.max-cached-archives:3}") int maxCachedArchives) {
        this.objectMapper = objectMapper;
        this.exportDirectory = Paths.get(exportDirectory).toAbsolutePath();
        this.maxCachedArchives = Math.max(1, maxCachedArchives);
    }

    public synchronized void recordTurn(
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
        SessionArchiveState previousState = getArchiveState(sessionId);
        List<SandboxPlanView> sandboxPlans = previousState == null
                ? List.of()
                : previousState.sandboxPlans();

        String parentTurnId = previousState == null ? null : previousState.lastTurnId();
        int turnCount = previousState == null ? 0 : previousState.turnCount();
        SessionTurnView turn = new SessionTurnView(
                "turn-" + turnCount,
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

        appendTurn(sessionId, turn);
        SessionArchiveState archiveState = new SessionArchiveState(
                sessionId,
                Instant.now(),
                worldState.snapshot(),
                List.copyOf(activeAgents),
                List.copyOf(sandboxPlans),
                turnCount + 1,
                turn.id());
        cacheArchiveState(sessionId, archiveState);
        persistHeader(archiveState);
    }

    public synchronized SessionExportResponse saveSandboxPlan(String sessionId, SandboxPlanRequest request) {
        SessionArchiveState previousState = getArchiveState(sessionId);
        if (previousState == null) {
            throw new IllegalArgumentException("No exported session found for " + sessionId);
        }

        List<SandboxPlanView> sandboxPlans = new ArrayList<>(previousState.sandboxPlans());
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

        SessionArchiveState archiveState = new SessionArchiveState(
                previousState.sessionId(),
                Instant.now(),
                previousState.currentState(),
                List.copyOf(previousState.agents()),
                List.copyOf(sandboxPlans),
                previousState.turnCount(),
                previousState.lastTurnId());
        cacheArchiveState(sessionId, archiveState);
        persistHeader(archiveState);
        return buildExportResponse(sessionId, archiveState);
    }

    public synchronized SessionExportResponse getSessionExport(String sessionId) {
        SessionArchiveState archiveState = getArchiveState(sessionId);
        if (archiveState == null) {
            throw new IllegalArgumentException("No exported session found for " + sessionId);
        }

        return buildExportResponse(sessionId, archiveState);
    }

    private SessionArchiveState getArchiveState(String sessionId) {
        SessionArchiveState archiveState = sessionArchives.get(sessionId);
        if (archiveState != null) {
            return archiveState;
        }

        SessionExportResponse header = readExportIfExists(headerPath(sessionId));
        SessionExportResponse legacyExport = readExportIfExists(legacyExportPath(sessionId));
        List<SessionTurnView> turns = readPersistedTurns(sessionId, legacyExport);
        SessionTurnView lastTurn = turns.isEmpty() ? null : turns.get(turns.size() - 1);
        SessionExportResponse stateSource = header != null ? header : legacyExport;
        if (stateSource == null && lastTurn == null) {
            return null;
        }

        archiveState = new SessionArchiveState(
                sessionId,
                stateSource == null ? lastTurn.timestamp() : stateSource.updatedAt(),
                stateSource == null ? lastTurn.stateSnapshot() : stateSource.currentState(),
                List.copyOf(stateSource == null ? List.of() : stateSource.agents()),
                List.copyOf(stateSource == null ? List.of() : stateSource.sandboxPlans()),
                turns.size(),
                lastTurn == null ? null : lastTurn.id());
        cacheArchiveState(sessionId, archiveState);
        return archiveState;
    }

    private void cacheArchiveState(String sessionId, SessionArchiveState archiveState) {
        sessionArchives.put(sessionId, archiveState);
        while (sessionArchives.size() > maxCachedArchives) {
            String eldestSessionId = sessionArchives.keySet().iterator().next();
            sessionArchives.remove(eldestSessionId);
        }
    }

    private SessionExportResponse buildExportResponse(String sessionId, SessionArchiveState archiveState) {
        List<SessionTurnView> turns = readPersistedTurns(sessionId, readExportIfExists(legacyExportPath(sessionId)));
        return new SessionExportResponse(
                archiveState.sessionId(),
                archiveState.updatedAt(),
                archiveState.currentState(),
                archiveState.agents(),
                List.copyOf(turns),
                archiveState.sandboxPlans());
    }

    private List<SessionTurnView> readPersistedTurns(String sessionId, SessionExportResponse legacyExport) {
        List<SessionTurnView> turns = new ArrayList<>();
        if (legacyExport != null) {
            turns.addAll(legacyExport.turns());
        }

        Path turnLogPath = turnLogPath(sessionId);
        if (!Files.exists(turnLogPath)) {
            return turns;
        }

        try {
            List<String> lines = Files.readAllLines(turnLogPath, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (!line.isBlank()) {
                    turns.add(objectMapper.readValue(line, SessionTurnView.class));
                }
            }
            return turns;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read session turns for " + sessionId, exception);
        }
    }

    private SessionExportResponse readExportIfExists(Path exportPath) {
        if (!Files.exists(exportPath)) {
            return null;
        }

        try {
            return normalizeExport(objectMapper.readValue(exportPath.toFile(), SessionExportResponse.class));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read session export " + exportPath, exception);
        }
    }

    private void persistHeader(SessionArchiveState archiveState) {
        try {
            Files.createDirectories(exportDirectory);
            objectMapper.writeValue(headerPath(archiveState.sessionId()).toFile(), new SessionExportResponse(
                    archiveState.sessionId(),
                    archiveState.updatedAt(),
                    archiveState.currentState(),
                    archiveState.agents(),
                    List.of(),
                    archiveState.sandboxPlans()));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist session header for " + archiveState.sessionId(), exception);
        }
    }

    private void appendTurn(String sessionId, SessionTurnView turn) {
        try {
            Files.createDirectories(exportDirectory);
            Files.writeString(
                    turnLogPath(sessionId),
                    objectMapper.writeValueAsString(turn) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to append session turn for " + sessionId, exception);
        }
    }

    private Path legacyExportPath(String sessionId) {
        return exportDirectory.resolve(sessionId + ".json");
    }

    private Path headerPath(String sessionId) {
        return exportDirectory.resolve(sessionId + ".session.json");
    }

    private Path turnLogPath(String sessionId) {
        return exportDirectory.resolve(sessionId + ".turns.jsonl");
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

    private record SessionArchiveState(
            String sessionId,
            Instant updatedAt,
            WorldState currentState,
            List<Agent> agents,
            List<SandboxPlanView> sandboxPlans,
            int turnCount,
            String lastTurnId) {
    }
}
