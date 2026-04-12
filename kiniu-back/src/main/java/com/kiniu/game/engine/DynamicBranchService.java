package com.kiniu.game.engine;

import com.kiniu.game.agent.Agent;
import com.kiniu.game.state.RelationshipState;
import com.kiniu.game.state.WorldState;
import com.kiniu.game.story.StoryEvent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DynamicBranchService {

    public List<String> generateChoices(
            WorldState state,
            StoryEvent storyBeat,
            List<Agent> activeAgents) {
        Set<String> generatedChoices = new LinkedHashSet<>(storyBeat.choices());

        if (generatedChoices.isEmpty()) {
            generatedChoices.add("Pause and observe how the scene responds");
        }
        if (storyBeat.isGenerated()) {
            generatedChoices.add("Let the generated branch sharpen into a new lead");
        }

        for (Agent agent : activeAgents) {
            if ("narrator".equals(agent.id())) {
                continue;
            }

            RelationshipState relationshipState = state.getRelationship(agent.id());
            generatedChoices.add("Ask " + agent.name() + " what they notice");

            if (relationshipState.getTrust() >= 2) {
                generatedChoices.add("Trust " + agent.name() + " to guide the next move");
            }
            if (relationshipState.getAffection() >= 2) {
                generatedChoices.add("Stay close to " + agent.name());
            }
            if (relationshipState.getCuriosity() >= 2) {
                generatedChoices.add("Challenge " + agent.name() + " to reveal more");
            }
        }

        if (state.getFlags().contains("session-started")) {
            generatedChoices.add("Inspect the shifting atmosphere");
        }
        if (state.getFlags().contains("generated-plot-active")) {
            generatedChoices.add("Anchor this dynamic beat into the authored seed graph");
        }
        if (generatedChoices.size() > 7) {
            return generatedChoices.stream().limit(7).toList();
        }
        return List.copyOf(generatedChoices);
    }
}
