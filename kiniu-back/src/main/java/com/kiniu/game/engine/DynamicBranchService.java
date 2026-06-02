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
            generatedChoices.add("让容器先总结当前目标");
        }
        if (storyBeat.isGenerated()) {
            generatedChoices.add("把这个临时方向沉淀成任务流");
        }

        for (Agent agent : activeAgents) {
            if ("narrator".equals(agent.id())) {
                continue;
            }

            RelationshipState relationshipState = state.getRelationship(agent.id());
            generatedChoices.add("让 " + agent.name() + " 给出下一步建议");

            if (relationshipState.getTrust() >= 2) {
                generatedChoices.add("让 " + agent.name() + " 主导这一轮");
            }
            if (relationshipState.getAffection() >= 2) {
                generatedChoices.add("让 " + agent.name() + " 保持陪伴节奏");
            }
            if (relationshipState.getCuriosity() >= 2) {
                generatedChoices.add("让 " + agent.name() + " 追问一个细节");
            }
        }

        if (state.getFlags().contains("session-started")) {
            generatedChoices.add("让容器总结当前会话状态");
        }
        if (state.getFlags().contains("generated-conversation-active")) {
            generatedChoices.add("把动态对话保存成可复用模板");
        }
        if (generatedChoices.size() > 7) {
            return generatedChoices.stream().limit(7).toList();
        }
        return List.copyOf(generatedChoices);
    }
}
