package com.kiniu.game.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiniu.game.agent.Agent;
import com.kiniu.game.dto.AgentReplyView;
import com.kiniu.game.dto.BranchOptionView;
import com.kiniu.game.dto.OrchestrationTraceView;
import com.kiniu.game.dto.SandboxPlanRequest;
import com.kiniu.game.dto.SandboxPlanView;
import com.kiniu.game.dto.SessionExportResponse;
import com.kiniu.game.dto.SessionTurnView;
import com.kiniu.game.security.SessionIdValidator;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SessionArchiveService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionArchiveService.class);

    private static final int DEFAULT_EXPORT_LIMIT = 50;
    private static final int MAX_EXPORT_LIMIT = 200;

    private final ObjectMapper objectMapper;
    private final SessionIdValidator sessionIdValidator;
    private final Path exportDirectory;
    private final int maxCachedArchives;
    private final Map<String, SessionArchiveState> sessionArchives = new LinkedHashMap<>(16, 0.75f, true);

    @Autowired
    public SessionArchiveService(
            ObjectMapper objectMapper,
            SessionIdValidator sessionIdValidator,
            @Value("${game.sessions.export-path:data/session-exports}") String exportDirectory,
            @Value("${game.sessions.max-cached-archives:3}") int maxCachedArchives) {
        this.objectMapper = objectMapper;
        this.sessionIdValidator = sessionIdValidator;
        this.exportDirectory = Paths.get(exportDirectory).toAbsolutePath().normalize();
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
        sessionId = sessionIdValidator.normalize(sessionId);
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
        sessionId = sessionIdValidator.normalize(sessionId);
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
                copyAffinityScores(request.finalAffinityScores())));

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
        return buildExportResponse(sessionId, archiveState, 0, DEFAULT_EXPORT_LIMIT);
    }

    public synchronized SessionExportResponse getSessionExport(String sessionId) {
        return getSessionExport(sessionId, 0, DEFAULT_EXPORT_LIMIT);
    }

    public synchronized SessionExportResponse getSessionExport(String sessionId, int offset, int limit) {
        sessionId = sessionIdValidator.normalize(sessionId);
        SessionArchiveState archiveState = getArchiveState(sessionId);
        if (archiveState == null) {
            throw new IllegalArgumentException("No exported session found for " + sessionId);
        }

        return buildExportResponse(sessionId, archiveState, offset, limit);
    }

    private SessionArchiveState getArchiveState(String sessionId) {
        sessionId = sessionIdValidator.normalize(sessionId);
        SessionArchiveState archiveState = sessionArchives.get(sessionId);
        if (archiveState != null) {
            return archiveState;
        }

        SessionExportResponse header = readExportIfExists(headerPath(sessionId));
        SessionExportResponse legacyExport = readExportIfExists(legacyExportPath(sessionId));
        TurnPage turnPage = readPersistedTurns(sessionId, legacyExport, 0, 0);
        SessionTurnView lastTurn = turnPage.lastTurn();
        SessionExportResponse stateSource = header != null ? header : legacyExport;
        if (stateSource == null && lastTurn == null) {
            return null;
        }

        archiveState = new SessionArchiveState(
                sessionId,
                stateSource == null ? safeInstant(lastTurn.timestamp()) : safeInstant(stateSource.updatedAt()),
                stateSource == null ? safeWorldState(lastTurn.stateSnapshot()) : safeWorldState(stateSource.currentState()),
                List.copyOf(stateSource == null ? List.of() : stateSource.agents()),
                List.copyOf(stateSource == null ? List.of() : stateSource.sandboxPlans()),
                turnPage.totalTurns(),
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

    private SessionExportResponse buildExportResponse(String sessionId, SessionArchiveState archiveState, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = normalizeExportLimit(limit);
        TurnPage turnPage = readPersistedTurns(sessionId, readExportIfExists(legacyExportPath(sessionId)), safeOffset, safeLimit);
        return new SessionExportResponse(
                archiveState.sessionId(),
                archiveState.updatedAt(),
                archiveState.currentState(),
                archiveState.agents(),
                turnPage.turns(),
                turnPage.totalTurns(),
                turnPage.offset(),
                turnPage.limit(),
                archiveState.sandboxPlans());
    }

    private TurnPage readPersistedTurns(String sessionId, SessionExportResponse legacyExport, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(0, Math.min(limit, MAX_EXPORT_LIMIT));
        List<SessionTurnView> page = new ArrayList<>();
        int totalTurns = 0;
        SessionTurnView lastTurn = null;

        List<SessionTurnView> legacyTurns = legacyExport == null || legacyExport.turns() == null
                ? List.of()
                : legacyExport.turns();
        for (SessionTurnView turn : legacyTurns) {
            if (turn == null) {
                continue;
            }
            if (totalTurns >= safeOffset && page.size() < safeLimit) {
                page.add(turn);
            }
            lastTurn = turn;
            totalTurns++;
        }

        Path turnLogPath = turnLogPath(sessionId);
        if (!Files.exists(turnLogPath)) {
            return new TurnPage(List.copyOf(page), totalTurns, safeOffset, safeLimit, lastTurn);
        }

        try (var lines = Files.lines(turnLogPath, StandardCharsets.UTF_8)) {
            var iterator = lines.iterator();
            int lineNumber = 0;
            while (iterator.hasNext()) {
                lineNumber++;
                String line = iterator.next();
                if (line.isBlank()) {
                    continue;
                }
                SessionTurnView turn;
                try {
                    turn = objectMapper.readValue(line, SessionTurnView.class);
                } catch (JsonProcessingException exception) {
                    LOGGER.warn("Skipping malformed session turn line {} for {}.", lineNumber, sessionId);
                    continue;
                }
                if (totalTurns >= safeOffset && page.size() < safeLimit) {
                    page.add(turn);
                }
                lastTurn = turn;
                totalTurns++;
            }
            return new TurnPage(List.copyOf(page), totalTurns, safeOffset, safeLimit, lastTurn);
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
            LOGGER.warn("Ignoring unreadable session export {}.", exportPath, exception);
            return null;
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
                    archiveState.turnCount(),
                    0,
                    0,
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
        return resolveSessionPath(sessionId, ".json");
    }

    private Path headerPath(String sessionId) {
        return resolveSessionPath(sessionId, ".session.json");
    }

    private Path turnLogPath(String sessionId) {
        return resolveSessionPath(sessionId, ".turns.jsonl");
    }

    private Path resolveSessionPath(String sessionId, String suffix) {
        String normalizedSessionId = sessionIdValidator.normalize(sessionId);
        Path resolved = exportDirectory.resolve(normalizedSessionId + suffix).normalize();
        if (!resolved.startsWith(exportDirectory)) {
            throw new IllegalArgumentException("Session export path escaped the configured export directory.");
        }
        return resolved;
    }

    private SessionExportResponse normalizeExport(SessionExportResponse exportResponse) {
        if (exportResponse == null) {
            return null;
        }

        List<SessionTurnView> turns = List.copyOf(exportResponse.turns() == null ? List.of() : exportResponse.turns());
        int totalTurns = exportResponse.totalTurns() > 0 ? exportResponse.totalTurns() : turns.size();
        int offset = Math.max(0, exportResponse.offset());
        int limit = exportResponse.limit() > 0 ? Math.min(exportResponse.limit(), MAX_EXPORT_LIMIT) : turns.size();
        return new SessionExportResponse(
                sessionIdValidator.normalize(exportResponse.sessionId()),
                safeInstant(exportResponse.updatedAt()),
                safeWorldState(exportResponse.currentState()),
                List.copyOf(exportResponse.agents() == null ? List.of() : exportResponse.agents()),
                turns,
                totalTurns,
                offset,
                limit,
                List.copyOf(exportResponse.sandboxPlans() == null ? List.of() : exportResponse.sandboxPlans()));
    }

    private int normalizeExportLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_EXPORT_LIMIT;
        }
        return Math.min(limit, MAX_EXPORT_LIMIT);
    }

    private Map<String, Integer> copyAffinityScores(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                normalized.put(key.trim(), value);
            }
        });
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    private Instant safeInstant(Instant value) {
        return value == null ? Instant.EPOCH : value;
    }

    private WorldState safeWorldState(WorldState value) {
        return value == null ? WorldState.initial() : value;
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value.trim();
    }

    private record TurnPage(
            List<SessionTurnView> turns,
            int totalTurns,
            int offset,
            int limit,
            SessionTurnView lastTurn) {
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