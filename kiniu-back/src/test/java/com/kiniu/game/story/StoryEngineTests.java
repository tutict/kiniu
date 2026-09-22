package com.kiniu.game.story;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
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
    void shouldExposeContainerModesForFreshState() {
        StoryEngine storyEngine = createStoryEngine();
        WorldState state = WorldState.initial();

        List<String> choices = storyEngine.getDefaultChoices(state);

        assertThat(choices).contains(
                "随便聊聊",
                "帮我理清今晚",
                "先查资料再回答",
                "帮我推进一件事",
                "写作教练");
    }

    @Test
    void shouldRouteEveningPlanIntoCompanion() {
        StoryEngine storyEngine = createStoryEngine();
        WorldState state = WorldState.initial();

        storyEngine.findTriggeredEvent(state, "", "帮我理清今晚");

        assertThat(state.getFlags()).contains("mode-companion");
        assertThat(state.getCurrentNodeId()).isEqualTo("companion.check-in");
        assertThat(storyEngine.getDefaultChoices(state)).contains("接着聊今晚", "帮我排出下一步");
    }

    @Test
    void shouldRouteThePreparedEveningStarterIntoCompanion() {
        StoryEngine storyEngine = createStoryEngine();
        WorldState state = WorldState.initial();

        storyEngine.findTriggeredEvent(
                state,
                "请按刚才定下的边界，帮我安排今晚：最多 3 条下一步，标出不确定的地方。不要改日历。",
                "");

        assertThat(state.getCurrentNodeId()).isEqualTo("companion.check-in");
        assertThat(state.getFlags()).contains("mode-companion");
    }

    @Test
    void shouldKeepInterviewOffTheOrdinaryHome() {
        StoryEngine storyEngine = createStoryEngine();
        WorldState state = WorldState.initial();

        List<String> choices = storyEngine.getDefaultChoices(state);

        assertThat(choices).doesNotContain("Java/RAG 面试考查");
        assertThat(choices).contains("随便聊聊", "帮我理清今晚", "写作教练");
        assertThat(storyEngine.getStoryCatalog().nodes().stream().map(node -> node.id()))
                .contains("interview.java-rag");
    }

    @Test
    void shouldRouteBetweenKnowledgeAndReviewFlows() {
        StoryEngine storyEngine = createStoryEngine();
        WorldState state = WorldState.initial();

        storyEngine.findTriggeredEvent(state, "", "先查资料再回答");
        storyEngine.findTriggeredEvent(state, "", "资料还缺什么");

        assertThat(state.getFlags()).contains("mode-knowledge");
        assertThat(state.getCurrentNodeId()).isEqualTo("learning.review");
        assertThat(storyEngine.getDefaultChoices(state)).contains("再收紧一点", "换一件别的事");
    }

    @Test
    void shouldExposeStoryCatalogForAuthoringTools() {
        StoryEngine storyEngine = createStoryEngine();
        StoryCatalogResponse catalog = storyEngine.getStoryCatalog();

        assertThat(catalog.entryNodeId()).isEqualTo("container.home");
        assertThat(catalog.nodes()).hasSizeGreaterThanOrEqualTo(10);
        assertThat(catalog.nodes().stream().map(node -> node.id()))
                .contains("interview.java-rag", "knowledge.qa", "workspace.project");
    }

    @Test
    void shouldPersistSavedStoryCatalog() {
        StoryEngine storyEngine = createStoryEngine();
        StoryCatalogResponse catalog = storyEngine.getStoryCatalog();
        StoryCatalogResponse updatedCatalog = new StoryCatalogResponse(
                "container.home",
                catalog.nodes().stream()
                        .map(node -> node.id().equals("container.home")
                                ? new com.kiniu.game.dto.StoryNodeView(
                                        node.id(),
                                        node.sceneId(),
                                        "Edited Container Hub",
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
                .filter(node -> node.id().equals("container.home"))
                .findFirst()
                .orElseThrow()
                .title()).isEqualTo("Edited Container Hub");
    }
}
