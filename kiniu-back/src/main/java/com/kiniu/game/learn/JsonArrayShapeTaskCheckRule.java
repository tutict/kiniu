package com.kiniu.game.learn;

import tools.jackson.core.JsonPointer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class JsonArrayShapeTaskCheckRule implements TaskCheckRule {

    private final ObjectMapper objectMapper;

    public JsonArrayShapeTaskCheckRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "json-array-shape";
    }

    @Override
    public void validateRule(TaskCheckDefinition definition) {
        parse(definition.rule());
    }

    @Override
    public RuleCheckOutcome evaluate(TaskCheckDefinition definition, SubmissionSnapshot submission) {
        ArrayShape rule = parse(definition.rule());
        return JsonTaskCheckRuleSupport.evaluate(
                definition,
                submission,
                objectMapper,
                root -> matches(root, rule));
    }

    private boolean matches(JsonNode root, ArrayShape rule) {
        JsonNode array = root.at(rule.pointer());
        if (!array.isArray() || array.size() < rule.minimum()) {
            return false;
        }
        return array.valueStream().allMatch(item -> item.isObject()
                && rule.requiredFields().stream()
                        .allMatch(field -> JsonTaskCheckRuleSupport.isMeaningful(item.get(field))));
    }

    private ArrayShape parse(String value) {
        String[] parts = value == null ? new String[0] : value.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid array shape rule");
        }
        JsonPointer.compile(parts[0]);
        int minimum = Integer.parseInt(parts[1]);
        List<String> requiredFields = Pattern.compile(",")
                .splitAsStream(parts[2])
                .map(String::trim)
                .filter(field -> !field.isBlank())
                .toList();
        if (minimum <= 0 || requiredFields.isEmpty()) {
            throw new IllegalArgumentException("Invalid array shape rule");
        }
        return new ArrayShape(parts[0], minimum, requiredFields);
    }

    private record ArrayShape(String pointer, int minimum, List<String> requiredFields) {
    }
}
