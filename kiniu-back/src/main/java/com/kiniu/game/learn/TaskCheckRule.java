package com.kiniu.game.learn;

public interface TaskCheckRule {

    String type();

    void validateRule(TaskCheckDefinition definition);

    RuleCheckOutcome evaluate(TaskCheckDefinition definition, SubmissionSnapshot submission);
}
