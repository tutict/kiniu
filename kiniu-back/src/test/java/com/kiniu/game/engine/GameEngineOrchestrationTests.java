package com.kiniu.game.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiniu.game.dto.BranchOptionView;
import com.kiniu.game.dto.GameRequest;
import com.kiniu.game.dto.GameResponse;
import com.kiniu.game.dto.SandboxPlanRequest;
import com.kiniu.game.dto.SessionExportResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "game.story.catalog-path=target/test-orchestration-story-catalog.json",
        "game.agents.catalog-path=target/test-orchestration-agent-catalog.json",
        "game.sessions.export-path=target/test-orchestration-session-exports"
})
class GameEngineOrchestrationTests {

    @Autowired
    private GameEngine gameEngine;

    @Autowired
    private SessionArchiveService sessionArchiveService;

    @Test
    void shouldGenerateDynamicBeatWhenNoSeedChoiceMatches() {
        String sessionId = "generated-" + UUID.randomUUID();

        GameResponse response = gameEngine.next(new GameRequest(sessionId, "look for a fresh lead with Rowan", ""));

        assertThat(response.storyEvent()).isNotNull();
        assertThat(response.storyEvent().sourceType()).isEqualTo("generated");
        assertThat(response.state().getCurrentNodeId()).startsWith("generated.");
        assertThat(response.directorMessage()).contains("Director framing");
        assertThat(response.orchestration()).isNotNull();
        assertThat(response.orchestration().planner()).isNotNull();
        assertThat(response.orchestration().critic()).isNotNull();
        assertThat(response.branchOptions()).isNotEmpty();
        assertThat(response.branchOptions().get(0).label()).isNotBlank();
        assertThat(response.orchestration().aiInvocations()).isNotEmpty();
        assertThat(response.orchestration().nextBranchOptions()).isNotEmpty();
        assertThat(response.orchestration().plans()).isNotEmpty();
        assertThat(response.agentReplies()).isNotEmpty();
        assertThat(response.agentReplies().get(0).objective()).isNotBlank();
        assertThat(response.agentReplies().get(0).initiativeScore()).isGreaterThan(0);

        SessionExportResponse exportResponse = sessionArchiveService.getSessionExport(sessionId);
        assertThat(exportResponse.turns()).hasSize(1);
        assertThat(exportResponse.turns().get(0).storyEvent().sourceType()).isEqualTo("generated");
        assertThat(exportResponse.turns().get(0).directorMessage()).contains("Director framing");
        assertThat(exportResponse.turns().get(0).presentedBranchOptions()).isNotEmpty();
        assertThat(exportResponse.turns().get(0).orchestration()).isNotNull();
        assertThat(exportResponse.turns().get(0).orchestration().critic().notes()).isNotEmpty();
        assertThat(exportResponse.turns().get(0).orchestration().aiInvocations()).isNotEmpty();
    }

    @Test
    void shouldPreferSeededStoryWhenAuthoredChoiceMatches() {
        String sessionId = "seed-" + UUID.randomUUID();

        GameResponse response = gameEngine.next(new GameRequest(sessionId, "follow the path forward", ""));

        assertThat(response.storyEvent()).isNotNull();
        assertThat(response.storyEvent().sourceType()).isEqualTo("seed");
        assertThat(response.storyEvent().targetNodeId()).isEqualTo("crossroads.arrival");
        assertThat(response.state().getCurrentNodeId()).isEqualTo("crossroads.arrival");
        assertThat(response.state().getStorySeedNodeId()).isEqualTo("crossroads.arrival");
        assertThat(response.choices()).isNotEmpty();
        assertThat(response.branchOptions()).hasSize(response.choices().size());
        assertThat(response.orchestration().speakingAgentIds()).isNotEmpty();
        assertThat(response.orchestration().planner().sceneGoal()).contains("Moonlit Crossroads");
    }

    @Test
    void shouldCarryAgentPrivateMemoryAcrossTurns() {
        String sessionId = "memory-" + UUID.randomUUID();

        gameEngine.next(new GameRequest(sessionId, "look for a fresh lead with Rowan", ""));
        GameResponse secondTurn = gameEngine.next(new GameRequest(sessionId, "ask Rowan why he hesitates", ""));

        assertThat(secondTurn.agentReplies()).isNotEmpty();
        assertThat(secondTurn.agentReplies().stream().map(reply -> reply.agentId())).contains("rowan");
        assertThat(secondTurn.agentReplies().stream()
                        .filter(reply -> reply.agentId().equals("rowan"))
                        .findFirst()
                        .orElseThrow()
                        .memorySummary())
                .isNotEqualTo("No private memory yet.");
        assertThat(secondTurn.agentReplies().stream()
                        .filter(reply -> reply.agentId().equals("rowan"))
                        .findFirst()
                        .orElseThrow()
                        .message())
                .contains("Current objective");
        assertThat(secondTurn.orchestration().plans().stream()
                        .filter(plan -> plan.agentId().equals("rowan"))
                        .findFirst()
                        .orElseThrow()
                        .scoreFactors())
                .isNotEmpty();
        assertThat(secondTurn.orchestration().critic().verdict()).isNotBlank();
    }

    @Test
    void shouldPersistSandboxPlansInsideSessionExport() {
        String sessionId = "sandbox-" + UUID.randomUUID();

        GameResponse firstTurn = gameEngine.next(new GameRequest(sessionId, "look for a fresh lead with Rowan", ""));
        BranchOptionView firstOption = firstTurn.branchOptions().get(0);

        SessionExportResponse updatedExport = sessionArchiveService.saveSandboxPlan(sessionId, new SandboxPlanRequest(
                firstTurn.state().getCurrentScene(),
                firstTurn.state().getCurrentNodeId(),
                "Sandbox rehearsal",
                "Test branch rehearsal",
                List.of(firstOption),
                firstOption.relationshipDelta(),
                firstOption.addedFlags(),
                Map.of("rowan", 2)));

        assertThat(updatedExport.sandboxPlans()).hasSize(1);
        assertThat(updatedExport.sandboxPlans().get(0).title()).isEqualTo("Sandbox rehearsal");
        assertThat(updatedExport.sandboxPlans().get(0).steps()).hasSize(1);

        gameEngine.next(new GameRequest(sessionId, "ask Rowan why he hesitates", ""));

        SessionExportResponse reloadedExport = sessionArchiveService.getSessionExport(sessionId);
        assertThat(reloadedExport.turns()).hasSize(2);
        assertThat(reloadedExport.sandboxPlans()).hasSize(1);
        assertThat(reloadedExport.sandboxPlans().get(0).summary()).isEqualTo("Test branch rehearsal");
    }
}
