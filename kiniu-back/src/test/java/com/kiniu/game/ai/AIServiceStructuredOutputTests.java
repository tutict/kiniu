package com.kiniu.game.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.kiniu.game.agent.Agent;
import com.kiniu.game.agent.AgentTurnPlan;
import com.kiniu.game.dto.AgentReplyView;
import com.kiniu.game.engine.BranchOption;
import com.kiniu.game.engine.DirectorBeatPlan;
import com.kiniu.game.engine.PlotBeatDraft;
import com.kiniu.game.engine.TurnCritiqueSummary;
import com.kiniu.game.engine.TurnPlannerBrief;
import com.kiniu.game.state.WorldState;
import com.kiniu.game.story.StoryEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AIServiceStructuredOutputTests {

    @Test
    void shouldParseStructuredPlannerBriefFromProviderOutput() throws Exception {
        AIRuntimeContext runtimeContext = new AIRuntimeContext();
        runtimeContext.set(new AIRequestConfig("https://example.com/v1", "test-key", "test-model"));
        OpenAICompatibleClient client = mock(OpenAICompatibleClient.class);
        AITelemetryCollector telemetryCollector = new AITelemetryCollector();
        AIService aiService = new AIService(runtimeContext, client, telemetryCollector, new ObjectMapper());

        when(client.complete(any(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("""
                        ```json
                        {
                          "sceneGoal": "Push Rowan into a confession beat.",
                          "tensionLabel": "volatile",
                          "pacingLabel": "measured",
                          "directorIntent": "Keep Rowan cornered while preserving player agency.",
                          "risks": ["Too much exposition could flatten the suspense."]
                        }
                        ```
                        """);

        TurnPlannerBrief fallback = new TurnPlannerBrief(
                "fallback scene goal",
                "focused",
                "measured",
                "fallback director intent",
                List.of("fallback risk"));

        TurnPlannerBrief result = aiService.generatePlannerBrief(
                sampleWorldState(),
                sampleStoryEvent(),
                List.of(sampleAgent()),
                "ask Rowan for the truth",
                "",
                List.of("Press harder", "Change the subject"),
                fallback);

        assertThat(result.sceneGoal()).isEqualTo("Push Rowan into a confession beat.");
        assertThat(result.tensionLabel()).isEqualTo("volatile");
        assertThat(result.risks()).containsExactly("Too much exposition could flatten the suspense.");
        assertThat(telemetryCollector.snapshot()).singleElement().satisfies(telemetry -> {
            assertThat(telemetry.operation()).isEqualTo("planner-brief");
            assertThat(telemetry.providerAttempted()).isTrue();
            assertThat(telemetry.providerSucceeded()).isTrue();
            assertThat(telemetry.fallbackUsed()).isFalse();
        });
    }

    @Test
    void shouldFallbackWhenCriticOutputIsInvalidJson() throws Exception {
        AIRuntimeContext runtimeContext = new AIRuntimeContext();
        runtimeContext.set(new AIRequestConfig("https://example.com/v1", "test-key", "test-model"));
        OpenAICompatibleClient client = mock(OpenAICompatibleClient.class);
        AITelemetryCollector telemetryCollector = new AITelemetryCollector();
        AIService aiService = new AIService(runtimeContext, client, telemetryCollector, new ObjectMapper());

        when(client.complete(any(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("{\"verdict\":\"stable\",\"focusScore\":99}");

        TurnCritiqueSummary fallback = new TurnCritiqueSummary(
                "usable",
                7,
                6,
                4,
                List.of("Fallback critic note."));

        TurnCritiqueSummary result = aiService.generateCritiqueSummary(
                sampleStoryEvent(),
                List.of("Press harder", "Leave"),
                List.of(new AgentTurnPlan(sampleAgent(), "Hold the line", "Memory anchor", 8, true, List.of())),
                List.of(new AgentReplyView("rowan", "Rowan", "Scout", "Hold the line", "Memory anchor", 8, "I need more time.")),
                fallback);

        assertThat(result).isEqualTo(fallback);
        assertThat(telemetryCollector.snapshot()).singleElement().satisfies(telemetry -> {
            assertThat(telemetry.operation()).isEqualTo("critic-summary");
            assertThat(telemetry.providerAttempted()).isTrue();
            assertThat(telemetry.providerSucceeded()).isFalse();
            assertThat(telemetry.fallbackUsed()).isTrue();
            assertThat(telemetry.errorMessage()).isNotBlank();
        });
    }

    @Test
    void shouldParseStructuredPlotBeatDraftFromProviderOutput() throws Exception {
        AIRuntimeContext runtimeContext = new AIRuntimeContext();
        runtimeContext.set(new AIRequestConfig("https://example.com/v1", "test-key", "test-model"));
        OpenAICompatibleClient client = mock(OpenAICompatibleClient.class);
        AITelemetryCollector telemetryCollector = new AITelemetryCollector();
        AIService aiService = new AIService(runtimeContext, client, telemetryCollector, new ObjectMapper());

        when(client.complete(any(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("""
                        {
                          "title": "A Sudden Disclosure",
                          "narrative": "Rowan finally lets one detail slip. The crossroads stops feeling neutral and starts feeling watched.",
                          "choices": ["Press Rowan now", "Step back and study the scene", "Mark the clue for later"],
                          "spotlightAgentIds": ["rowan"]
                        }
                        """);

        PlotBeatDraft fallback = new PlotBeatDraft(
                "Fallback title",
                "Fallback narrative",
                List.of("Fallback choice"),
                List.of("rowan"));

        PlotBeatDraft result = aiService.generatePlotBeatDraft(
                "discovery",
                "crossroads",
                sampleAgent(),
                sampleWorldState(),
                "ask Rowan for the truth",
                "",
                List.of("Press harder", "Leave"),
                fallback);

        assertThat(result.title()).isEqualTo("A Sudden Disclosure");
        assertThat(result.choices()).containsExactly("Press Rowan now", "Step back and study the scene", "Mark the clue for later");
        assertThat(result.spotlightAgentIds()).containsExactly("rowan");
    }

    @Test
    void shouldFallbackDirectorBeatPlanWhenProviderOmitsRequiredArrays() throws Exception {
        AIRuntimeContext runtimeContext = new AIRuntimeContext();
        runtimeContext.set(new AIRequestConfig("https://example.com/v1", "test-key", "test-model"));
        OpenAICompatibleClient client = mock(OpenAICompatibleClient.class);
        AITelemetryCollector telemetryCollector = new AITelemetryCollector();
        AIService aiService = new AIService(runtimeContext, client, telemetryCollector, new ObjectMapper());

        when(client.complete(any(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("""
                        {
                          "directorSummary": "Keep Rowan under pressure."
                        }
                        """);

        DirectorBeatPlan fallback = new DirectorBeatPlan(
                "Fallback directing note.",
                List.of("Press harder", "Leave"),
                List.of("rowan"));

        DirectorBeatPlan result = aiService.generateDirectorBeatPlan(
                sampleStoryEvent(),
                sampleWorldState(),
                List.of(sampleAgent()),
                "ask Rowan for the truth",
                "",
                List.of("Press harder", "Leave"),
                fallback);

        assertThat(result).isEqualTo(fallback);
        assertThat(telemetryCollector.snapshot()).singleElement().satisfies(telemetry -> {
            assertThat(telemetry.operation()).isEqualTo("director-summary");
            assertThat(telemetry.providerSucceeded()).isFalse();
            assertThat(telemetry.fallbackUsed()).isTrue();
        });
    }

    @Test
    void shouldParseStructuredBranchOptionsFromProviderOutput() throws Exception {
        AIRuntimeContext runtimeContext = new AIRuntimeContext();
        runtimeContext.set(new AIRequestConfig("https://example.com/v1", "test-key", "test-model"));
        OpenAICompatibleClient client = mock(OpenAICompatibleClient.class);
        AITelemetryCollector telemetryCollector = new AITelemetryCollector();
        AIService aiService = new AIService(runtimeContext, client, telemetryCollector, new ObjectMapper());

        when(client.complete(any(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("""
                        {
                          "branchOptions": [
                            {
                              "label": "Press harder",
                              "intent": "pressure",
                              "risk": "high",
                              "targetMood": "tense",
                              "targetAgentId": "rowan",
                              "consequenceSummary": "This should force Rowan to react immediately.",
                              "relationshipDelta": -1,
                              "addedFlags": ["tension-raised"],
                              "removedFlags": []
                            }
                          ]
                        }
                        """);

        List<BranchOption> fallback = List.of(
                new BranchOption("Press harder", "pressure", "high", "tense", "rowan", "Fallback summary", 0, List.of(), List.of(), "heuristic"));

        List<BranchOption> result = aiService.generateBranchOptions(
                sampleStoryEvent(),
                List.of(sampleAgent()),
                "ask Rowan for the truth",
                "",
                List.of("Press harder"),
                fallback);

        assertThat(result).containsExactly(
                new BranchOption(
                        "Press harder",
                        "pressure",
                        "high",
                        "tense",
                        "rowan",
                        "This should force Rowan to react immediately.",
                        -1,
                        List.of("tension-raised"),
                        List.of(),
                        "ai"));
    }

    @Test
    void shouldSpeakPlainChineseWhenProviderIsMissing() {
        AIRuntimeContext runtimeContext = new AIRuntimeContext();
        OpenAICompatibleClient client = mock(OpenAICompatibleClient.class);
        AIService aiService = new AIService(runtimeContext, client, new AITelemetryCollector(), new ObjectMapper());
        Agent companion = new Agent(
                "companion",
                "晚间计划助手",
                "companion",
                "把乱成一团的今晚收成最多三条下一步。",
                "warm",
                "用中文简短回答。",
                List.of("companion-check-in"),
                Map.of(),
                List.of("Help plan tonight"),
                List.of(),
                7,
                "emotional");
        AgentTurnPlan plan = new AgentTurnPlan(companion, "plan tonight", "none", 7, true, List.of());
        StoryEvent beat = new StoryEvent(
                "evt-evening",
                "container.home",
                "companion.check-in",
                "companion-check-in",
                "companion",
                "晚间计划",
                "我不会改日历，也不会给同事发消息。",
                List.of("接着聊今晚"),
                "seed",
                "Keep it short.",
                List.of("companion"));
        WorldState state = WorldState.initial();
        state.setCurrentScene("companion-check-in");
        state.setCurrentNodeId("companion.check-in");

        String reply = aiService.generateReply(
                companion,
                plan,
                "请按刚才定下的边界，帮我安排今晚。",
                "帮我理清今晚",
                state,
                List.of(),
                beat);

        assertThat(reply).contains("三条下一步").contains("不会改日历");
        assertThat(reply).doesNotContain("relationship vector", "Agent container", "Current objective", "Fallback local");
    }

    @Test
    void shouldAnswerTheLessonSentenceInsteadOfAskingForItAgain() {
        AIRuntimeContext runtimeContext = new AIRuntimeContext();
        OpenAICompatibleClient client = mock(OpenAICompatibleClient.class);
        AIService aiService = new AIService(runtimeContext, client, new AITelemetryCollector(), new ObjectMapper());
        Agent companion = new Agent(
                "companion",
                "晚间计划助手",
                "companion",
                "把乱成一团的今晚收成最多三条下一步。",
                "warm",
                "用中文简短回答。",
                List.of("companion-check-in"),
                Map.of(),
                List.of("Help plan tonight"),
                List.of(),
                7,
                "emotional");
        AgentTurnPlan plan = new AgentTurnPlan(companion, "plan tonight", "none", 7, true, List.of());
        StoryEvent beat = new StoryEvent(
                "evt-evening",
                "container.home",
                "companion.check-in",
                "companion-check-in",
                "companion",
                "晚间计划",
                "我不会改日历，也不会给同事发消息。",
                List.of("接着聊今晚"),
                "seed",
                "Keep it short.",
                List.of("companion"));
        WorldState state = WorldState.initial();
        state.setCurrentScene("companion-check-in");
        state.setCurrentNodeId("companion.check-in");

        String reply = aiService.generateReply(
                companion,
                plan,
                "今晚我加班回家了。不要改日历，不要发消息，也不要下单。待办：回客户邮件，买早餐，准备周会。",
                "帮我理清今晚",
                state,
                List.of(),
                beat);

        assertThat(reply).contains("回客户邮件", "买早餐", "准备周会", "不要下单");
        assertThat(reply).doesNotContain("贴过来");
    }

    private WorldState sampleWorldState() {
        WorldState state = WorldState.initial();
        state.setCurrentScene("crossroads");
        state.setCurrentNodeId("crossroads.arrival");
        state.addFlag("generated-plot-active");
        state.adjustRelationship("rowan", 1, 0, 2);
        return state;
    }

    private StoryEvent sampleStoryEvent() {
        return new StoryEvent(
                "evt-1",
                "opening.threshold",
                "crossroads.arrival",
                "crossroads",
                "rowan",
                "Moonlit Crossroads",
                "Rowan watches the fork in silence.",
                List.of("Press harder", "Leave"),
                "generated",
                "Keep the conversation tight.",
                List.of("rowan"));
    }

    private Agent sampleAgent() {
        return new Agent(
                "rowan",
                "Rowan",
                "Scout",
                "A cautious scout guarding what he knows.",
                "guarded",
                "Stay measured.",
                List.of("crossroads"),
                Map.of(),
                List.of("Protect the route"),
                List.of("Hide the true threat"),
                8,
                "episodic");
    }
}
