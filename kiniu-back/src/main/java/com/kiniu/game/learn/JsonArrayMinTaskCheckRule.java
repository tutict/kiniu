package com.kiniu.game.learn;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class JsonArrayMinTaskCheckRule implements TaskCheckRule {

    private final ObjectMapper objectMapper;

    public JsonArrayMinTaskCheckRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "json-array-min";
    }

    @Override
    public void validateRule(TaskCheckDefinition definition) {
        parse(definition.rule());
    }

    @Override
    public RuleCheckOutcome evaluate(TaskCheckDefinition definition, SubmissionSnapshot submission) {
        ArrayMinimum rule = parse(definition.rule());
        return JsonTaskCheckRuleSupport.evaluate(
                definition,
                submission,
                objectMapper,
                root -> matches(root, rule));
    }

    private boolean matches(JsonNode root, ArrayMinimum rule) {
        JsonNode array = root.path(rule.field());
        if (!array.isArray()) {
            return false;
        }
        long meaningfulItems = array.valueStream()
                .filter(JsonTaskCheckRuleSupport::isMeaningful)
                .count();
        return meaningfulItems >= rule.minimum();
    }

    private ArrayMinimum parse(String value) {
        String[] parts = value == null ? new String[0] : value.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank()) {
            throw new IllegalArgumentException("Invalid array minimum rule");
        }
        int minimum = Integer.parseInt(parts[1]);
        if (minimum <= 0) {
            throw new IllegalArgumentException("Invalid array minimum rule");
        }
        return new ArrayMinimum(parts[0], minimum);
    }

    private record ArrayMinimum(String field, int minimum) {
    }
}
