package com.kiniu.game.engine;

import com.kiniu.game.ai.AIInvocationTelemetry;
import com.kiniu.game.dto.AIInvocationView;
import com.kiniu.game.agent.Agent;
import com.kiniu.game.agent.AgentTurnPlan;
import com.kiniu.game.dto.AgentReplyView;
import com.kiniu.game.dto.BranchOptionView;
import com.kiniu.game.dto.OrchestrationAgentPlanView;
import com.kiniu.game.dto.OrchestrationCriticView;
import com.kiniu.game.dto.OrchestrationPlannerView;
import com.kiniu.game.dto.OrchestrationTraceView;
import com.kiniu.game.dto.ScoreFactorView;
import com.kiniu.game.story.StoryEvent;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrchestrationTraceFactory {

    public OrchestrationTraceView create(
            StoryEvent storyEvent,
            List<Agent> activeAgents,
            List<String> nextChoices,
            List<BranchOption> nextBranchOptions,
            List<AgentTurnPlan> turnPlans,
            List<AgentReplyView> replies,
            TurnPlannerBrief plannerBrief,
            TurnCritiqueSummary critiqueSummary,
            List<AIInvocationTelemetry> aiInvocations) {
        List<OrchestrationAgentPlanView> plans = turnPlans.stream()
                .map(plan -> new OrchestrationAgentPlanView(
                        plan.agent().id(),
                        plan.agent().name(),
                        plan.agent().role(),
                        plan.shouldSpeak(),
                        plan.initiativeScore(),
                        plan.objective(),
                        plan.memorySummary(),
                        plan.scoreFactors().stream()
                                .map(factor -> new ScoreFactorView(factor.code(), factor.delta(), factor.reason()))
                                .toList()))
                .toList();

        return new OrchestrationTraceView(
                storyEvent.id(),
                storyEvent.title(),
                storyEvent.sourceType(),
                storyEvent.targetScene(),
                storyEvent.speakerId(),
                new OrchestrationPlannerView(
                        plannerBrief.sceneGoal(),
                        plannerBrief.tensionLabel(),
                        plannerBrief.pacingLabel(),
                        plannerBrief.directorIntent(),
                        plannerBrief.risks()),
                new OrchestrationCriticView(
                        critiqueSummary.verdict(),
                        critiqueSummary.focusScore(),
                        critiqueSummary.castCoverageScore(),
                        critiqueSummary.choicePressureScore(),
                        critiqueSummary.notes()),
                aiInvocations.stream()
                        .map(invocation -> new AIInvocationView(
                                invocation.operation(),
                                invocation.targetId(),
                                invocation.providerAttempted(),
                                invocation.providerSucceeded(),
                                invocation.fallbackUsed(),
                                invocation.providerUrl(),
                                invocation.model(),
                                invocation.latencyMs(),
                                invocation.errorMessage()))
                        .toList(),
                List.copyOf(storyEvent.spotlightAgentIds()),
                activeAgents.stream().map(Agent::id).toList(),
                replies.stream().map(AgentReplyView::agentId).toList(),
                List.copyOf(nextChoices),
                nextBranchOptions.stream()
                        .map(option -> new BranchOptionView(
                                option.label(),
                                option.intent(),
                                option.risk(),
                                option.targetMood(),
                                option.targetAgentId(),
                                option.consequenceSummary(),
                                option.relationshipDelta(),
                                option.addedFlags(),
                                option.removedFlags(),
                                option.source()))
                        .toList(),
                plans);
    }
}
