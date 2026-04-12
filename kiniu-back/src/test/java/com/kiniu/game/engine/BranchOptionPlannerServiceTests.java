package com.kiniu.game.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kiniu.game.agent.Agent;
import com.kiniu.game.ai.AIService;
import com.kiniu.game.story.StoryEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BranchOptionPlannerServiceTests {

    @Test
    void shouldAlignAiAnnotationsBackToPresentedChoices() {
        AIService aiService = mock(AIService.class);
        BranchOptionPlannerService service = new BranchOptionPlannerService(aiService);
        StoryEvent storyEvent = sampleStoryEvent();
        Agent rowan = sampleAgent("rowan", "Rowan");
        Agent lyra = sampleAgent("lyra", "Lyra");

        when(aiService.generateBranchOptions(
                any(StoryEvent.class),
                anyList(),
                anyString(),
                anyString(),
                anyList(),
                anyList()))
                .thenReturn(List.of(
                        new BranchOption("PRESS HARDER", "pressure", "high", "tense", "rowan", "Force Rowan into a sharper reaction.", -1, List.of("tension-raised"), List.of(), "ai"),
                        new BranchOption("Invented path", "pivot", "medium", "volatile", "ghost", "Unused option.", 0, List.of(), List.of(), "ai"),
                        new BranchOption("Leave", "", "extreme", "", "ghost", "", 9, null, null, "ai")));

        List<BranchOption> result = service.annotateChoices(
                storyEvent,
                List.of(rowan, lyra),
                "ask Rowan for the truth",
                "",
                List.of("Press harder", "Leave"));

        assertThat(result).containsExactly(
                new BranchOption(
                        "Press harder",
                        "pressure",
                        "high",
                        "tense",
                        "rowan",
                        "Force Rowan into a sharper reaction.",
                        -1,
                        List.of("tension-raised"),
                        List.of(),
                        "ai"),
                new BranchOption(
                        "Leave",
                        "withdraw",
                        "medium",
                        "cautious",
                        "rowan",
                        "This branch should lower immediate pressure while preserving later options.",
                        -1,
                        List.of(),
                        List.of("tension-raised"),
                        "ai"));
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
