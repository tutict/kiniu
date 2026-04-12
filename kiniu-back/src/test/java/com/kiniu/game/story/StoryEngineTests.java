package com.kiniu.game.story;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiniu.game.dto.StoryCatalogResponse;
import com.kiniu.game.state.WorldState;
import java.util.List;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoryEngineTests {

    @TempDir
    Path tempDir;

    private StoryEngine createStoryEngine() {
        return new StoryEngine(new ObjectMapper(), tempDir.resolve("story-catalog.json"));
    }

    @Test
    void shouldExposeEntryChoicesForFreshState() {
        StoryEngine storyEngine = createStoryEngine();
        WorldState state = WorldState.initial();

        List<String> choices = storyEngine.getDefaultChoices(state);

        assertThat(choices).containsExactly(
                "沿着脚印前进",
                "调查远处灯光",
                "留在原地倾听");
    }

    @Test
    void shouldUnlockWarningBranchAfterOverhearingHunters() {
        StoryEngine storyEngine = createStoryEngine();
        WorldState state = WorldState.initial();

        storyEngine.findTriggeredEvent(state, "", "留在原地倾听");
        storyEngine.findTriggeredEvent(state, "", "转回岔路口");

        List<String> choices = storyEngine.getDefaultChoices(state);

        assertThat(state.getFlags()).contains("overheard-hunters");
        assertThat(choices).contains("提起猎人的动向");
    }

    @Test
    void shouldRequireLanternAndAffinityBeforeVaultBranchAppears() {
        StoryEngine storyEngine = createStoryEngine();
        WorldState state = WorldState.initial();

        storyEngine.findTriggeredEvent(state, "", "沿着脚印前进");
        assertThat(storyEngine.getDefaultChoices(state)).doesNotContain("要求 Lyra 揭示圣物位置");

        WorldState preparedState = WorldState.initial();
        storyEngine.findTriggeredEvent(preparedState, "", "调查远处灯光");
        storyEngine.findTriggeredEvent(preparedState, "", "带着符文去找 Lyra");

        List<String> crossroadsChoices = storyEngine.getDefaultChoices(preparedState);
        assertThat(crossroadsChoices).contains("要求 Lyra 揭示圣物位置");

        storyEngine.findTriggeredEvent(preparedState, "", "告诉 Lyra 你愿意合作");
        assertThat(storyEngine.getDefaultChoices(preparedState)).contains("请求进入密库");
    }

    @Test
    void shouldExposeStoryCatalogForAuthoringTools() {
        StoryEngine storyEngine = createStoryEngine();
        StoryCatalogResponse catalog = storyEngine.getStoryCatalog();

        assertThat(catalog.entryNodeId()).isEqualTo("opening.threshold");
        assertThat(catalog.nodes()).hasSizeGreaterThanOrEqualTo(10);
        assertThat(catalog.nodes().stream().map(node -> node.id()))
                .contains("vault.threshold", "grove.alliance", "archive.notes");
    }

    @Test
    void shouldPersistSavedStoryCatalog() {
        StoryEngine storyEngine = createStoryEngine();
        StoryCatalogResponse catalog = storyEngine.getStoryCatalog();
        StoryCatalogResponse updatedCatalog = new StoryCatalogResponse(
                "opening.threshold",
                catalog.nodes().stream()
                        .map(node -> node.id().equals("opening.threshold")
                                ? new com.kiniu.game.dto.StoryNodeView(
                                        node.id(),
                                        node.sceneId(),
                                        "Edited Threshold",
                                        node.speakerId(),
                                        node.narrative(),
                                        node.tags(),
                                        node.enterFlags(),
                                        node.enterAffinityChanges(),
                                        node.choices())
                                : node)
                        .toList());

        storyEngine.saveStoryCatalog(updatedCatalog);

        StoryEngine reloadedEngine = createStoryEngine();
        assertThat(reloadedEngine.getStoryCatalog().nodes().stream()
                .filter(node -> node.id().equals("opening.threshold"))
                .findFirst()
                .orElseThrow()
                .title()).isEqualTo("Edited Threshold");
    }
}
