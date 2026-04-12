package com.kiniu.game.story;

import com.kiniu.game.agent.Agent;
import com.kiniu.game.dto.AgentCatalogResponse;
import com.kiniu.game.dto.StoryAnalysisResponse;
import com.kiniu.game.dto.StoryCatalogResponse;
import com.kiniu.game.dto.StoryChoiceView;
import com.kiniu.game.dto.StoryGenerationRequest;
import com.kiniu.game.dto.StoryGenerationResponse;
import com.kiniu.game.dto.StoryNodeView;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StoryGeneratorService {

    private final StoryCompilerService storyCompilerService;

    public StoryGeneratorService(StoryCompilerService storyCompilerService) {
        this.storyCompilerService = storyCompilerService;
    }

    public StoryGenerationResponse generate(StoryGenerationRequest request) {
        String premise = fallback(request.premise(), "A player is pulled into a secret struggle around a living relic.");
        String setting = fallback(request.setting(), "ruined harbor district");
        String tone = fallback(request.tone(), "tense mystery");
        String chapterGoal = fallback(request.chapterGoal(), "secure a lead before dawn");
        String protagonistName = fallback(request.protagonistName(), "The Drifter");
        String companionName = fallback(request.companionName(), "Lyra");
        String rivalName = fallback(request.rivalName(), "Rowan");
        String companionId = slug(companionName);
        String rivalId = slug(rivalName);

        StoryCatalogResponse story = new StoryCatalogResponse(
                "opening.arrival",
                List.of(
                        new StoryNodeView(
                                "opening.arrival",
                                "opening",
                                "Arrival at the Threshold",
                                "narrator",
                                protagonistName + " arrives at the " + setting + " chasing a rumor. " + premise
                                        + " The first chapter should end by deciding who controls the next lead.",
                                List.of("entry", "hook"),
                                List.of(),
                                Map.of(),
                                List.of(
                                        choice(
                                                "follow-companion-signal",
                                                "Follow " + companionName + "'s signal",
                                                "Move toward the person who seems to know how the district works.",
                                                "crossroads.signal",
                                                List.of("signal", "follow", companionName.toLowerCase()),
                                                List.of(),
                                                List.of(),
                                                Map.of(),
                                                List.of(),
                                                Map.of()),
                                        choice(
                                                "intercept-rival",
                                                "Intercept " + rivalName,
                                                "Push directly into conflict before the cast settles.",
                                                "crossroads.intercept",
                                                List.of("intercept", "stop", rivalName.toLowerCase()),
                                                List.of(),
                                                List.of(),
                                                Map.of(),
                                                List.of(),
                                                Map.of()),
                                        choice(
                                                "inspect-scene",
                                                "Inspect the scene first",
                                                "Start with evidence before trusting anyone.",
                                                "clue.archive",
                                                List.of("inspect", "study", "clue", "observe"),
                                                List.of(),
                                                List.of(),
                                                Map.of(),
                                                List.of("evidence-found"),
                                                Map.of()))),
                        new StoryNodeView(
                                "crossroads.signal",
                                "signal-crossroads",
                                "Signal at the Crossroads",
                                companionId,
                                companionName + " appears in the haze and offers a shortcut to the chapter goal: " + chapterGoal
                                        + ". The offer feels useful, but not safe.",
                                List.of("hub", "relationship"),
                                List.of("met-" + companionId),
                                Map.of(companionId, 1),
                                List.of(
                                        choice(
                                                "accept-guidance",
                                                "Accept the guidance",
                                                "Trust the companion enough to accelerate the route.",
                                                "alliance.pact",
                                                List.of("trust", "agree", "accept"),
                                                List.of(),
                                                List.of(),
                                                Map.of(),
                                                List.of("trust-offered"),
                                                Map.of(companionId, 1)),
                                        choice(
                                                "question-intent",
                                                "Question the intent",
                                                "Keep the alliance provisional and extract context first.",
                                                "reveal.pressure",
                                                List.of("why", "question", "doubt"),
                                                List.of(),
                                                List.of(),
                                                Map.of(),
                                                List.of(),
                                                Map.of(companionId, -1)))),
                        new StoryNodeView(
                                "crossroads.intercept",
                                "pressure-lane",
                                "Pressure in the Lane",
                                rivalId,
                                rivalName + " is already moving assets through the " + setting
                                        + ". A direct approach turns the opening into a negotiation under pressure.",
                                List.of("conflict", "hub"),
                                List.of("met-" + rivalId),
                                Map.of(rivalId, 1),
                                List.of(
                                        choice(
                                                "force-deal",
                                                "Force a deal",
                                                "Trade information for access to the objective.",
                                                "reveal.pressure",
                                                List.of("deal", "trade", "bargain"),
                                                List.of(),
                                                List.of(),
                                                Map.of(),
                                                List.of("deal-opened"),
                                                Map.of(rivalId, 1)),
                                        choice(
                                                "shadow-rival",
                                                "Shadow the rival",
                                                "Keep distance and gather leverage before speaking.",
                                                "clue.archive",
                                                List.of("shadow", "tail", "follow"),
                                                List.of(),
                                                List.of(),
                                                Map.of(),
                                                List.of("rival-trailed"),
                                                Map.of()))),
                        new StoryNodeView(
                                "clue.archive",
                                "archive-room",
                                "Archive of the Living Relic",
                                "narrator",
                                "Fragments in the archive tie the chapter to a larger system: the relic chooses witnesses, and " + chapterGoal
                                        + " depends on reading its rules before someone weaponizes them.",
                                List.of("lore", "evidence"),
                                List.of("archive-opened"),
                                Map.of(),
                                List.of(
                                        choice(
                                                "bring-proof-to-companion",
                                                "Bring the proof to " + companionName,
                                                "Use evidence to reshape the alliance route.",
                                                "alliance.pact",
                                                List.of("proof", companionName.toLowerCase(), "share"),
                                                List.of(),
                                                List.of(),
                                                Map.of(),
                                                List.of("proof-shared"),
                                                Map.of(companionId, 1)),
                                        choice(
                                                "weaponize-proof",
                                                "Use the proof against " + rivalName,
                                                "Convert evidence into pressure.",
                                                "reveal.pressure",
                                                List.of("weaponize", "pressure", rivalName.toLowerCase()),
                                                List.of(),
                                                List.of(),
                                                Map.of(),
                                                List.of("proof-weaponized"),
                                                Map.of(rivalId, -1)))),
                        new StoryNodeView(
                                "alliance.pact",
                                "whispering-yard",
                                "Provisional Pact",
                                companionId,
                                companionName + " agrees to move with " + protagonistName
                                        + ", but only if the player commits to a path. This node pivots from social trust into tactical consequence.",
                                List.of("relationship", "midpoint"),
                                List.of("allied-" + companionId),
                                Map.of(companionId, 1),
                                List.of(
                                        choice(
                                                "stage-decoy",
                                                "Stage a decoy route",
                                                "Turn the chapter into a tactical setup.",
                                                "ending.decoy",
                                                List.of("decoy", "plan", "route"),
                                                List.of(),
                                                List.of(),
                                                Map.of(),
                                                List.of("decoy-staged"),
                                                Map.of()),
                                        choice(
                                                "push-for-truth",
                                                "Push for the hidden truth",
                                                "Force the companion to explain what is still being concealed.",
                                                "reveal.pressure",
                                                List.of("truth", "reveal", "hidden"),
                                                List.of(),
                                                List.of(),
                                                Map.of(),
                                                List.of(),
                                                Map.of(companionId, -1)))),
                        new StoryNodeView(
                                "reveal.pressure",
                                "fracture-point",
                                "Reveal Under Pressure",
                                rivalId,
                                rivalName + " reveals that the real contest is not possession but authorship: whoever frames the relic first controls the meaning of the next act.",
                                List.of("reveal", "midpoint"),
                                List.of("pressure-reveal"),
                                Map.of(),
                                List.of(
                                        choice(
                                                "close-chapter-with-alliance",
                                                "Close the chapter with an uneasy alliance",
                                                "Land on a social cliffhanger.",
                                                "ending.handoff",
                                                List.of("alliance", "uneasy", "together"),
                                                List.of(),
                                                List.of(),
                                                Map.of(),
                                                List.of("chapter-alliance"),
                                                Map.of(companionId, 1, rivalId, 1)),
                                        choice(
                                                "close-chapter-with-confrontation",
                                                "Close the chapter with confrontation",
                                                "Land on a sharper break before the next act.",
                                                "ending.decoy",
                                                List.of("fight", "confront", "break"),
                                                List.of(),
                                                List.of(),
                                                Map.of(),
                                                List.of("chapter-break"),
                                                Map.of(rivalId, -1)))),
                        new StoryNodeView(
                                "ending.decoy",
                                "dock-rim",
                                "Decoy Window",
                                "narrator",
                                "The decoy works just long enough to move the chapter into its handoff. The cast has shape, the relic has a rule set, and the next act has a clear tactical opening.",
                                List.of("chapter-end", "handoff"),
                                List.of(),
                                Map.of(),
                                List.of()),
                        new StoryNodeView(
                                "ending.handoff",
                                "threshold-room",
                                "Handoff at the Threshold",
                                companionId,
                                companionName + " accepts the temporary alliance, but the cost is explicit: the next act will demand a choice between speed, trust, and control.",
                                List.of("chapter-end", "handoff"),
                                List.of(),
                                Map.of(),
                                List.of())));

        AgentCatalogResponse agents = new AgentCatalogResponse(List.of(
                narratorAgent(setting, tone),
                characterAgent(
                        companionId,
                        companionName,
                        "companion",
                        "A field partner who understands the local rules better than they admit.",
                        tone + ", selective honesty",
                        List.of("signal-crossroads", "whispering-yard", "threshold-room"),
                        List.of(
                                "Pull the player toward the chapter goal without becoming obedient",
                                "Reward trust with partial clarity",
                                "Keep one hidden agenda active")),
                characterAgent(
                        rivalId,
                        rivalName,
                        "rival",
                        "A poised rival who competes through framing, leverage, and timing.",
                        "cool, strategic, provocative",
                        List.of("pressure-lane", "fracture-point", "dock-rim"),
                        List.of(
                                "Force the player to commit to a legible position",
                                "Exploit any ambiguity in the alliance",
                                "Convert information into control"))));

        StoryAnalysisResponse analysis = storyCompilerService.analyze(story, agents);
        String summary = "Generated a starter chapter from the premise with "
                + story.nodes().size() + " nodes, "
                + agents.agents().size() + " agents, and goal \"" + chapterGoal + "\".";

        return new StoryGenerationResponse(story, agents, analysis, summary);
    }

    private StoryChoiceView choice(
            String id,
            String label,
            String description,
            String targetNodeId,
            List<String> keywords,
            List<String> requiredFlags,
            List<String> blockedFlags,
            Map<String, Integer> minimumAffinity,
            List<String> flagsToAdd,
            Map<String, Integer> affinityChanges) {
        return new StoryChoiceView(
                id,
                label,
                description,
                targetNodeId,
                requiredFlags,
                blockedFlags,
                minimumAffinity,
                keywords,
                flagsToAdd,
                affinityChanges);
    }

    private Agent narratorAgent(String setting, String tone) {
        return new Agent(
                "narrator",
                "Narrator",
                "director",
                "Frames the " + setting + " and keeps authored and generated beats coherent.",
                "measured, cinematic, " + tone,
                "Maintain chapter pacing, preserve legibility, and surface the next playable branch.",
                List.of("opening", "archive-room", "dock-rim"),
                Map.of("tone", tone, "mode", "chapter-director"),
                List.of(
                        "Keep the player's options legible",
                        "Bridge authored seeds and dynamic improvisation",
                        "Protect chapter pacing"),
                List.of("Measure whether the current cast can sustain another act."),
                10,
                "omniscient");
    }

    private Agent characterAgent(
            String id,
            String name,
            String role,
            String summary,
            String personality,
            List<String> activeScenes,
            List<String> coreGoals) {
        return new Agent(
                id,
                name,
                role,
                summary,
                personality,
                "Respond as an independent AVG character with memory, leverage, and scene intent.",
                activeScenes,
                Map.of("tone", personality, "focus", role),
                coreGoals,
                List.of("Keep one emotionally expensive truth hidden until it matters."),
                7,
                "episodic");
    }

    private String slug(String value) {
        return fallback(value, "character")
                .trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
