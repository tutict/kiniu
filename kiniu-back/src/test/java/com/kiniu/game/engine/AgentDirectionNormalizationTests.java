package com.kiniu.game.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kiniu.game.agent.Agent;
import com.kiniu.game.ai.AIService;
import com.kiniu.game.state.WorldState;
import com.kiniu.game.story.StoryEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentDirectionNormalizationTests {

    @Test
    void plotAgentShouldFilterInvalidChoicesAndUnknownSpotlightIds() {
        AIService aiService = mock(AIService.class);
        PlotAgentService plotAgentService = new PlotAgentService(aiService);
        WorldState state = WorldState.initial();
        state.setCurrentScene("crossroads");
        state.adjustRelationship("rowan", 2, 0, 1);

        Agent rowan = sampleAgent("rowan", "Rowan");
        Agent lyra = sampleAgent("lyra", "Lyra");

        when(aiService.generatePlotBeatDraft(
                anyString(),
                anyString(),
                any(Agent.class),
                any(WorldState.class),
                anyString(),
                anyString(),
                anyList(),
                any(PlotBeatDraft.class)))
                .thenReturn(new PlotBeatDraft(
                        "AI beat",
                        "AI narrative",
                        List.of("  ", "Press Rowan now", "Press Rowan now", "Leave the ridge", "Track the clue"),
                        List.of("ghost", "lyra")));

        StoryEvent result = plotAgentService.resolveStoryBeat(
                state,
                "ask for the truth",
                "",
                Optional.empty(),
                List.of(rowan, lyra));

        assertThat(result.title()).isEqualTo("AI beat");
        assertThat(result.narrative()).isEqualTo("AI narrative");
        assertThat(result.choices()).containsExactly("Press Rowan now", "Leave the ridge", "Track the clue");
        assertThat(result.spotlightAgentIds()).containsExactly("rowan", "lyra");
    }

    @Test
    void directorAgentShouldFallbackToSpeakerAndPruneBlankChoices() {
        AIService aiService = mock(AIService.class);
        DirectorAgentService directorAgentService = new DirectorAgentService(aiService);
        WorldState state = WorldState.initial();
        state.setCurrentScene("crossroads");
        state.setCurrentNodeId("crossroads.arrival");

        Agent rowan = sampleAgent("rowan", "Rowan");
        Agent lyra = sampleAgent("lyra", "Lyra");
        StoryEvent storyBeat = new StoryEvent(
                "evt-1",
                "opening.threshold",
                "crossroads.arrival",
                "crossroads",
                "rowan",
                "Moonlit Crossroads",
                "Rowan watches the fork in silence.",
                List.of("Press harder", "Leave"),
                "generated",
                "",
                List.of("rowan"));

        when(aiService.generateDirectorBeatPlan(
                any(StoryEvent.class),
                any(WorldState.class),
                anyList(),
                anyString(),
                anyString(),
                anyList(),
                any(DirectorBeatPlan.class)))
                .thenReturn(new DirectorBeatPlan(
                        "AI direction",
                        List.of("  ", "Press harder", "Press harder", "Circle behind Rowan"),
                        List.of("ghost", "lyra")));

        StoryEvent directed = directorAgentService.directStoryBeat(
                state,
                storyBeat,
                List.of(rowan, lyra),
                "ask for the truth",
                "",
                List.of("Press harder", "Leave"));

        assertThat(directed.directorSummary()).isEqualTo("AI direction");
        assertThat(directed.choices()).containsExactly("Press harder", "Circle behind Rowan");
        assertThat(directed.spotlightAgentIds()).containsExactly("rowan", "lyra");
    }

    private Agent sampleAgent(String id, String name) {
        return new Agent(
                id,
                name,
                "Scout",
                "A watchful scout.",
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
