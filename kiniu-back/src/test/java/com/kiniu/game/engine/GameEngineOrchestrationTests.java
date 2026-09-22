package com.kiniu.game.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kiniu.game.dto.BranchOptionView;
import com.kiniu.game.dto.GameRequest;
import com.kiniu.game.dto.GameResponse;
import com.kiniu.game.dto.SandboxPlanRequest;
import com.kiniu.game.dto.SessionExportResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "game.story.catalog-path=target/test-agent-container-story-catalog.json",
        "game.agents.catalog-path=target/test-agent-container-agent-catalog.json",
        "game.sessions.export-path=target/test-agent-container-session-exports"
})
class GameEngineOrchestrationTests {

    @Autowired
    private GameEngine gameEngine;

    @Autowired
    private SessionArchiveService sessionArchiveService;

    @Test
    void shouldGenerateDynamicBeatWhenNoSeedChoiceMatches() {
        String sessionId = "generated-" + UUID.randomUUID();

        GameResponse response = gameEngine.next(new GameRequest(sessionId, "design a custom workflow outside the shipped presets", ""));

        assertThat(response.storyEvent()).isNotNull();
        assertThat(response.storyEvent().sourceType()).isEqualTo("generated");
        assertThat(response.state().getCurrentNodeId()).startsWith("generated.");
        assertThat(response.directorMessage()).contains("这一轮围绕");
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
        assertThat(exportResponse.turns().get(0).directorMessage()).contains("这一轮围绕");
        assertThat(exportResponse.turns().get(0).presentedBranchOptions()).isNotEmpty();
        assertThat(exportResponse.turns().get(0).orchestration()).isNotNull();
        assertThat(exportResponse.turns().get(0).orchestration().critic().notes()).isNotEmpty();
        assertThat(exportResponse.turns().get(0).orchestration().aiInvocations()).isNotEmpty();

        Path exportDirectory = Path.of("target/test-agent-container-session-exports");
        assertThat(Files.exists(exportDirectory.resolve(sessionId + ".session.json"))).isTrue();
        assertThat(Files.exists(exportDirectory.resolve(sessionId + ".turns.jsonl"))).isTrue();
        assertThat(Files.exists(exportDirectory.resolve(sessionId + ".json"))).isFalse();
    }

    @Test
    void shouldMakeThePreferredAgentTheFocusAndAReplyAuthorForAuthoredBeats() {
        String sessionId = "preferred-" + UUID.randomUUID();

        GameResponse response = gameEngine.next(new GameRequest(
                sessionId,
                "",
                "帮我理清今晚",
                "companion"));

        assertThat(response.orchestration().focusAgentId()).isEqualTo("companion");
        assertThat(response.orchestration().spotlightAgentIds()).contains("companion");
        assertThat(response.orchestration().speakingAgentIds()).contains("companion");
        assertThat(response.agentReplies()).extracting(reply -> reply.agentId()).contains("companion");
    }

    @Test
    void shouldPreferSeededStoryWhenAuthoredChoiceMatches() {
        String sessionId = "seed-" + UUID.randomUUID();

        GameResponse response = gameEngine.next(new GameRequest(sessionId, "", "帮我理清今晚"));

        assertThat(response.storyEvent()).isNotNull();
        assertThat(response.storyEvent().sourceType()).isEqualTo("seed");
        assertThat(response.storyEvent().targetNodeId()).isEqualTo("companion.check-in");
        assertThat(response.state().getCurrentNodeId()).isEqualTo("companion.check-in");
        assertThat(response.state().getStorySeedNodeId()).isEqualTo("companion.check-in");
        assertThat(response.choices()).isNotEmpty();
        assertThat(response.branchOptions()).hasSize(response.choices().size());
        assertThat(response.orchestration().speakingAgentIds()).isNotEmpty();
        assertThat(response.message()).contains("不会改日历");
        assertThat(response.message()).doesNotContain("Container framing", "relationship vector", "Current objective");
        assertThat(response.orchestration().planner().sceneGoal()).contains("晚间计划");
    }

    @Test
    void shouldApplyTheLessonSentenceWhenTheEveningChoiceIsAlsoClicked() {
        String sessionId = "practice-" + UUID.randomUUID();

        GameResponse response = gameEngine.next(new GameRequest(
                sessionId,
                "今晚我加班回家了。不要改日历，不要发消息，也不要下单。待办：回客户邮件，买早餐，准备周会。",
                "帮我理清今晚"));

        assertThat(response.message()).contains("回客户邮件", "不要下单");
        assertThat(response.message()).doesNotContain(
                "贴过来",
                "acts as an independent",
                "我可以陪你聊聊今晚",
                "晚间计划助手:");
        assertThat(response.message()).doesNotStartWith("晚间计划");
    }

