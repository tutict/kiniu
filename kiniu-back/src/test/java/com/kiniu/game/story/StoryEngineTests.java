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
    void shouldExposeContainerModesForFreshState() {
        StoryEngine storyEngine = createStoryEngine();
        WorldState state = WorldState.initial();

        List<String> choices = storyEngine.getDefaultChoices(state);

        assertThat(choices).containsExactly(
                "自由陪聊",
                "Java/RAG 面试考查",
                "知识库问答",
                "项目助理",
                "写作教练");
    }

    @Test
    void shouldRouteIntoJavaRagInterviewMode() {
        StoryEngine storyEngine = createStoryEngine();
        WorldState state = WorldState.initial();

        storyEngine.findTriggeredEvent(state, "", "Java/RAG 面试考查");

        List<String> choices = storyEngine.getDefaultChoices(state);

        assertThat(state.getFlags()).contains("mode-interview");
        assertThat(state.getCurrentNodeId()).isEqualTo("interview.java-rag");
        assertThat(state.getAffinity("java-rag-interviewer")).isGreaterThan(0);
        assertThat(choices).contains("先问 Java 基础", "切到 RAG 架构", "总结薄弱点");
    }

    @Test
    void shouldRouteBetweenKnowledgeAndReviewFlows() {
        StoryEngine storyEngine = createStoryEngine();
        WorldState state = WorldState.initial();

        storyEngine.findTriggeredEvent(state, "", "知识库问答");
        storyEngine.findTriggeredEvent(state, "", "整理知识包缺口");

        assertThat(state.getFlags()).contains("mode-knowledge");
        assertThat(state.getCurrentNodeId()).isEqualTo("learning.review");
        assertThat(storyEngine.getDefaultChoices(state)).contains("继续复盘", "回到 Agent 容器");
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
