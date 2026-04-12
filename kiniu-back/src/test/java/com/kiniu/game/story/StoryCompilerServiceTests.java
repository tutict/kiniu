package com.kiniu.game.story;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiniu.game.dto.StoryAnalysisResponse;
import com.kiniu.game.dto.StoryCatalogResponse;
import com.kiniu.game.dto.StoryChoiceView;
import com.kiniu.game.dto.StoryGenerationRequest;
import com.kiniu.game.dto.StoryGenerationResponse;
import com.kiniu.game.dto.StoryNodeView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoryCompilerServiceTests {

    private final StoryCompilerService storyCompilerService = new StoryCompilerService();
    private final StoryGeneratorService storyGeneratorService = new StoryGeneratorService(storyCompilerService);

    @Test
    void shouldReportMissingTargetsAndUnreachableNodes() {
        StoryCatalogResponse catalog = new StoryCatalogResponse(
                "entry",
                List.of(
                        node(
                                "entry",
                                List.of(new StoryChoiceView(
                                        "go-missing",
                                        "Go",
                                        "Missing target",
                                        "missing-node",
                                        List.of(),
                                        List.of(),
                                        Map.of(),
                                        List.of("go"),
                                        List.of(),
                                        Map.of()))),
                        node("isolated", List.of())));

        StoryAnalysisResponse analysis = storyCompilerService.analyze(catalog);

        assertThat(analysis.errorCount()).isGreaterThanOrEqualTo(1);
        assertThat(analysis.warningCount()).isGreaterThanOrEqualTo(1);
        assertThat(analysis.unreachableNodeIds()).contains("isolated");
        assertThat(analysis.issues().stream().map(issue -> issue.code()))
                .contains("choice.target.missing", "node.unreachable");
    }

    @Test
    void shouldGenerateStarterStoryAndAgentsFromPrompt() {
        StoryGenerationResponse response = storyGeneratorService.generate(new StoryGenerationRequest(
                "The relic can rewrite witness testimony.",
                "flooded observatory",
                "melancholic conspiracy",
                "win access to the observatory core",
                "Mira",
                "Sera",
                "Vale"));

        assertThat(response.story().entryNodeId()).isEqualTo("opening.arrival");
        assertThat(response.story().nodes()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(response.agents().agents().stream().map(agent -> agent.id()))
                .contains("narrator", "sera", "vale");
        assertThat(response.analysis().errorCount()).isZero();
        assertThat(response.summary()).contains("Generated a starter chapter");
    }

    private StoryNodeView node(String id, List<StoryChoiceView> choices) {
        return new StoryNodeView(
                id,
                "scene-" + id,
                "Title " + id,
                "narrator",
                "Narrative " + id,
                List.of("draft"),
                List.of(),
                Map.of(),
                choices);
    }
}