    @Test
    void shouldCarryAgentPrivateMemoryAcrossTurns() {
        String sessionId = "memory-" + UUID.randomUUID();

        gameEngine.next(new GameRequest(sessionId, "", "帮我理清今晚"));
        GameResponse secondTurn = gameEngine.next(new GameRequest(sessionId, "今晚待办有点乱，帮我排出下一步", ""));

        assertThat(secondTurn.agentReplies()).isNotEmpty();
        assertThat(secondTurn.agentReplies().stream().map(reply -> reply.agentId())).contains("companion");
        assertThat(secondTurn.agentReplies().stream()
                        .filter(reply -> reply.agentId().equals("companion"))
                        .findFirst()
                        .orElseThrow()
                        .memorySummary())
                .isNotEqualTo("No private memory yet.");
        assertThat(secondTurn.agentReplies().stream()
                        .filter(reply -> reply.agentId().equals("companion"))
                        .findFirst()
                        .orElseThrow()
                        .message())
                .contains("三条下一步");
        assertThat(secondTurn.message()).doesNotContain("Current objective", "relationship vector");
        assertThat(secondTurn.orchestration().plans().stream()
                        .filter(plan -> plan.agentId().equals("companion"))
                        .findFirst()
                        .orElseThrow()
                        .scoreFactors())
                .isNotEmpty();
        assertThat(secondTurn.orchestration().critic().verdict()).isNotBlank();
    }

    @Test
    void shouldPersistSandboxPlansInsideSessionExport() {
        String sessionId = "sandbox-" + UUID.randomUUID();

        GameResponse firstTurn = gameEngine.next(new GameRequest(sessionId, "", "帮我理清今晚"));
        BranchOptionView firstOption = firstTurn.branchOptions().get(0);

        SessionExportResponse updatedExport = sessionArchiveService.saveSandboxPlan(sessionId, new SandboxPlanRequest(
                firstTurn.state().getCurrentScene(),
                firstTurn.state().getCurrentNodeId(),
                "Sandbox rehearsal",
                "Test branch rehearsal",
                List.of(firstOption),
                firstOption.relationshipDelta(),
                firstOption.addedFlags(),
                Map.of("companion", 2)));

        assertThat(updatedExport.sandboxPlans()).hasSize(1);
        assertThat(updatedExport.sandboxPlans().get(0).title()).isEqualTo("Sandbox rehearsal");
        assertThat(updatedExport.sandboxPlans().get(0).steps()).hasSize(1);

        gameEngine.next(new GameRequest(sessionId, "继续追问 Java 并发", ""));

        SessionExportResponse reloadedExport = sessionArchiveService.getSessionExport(sessionId);
        assertThat(reloadedExport.turns()).hasSize(2);
        assertThat(reloadedExport.sandboxPlans()).hasSize(1);
        assertThat(reloadedExport.sandboxPlans().get(0).summary()).isEqualTo("Test branch rehearsal");
    }
    @Test
    void shouldRejectUnsafeSessionIdsBeforeWritingArchive() {
        Path exportDirectory = Path.of("target/test-agent-container-session-exports");

        assertThatThrownBy(() -> gameEngine.next(new GameRequest("../escape", "hello", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session id");
        assertThat(Files.exists(exportDirectory.resolve("escape.session.json"))).isFalse();
    }

    @Test
    void shouldPageSessionExports() {
        String sessionId = "paged-" + UUID.randomUUID();

        gameEngine.next(new GameRequest(sessionId, "java rag interview", ""));
        gameEngine.next(new GameRequest(sessionId, "continue with JVM and thread pools", ""));

        SessionExportResponse firstPage = sessionArchiveService.getSessionExport(sessionId, 0, 1);
        assertThat(firstPage.turns()).hasSize(1);
        assertThat(firstPage.turns().get(0).id()).isEqualTo("turn-0");
        assertThat(firstPage.totalTurns()).isEqualTo(2);
        assertThat(firstPage.offset()).isZero();
        assertThat(firstPage.limit()).isEqualTo(1);

        SessionExportResponse secondPage = sessionArchiveService.getSessionExport(sessionId, 1, 1);
        assertThat(secondPage.turns()).hasSize(1);
        assertThat(secondPage.turns().get(0).id()).isEqualTo("turn-1");
        assertThat(secondPage.totalTurns()).isEqualTo(2);
        assertThat(secondPage.offset()).isEqualTo(1);
        assertThat(secondPage.limit()).isEqualTo(1);
    }

    @Test
    void shouldSkipMalformedSessionTurnLines() throws IOException {
        String sessionId = "corrupt-" + UUID.randomUUID();
        Path exportDirectory = Path.of("target/test-agent-container-session-exports");

        gameEngine.next(new GameRequest(sessionId, "java rag interview", ""));
        Files.writeString(
                exportDirectory.resolve(sessionId + ".turns.jsonl"),
                "{not-json}" + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);

        SessionExportResponse exportResponse = sessionArchiveService.getSessionExport(sessionId, 0, 10);

        assertThat(exportResponse.turns()).hasSize(1);
        assertThat(exportResponse.totalTurns()).isEqualTo(1);
    }
}
