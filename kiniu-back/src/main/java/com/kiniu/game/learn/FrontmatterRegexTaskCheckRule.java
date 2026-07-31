package com.kiniu.game.learn;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class FrontmatterRegexTaskCheckRule implements TaskCheckRule {

    private static final int MAX_REGEX_LENGTH = 500;

    @Override
    public String type() {
        return "frontmatter-regex";
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
                content -> matchesFrontmatter(content, definition.rule()));
    }

    private boolean matchesFrontmatter(String content, String rule) {
        String[] lines = content.replace("\r", "").split("\n", -1);
        if (lines.length < 3 || !lines[0].trim().equals("---")) {
            return false;
        }
        StringBuilder frontmatter = new StringBuilder();
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            if (line.trim().equals("---")) {
                return Pattern.compile(rule, Pattern.MULTILINE).matcher(frontmatter).find();
            }
            frontmatter.append(line).append('\n');
        }
        return false;
    }
}
