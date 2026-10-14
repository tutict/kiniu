package com.kiniu.game.engine;

import com.kiniu.game.agent.Agent;
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
        Set<String> generatedChoices = new LinkedHashSet<>();
        if (storyBeat.choices() != null) {
            generatedChoices.addAll(storyBeat.choices());
        }
        if (generatedChoices.isEmpty()) {
            generatedChoices.add("帮我理清今晚");
        }
        if (generatedChoices.size() > 5) {
            return generatedChoices.stream().limit(5).toList();
        }
        return List.copyOf(generatedChoices);
    }

}
