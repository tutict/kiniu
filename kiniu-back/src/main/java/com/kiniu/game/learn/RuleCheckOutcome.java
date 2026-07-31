package com.kiniu.game.learn;

public record RuleCheckOutcome(boolean passed, String evidence) {

    public static RuleCheckOutcome passed(String evidence) {
        return new RuleCheckOutcome(true, evidence);
    }

    public static RuleCheckOutcome failed(String evidence) {
        return new RuleCheckOutcome(false, evidence);
    }
}
