package com.kiniu.game.learn;

public record LearningReference(
        String title,
        String url,
        String publisher,
        String version,
        String accessedAt) {

    public LearningReference {
        title = title == null ? "" : title.trim();
        url = url == null ? "" : url.trim();
        publisher = publisher == null ? "" : publisher.trim();
        version = version == null ? "" : version.trim();
        accessedAt = accessedAt == null ? "" : accessedAt.trim();
    }
}
