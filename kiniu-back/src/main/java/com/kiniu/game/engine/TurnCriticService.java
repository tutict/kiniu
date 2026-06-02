package com.kiniu.game.engine;

import com.kiniu.game.agent.AgentTurnPlan;
import com.kiniu.game.ai.AIService;
import com.kiniu.game.dto.AgentReplyView;
import com.kiniu.game.story.StoryEvent;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TurnCriticService {

    private final AIService aiService;

    public TurnCriticService(AIService aiService) {
        this.aiService = aiService;
    }

    public TurnCritiqueSummary critique(
            StoryEvent storyEvent,
            List<String> nextChoices,
            List<AgentTurnPlan> turnPlans,
            List<AgentReplyView> replies) {
        int focusScore = scoreFocus(storyEvent, replies);
        int castCoverageScore = Math.min(10, replies.size() * 3 + (turnPlans.size() > replies.size() ? 1 : 0));
        int choicePressureScore = Math.min(10, nextChoices.size() * 2);
        List<String> notes = buildNotes(storyEvent, nextChoices, turnPlans, replies, focusScore, castCoverageScore, choicePressureScore);
        String verdict = buildVerdict(focusScore, castCoverageScore, choicePressureScore);
        TurnCritiqueSummary fallbackSummary =
                new TurnCritiqueSummary(verdict, focusScore, castCoverageScore, choicePressureScore, notes);
        return aiService.generateCritiqueSummary(storyEvent, nextChoices, turnPlans, replies, fallbackSummary);
    }

    private int scoreFocus(StoryEvent storyEvent, List<AgentReplyView> replies) {
        boolean includesFocus = replies.stream().anyMatch(reply -> reply.agentId().equals(storyEvent.speakerId()));
        int base = includesFocus ? 8 : 4;
        if (replies.size() > 2) {
            base -= 1;
        }
        return Math.max(1, Math.min(10, base));
    }

    private List<String> buildNotes(
            StoryEvent storyEvent,
            List<String> nextChoices,
            List<AgentTurnPlan> turnPlans,
            List<AgentReplyView> replies,
            int focusScore,
            int castCoverageScore,
            int choicePressureScore) {
        List<String> notes = new ArrayList<>();
        if (focusScore < 7) {
            notes.add("Focus drift detected: the active speaker did not clearly dominate the performed replies.");
        } else {
            notes.add("Focus is coherent around " + storyEvent.speakerId() + ".");
        }
        if (turnPlans.size() > replies.size()) {
            notes.add("Some planned agents were held back, which preserves pacing but may hide unused tension.");
        }
        if (castCoverageScore < 6) {
            notes.add("Agent coverage is thin and may underuse the active roster.");
        } else {
            notes.add("Agent coverage is acceptable for the current workspace scale.");
        }
        if (choicePressureScore < 5) {
            notes.add("Choice pressure is weak; the user may need sharper next-action contrast.");
        } else {
            notes.add("Presented choices provide enough pressure to carry the next decision.");
        }
        if ("generated".equals(storyEvent.sourceType())) {
            notes.add("Generated turn should be reconciled with reusable flow continuity.");
        }
        return List.copyOf(notes);
    }

    private String buildVerdict(int focusScore, int castCoverageScore, int choicePressureScore) {
        int composite = focusScore + castCoverageScore + choicePressureScore;
        if (composite >= 22) {
            return "stable";
        }
        if (composite >= 16) {
            return "usable";
        }
        return "fragile";
    }
}
