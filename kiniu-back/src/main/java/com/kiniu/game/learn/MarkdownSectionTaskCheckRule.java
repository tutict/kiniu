package com.kiniu.game.learn;

import org.springframework.stereotype.Component;

@Component
public final class MarkdownSectionTaskCheckRule implements TaskCheckRule {

    @Override
    public String type() {
        return "markdown-section";
    }

    @Override
    public void validateRule(TaskCheckDefinition definition) {
        TaskCheckRuleSupport.requireRule(definition);
    }

    @Override
    public RuleCheckOutcome evaluate(TaskCheckDefinition definition, SubmissionSnapshot submission) {
        return TaskCheckRuleSupport.evaluateContent(
                definition,
                submission,
                content -> hasContent(content, definition.rule()));
    }

    private boolean hasContent(String content, String heading) {
        String target = heading.trim();
        int targetLevel = headingLevel(target);
        int breakLevel = targetLevel == 0 ? Integer.MAX_VALUE : targetLevel;
        String[] lines = content.replace("\r", "").split("\n", -1);
        boolean inSection = false;
        StringBuilder section = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!inSection && trimmed.equals(target)) {
                inSection = true;
                continue;
            }
            if (inSection && trimmed.startsWith("#") && headingLevel(trimmed) <= breakLevel) {
                break;
            }
            if (inSection && isSubstantiveLine(trimmed)) {
                section.append(line).append('\n');
            }
        }
        return inSection && !section.toString().trim().isBlank();
    }

    private int headingLevel(String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        return level;
    }

    private boolean isSubstantiveLine(String line) {
        return !line.isBlank()
                && !line.startsWith("#")
                && !line.matches("(?:-[ \\t]*){3,}|(?:\\*[ \\t]*){3,}|(?:_[ \\t]*){3,}")
                && !line.matches("(?:[-+*]|\\d+[.)])(?:[ \\t]+\\[[ xX]\\])?[ \\t]*")
                && !line.matches("(?:>[ \\t]*)+")
                && !line.matches("(?:`{3,}|~{3,}).*");
    }
}
