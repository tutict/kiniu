package com.kiniu.game.ai;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

public final class EveningPracticeReply {

    private EveningPracticeReply() {
    }

    public static Optional<String> fromLearnerText(String text) {
        String practice = text == null ? "" : text.trim();
        if (!practice.contains("今晚")) {
            return Optional.empty();
        }
        List<String> steps = todos(practice);
        List<String> limits = limits(practice);
        if (steps.isEmpty() && limits.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder reply = new StringBuilder();
        if (!steps.isEmpty()) {
            reply.append("按你贴的待办，我先排这几步：");
            for (int index = 0; index < steps.size(); index++) {
                reply.append('\n').append(index + 1).append(". ").append(steps.get(index));
            }
            reply.append("\n不确定哪一件最急，所以没有替你决定顺序。");
        } else {
            reply.append("我按你刚写的边界来，先不编今晚的安排。");
        }
        if (!limits.isEmpty()) {
            reply.append("\n按你的话，这些不做：");
            for (String limit : limits) {
                reply.append('\n').append("- ").append(limit);
            }
        }
        return Optional.of(reply.toString());
    }

    private static List<String> todos(String practice) {
        int marker = practice.indexOf("待办：");
        int offset = "待办：".length();
        if (marker < 0) {
            marker = practice.indexOf("待办:");
            offset = "待办:".length();
        }
        if (marker < 0) {
            return List.of();
        }
        String rest = practice.substring(marker + offset);
        int end = indexOfAny(rest, '。', '！', '？');
        if (end >= 0) {
            rest = rest.substring(0, end);
        }
        LinkedHashSet<String> steps = new LinkedHashSet<>();
        for (String part : rest.split("[，、,;；]")) {
            String step = part.trim();
            if (step.length() < 2 || step.length() > 20) {
                continue;
            }
            steps.add(step);
            if (steps.size() == 3) {
                break;
            }
        }
        return List.copyOf(steps);
    }

    private static List<String> limits(String practice) {
        LinkedHashSet<String> limits = new LinkedHashSet<>();
        for (String sentence : practice.split("[。！？]")) {
            for (String part : sentence.split("，")) {
                String clause = part.trim().replaceFirst("^(?:请|也|再)+", "");
                if (!clause.contains("不要") && !clause.contains("不能") && !clause.contains("不算")) {
                    continue;
                }
                if (clause.length() > 32) {
                    clause = clause.substring(0, 32);
                }
                if (clause.length() >= 2) {
                    limits.add(clause);
                }
                if (limits.size() == 4) {
                    return List.copyOf(limits);
                }
            }
        }
        return List.copyOf(limits);
    }

    private static int indexOfAny(String value, char... candidates) {
        int found = -1;
        for (char candidate : candidates) {
            int index = value.indexOf(candidate);
            if (index >= 0 && (found < 0 || index < found)) {
                found = index;
            }
        }
        return found;
    }
}
