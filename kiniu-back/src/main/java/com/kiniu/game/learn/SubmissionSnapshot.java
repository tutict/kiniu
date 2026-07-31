package com.kiniu.game.learn;

import java.util.Map;
import java.util.Optional;

public final class SubmissionSnapshot {

    private final Map<String, String> files;

    public SubmissionSnapshot(Map<String, String> files) {
        this.files = Map.copyOf(files);
    }

    public Optional<String> content(String path) {
        return Optional.ofNullable(files.get(path));
    }

    public Map<String, String> files() {
        return files;
    }
}
