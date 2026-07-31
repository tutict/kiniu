package com.kiniu.game.learn;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.function.Predicate;

final class JsonTaskCheckRuleSupport {

    private JsonTaskCheckRuleSupport() {
    }

    static RuleCheckOutcome evaluate(
            TaskCheckDefinition definition,
            SubmissionSnapshot submission,
            ObjectMapper objectMapper,
            Predicate<JsonNode> predicate) {
        String content = submission.content(definition.path()).orElse(null);
        if (content == null) {
            return RuleCheckOutcome.failed("文件不存在");
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            boolean passed = root != null && predicate.test(root);
            return passed
                    ? RuleCheckOutcome.passed("规则已匹配且内容有效")
                    : RuleCheckOutcome.failed("规则未满足：" + definition.rule());
        } catch (JacksonException exception) {
            return RuleCheckOutcome.failed("检查输入无法解析：JSON 格式无效");
        }
    }

    static boolean isMeaningful(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return false;
        }
        if (node.isString()) {
            return !node.asString().trim().isBlank();
        }
        if (node.isArray()) {
            return node.valueStream().anyMatch(JsonTaskCheckRuleSupport::isMeaningful);
        }
        if (node.isObject()) {
            return !node.properties().isEmpty()
                    && node.valueStream().anyMatch(JsonTaskCheckRuleSupport::isMeaningful);
        }
        return true;
    }
}
