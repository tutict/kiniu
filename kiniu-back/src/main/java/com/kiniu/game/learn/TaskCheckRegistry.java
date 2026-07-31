package com.kiniu.game.learn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class TaskCheckRegistry {

    private final Map<String, TaskCheckRule> rulesByType;

    public TaskCheckRegistry(List<TaskCheckRule> rules) {
        Map<String, TaskCheckRule> registered = new LinkedHashMap<>();
        for (TaskCheckRule rule : rules) {
            String type = rule.type();
            if (type == null || type.isBlank()) {
                throw new IllegalStateException("Learning check type must not be blank.");
            }
            if (registered.putIfAbsent(type, rule) != null) {
                throw new IllegalStateException("Duplicate learning check type: " + type);
            }
        }
        this.rulesByType = Map.copyOf(registered);
    }

    public void validateRule(TaskCheckDefinition definition) {
        TaskCheckRule rule = requireRule(definition.type());
        try {
            rule.validateRule(definition);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid learning check rule: " + definition.id(), exception);
        }
    }

    public RuleCheckOutcome evaluate(TaskCheckDefinition definition, SubmissionSnapshot submission) {
        return requireRule(definition.type()).evaluate(definition, submission);
    }

    private TaskCheckRule requireRule(String type) {
        TaskCheckRule rule = rulesByType.get(type);
        if (rule == null) {
            throw new IllegalStateException("Unsupported learning check type: " + type);
        }
        return rule;
    }
}
