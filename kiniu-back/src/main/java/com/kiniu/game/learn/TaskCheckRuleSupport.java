package com.kiniu.game.learn;

import java.util.function.Predicate;

final class TaskCheckRuleSupport {

    private TaskCheckRuleSupport() {
    }

    static void requireRule(TaskCheckDefinition definition) {
        if (definition.rule() == null || definition.rule().isBlank()) {
            throw new IllegalArgumentException("Learning check rule must not be blank");
        }
    }

    static RuleCheckOutcome evaluateContent(
            TaskCheckDefinition definition,
            SubmissionSnapshot submission,
            Predicate<String> predicate) {
        return submission.content(definition.path())
                .map(content -> predicate.test(content)
                        ? RuleCheckOutcome.passed("规则已匹配且内容有效")
                        : RuleCheckOutcome.failed("规则未满足：" + definition.rule()))
                .orElseGet(() -> RuleCheckOutcome.failed("文件不存在"));
    }
}
