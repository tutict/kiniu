package com.kiniu.game.learn;

import org.springframework.stereotype.Component;

@Component
public final class MinLengthTaskCheckRule implements TaskCheckRule {

    @Override
    public String type() {
        return "min-length";
    }

    @Override
    public void validateRule(TaskCheckDefinition definition) {
        parse(definition.rule());
    }

    @Override
    public RuleCheckOutcome evaluate(TaskCheckDefinition definition, SubmissionSnapshot submission) {
        int minimum = parse(definition.rule());
        return TaskCheckRuleSupport.evaluateContent(
                definition,
                submission,
                content -> content.trim().length() >= minimum);
    }

    private int parse(String value) {
        int minimum = Integer.parseInt(value);
        if (minimum <= 0) {
            throw new IllegalArgumentException("Invalid minimum length rule");
        }
        return minimum;
    }
}
