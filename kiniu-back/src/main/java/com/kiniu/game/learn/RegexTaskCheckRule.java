package com.kiniu.game.learn;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class RegexTaskCheckRule implements TaskCheckRule {

    private static final int MAX_REGEX_LENGTH = 500;

    @Override
    public String type() {
        return "regex";
    }

    @Override
    public void validateRule(TaskCheckDefinition definition) {
        TaskCheckRuleSupport.requireRule(definition);
        if (definition.rule().length() > MAX_REGEX_LENGTH) {
            throw new IllegalArgumentException("Regex rule too long");
        }
        Pattern.compile(definition.rule());
    }

    @Override
    public RuleCheckOutcome evaluate(TaskCheckDefinition definition, SubmissionSnapshot submission) {
        return TaskCheckRuleSupport.evaluateContent(
                definition,
                submission,
                content -> Pattern.compile(definition.rule(), Pattern.MULTILINE).matcher(content).find());
    }
}
