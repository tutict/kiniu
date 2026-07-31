package com.kiniu.game.learn;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class JsonFieldTaskCheckRule implements TaskCheckRule {

    private final ObjectMapper objectMapper;

    public JsonFieldTaskCheckRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "json-field";
    }

    @Override
    public void validateRule(TaskCheckDefinition definition) {
        TaskCheckRuleSupport.requireRule(definition);
    }

    @Override
    public RuleCheckOutcome evaluate(TaskCheckDefinition definition, SubmissionSnapshot submission) {
        return JsonTaskCheckRuleSupport.evaluate(
                definition,
                submission,
                objectMapper,
                root -> root.has(definition.rule())
                        && JsonTaskCheckRuleSupport.isMeaningful(root.get(definition.rule())));
    }
}
