package com.kiniu.game.dto;

import com.kiniu.game.agent.Agent;
import com.kiniu.game.state.WorldState;
import java.time.Instant;
import java.util.List;

public record SessionExportResponse(
        String sessionId,
        Instant updatedAt,
        WorldState currentState,
        List<Agent> agents,
        List<SessionTurnView> turns,
        int totalTurns,
        int offset,
        int limit,
        List<SandboxPlanView> sandboxPlans) {
}
