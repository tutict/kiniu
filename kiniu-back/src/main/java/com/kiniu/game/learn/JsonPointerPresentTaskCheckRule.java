package com.kiniu.game.learn;

import tools.jackson.core.JsonPointer;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class JsonPointerPresentTaskCheckRule implements TaskCheckRule {

    private final ObjectMapper objectMapper;

    public JsonPointerPresentTaskCheckRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "json-pointer-present";
    }

    @Override
    public void validateRule(TaskCheckDefinition definition) {
        TaskCheckRuleSupport.requireRule(definition);
        JsonPointer.compile(definition.rule());
    }

    @Override
    public RuleCheckOutcome evaluate(TaskCheckDefinition definition, SubmissionSnapshot submission) {
        return JsonTaskCheckRuleSupport.evaluate(
                definition,
                submission,
                objectMapper,
                root -> JsonTaskCheckRuleSupport.isMeaningful(root.at(definition.rule())));
    }
}
