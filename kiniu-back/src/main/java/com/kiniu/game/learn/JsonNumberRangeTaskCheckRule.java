package com.kiniu.game.learn;

import tools.jackson.core.JsonPointer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public final class JsonNumberRangeTaskCheckRule implements TaskCheckRule {

    private final ObjectMapper objectMapper;

    public JsonNumberRangeTaskCheckRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "json-number-range";
    }

    @Override
    public void validateRule(TaskCheckDefinition definition) {
        parse(definition.rule());
    }

    @Override
    public RuleCheckOutcome evaluate(TaskCheckDefinition definition, SubmissionSnapshot submission) {
        NumberRange rule = parse(definition.rule());
        return JsonTaskCheckRuleSupport.evaluate(
                definition,
                submission,
                objectMapper,
                root -> matches(root, rule));
    }

    private boolean matches(JsonNode root, NumberRange rule) {
        JsonNode value = root.at(rule.pointer());
        if (!value.isNumber()) {
            return false;
        }
        BigDecimal actual = value.decimalValue();
        return actual.compareTo(rule.minimum()) >= 0 && actual.compareTo(rule.maximum()) <= 0;
    }

    private NumberRange parse(String value) {
        String[] parts = value == null ? new String[0] : value.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid number range rule");
        }
        JsonPointer.compile(parts[0]);
        BigDecimal minimum = new BigDecimal(parts[1]);
        BigDecimal maximum = new BigDecimal(parts[2]);
        if (minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Invalid number range rule");
        }
        return new NumberRange(parts[0], minimum, maximum);
    }

    private record NumberRange(String pointer, BigDecimal minimum, BigDecimal maximum) {
    }
}
