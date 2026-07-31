package com.kiniu.game.learn;

import org.springframework.stereotype.Component;

@Component
public final class ContainsTaskCheckRule implements TaskCheckRule {

    @Override
    public String type() {
        return "contains";
    }

    @Override
    public void validateRule(TaskCheckDefinition definition) {
        TaskCheckRuleSupport.requireRule(definition);
    }

    @Override
    public RuleCheckOutcome evaluate(TaskCheckDefinition definition, SubmissionSnapshot submission) {
        return TaskCheckRuleSupport.evaluateContent(
                definition,
                submission,
                content -> content.contains(definition.rule()));
    }
}
