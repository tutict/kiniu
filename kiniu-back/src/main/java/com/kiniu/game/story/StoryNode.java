package com.kiniu.game.story;

import com.kiniu.game.state.WorldState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class StoryNode {

    private final String id;
    private final String sceneId;
    private final String title;
    private final String speakerId;
    private final String narrative;
    private final List<String> tags;
    private final List<StoryChoice> choices;
    private final Set<String> enterFlags;
    private final Map<String, Integer> enterAffinityChanges;

    private StoryNode(Builder builder) {
        this.id = builder.id;
        this.sceneId = builder.sceneId;
        this.title = builder.title;
        this.speakerId = builder.speakerId;
        this.narrative = builder.narrative;
        this.tags = List.copyOf(builder.tags);
        this.choices = List.copyOf(builder.choices);
        this.enterFlags = Set.copyOf(builder.enterFlags);
        this.enterAffinityChanges = Map.copyOf(builder.enterAffinityChanges);
    }

    public static Builder builder(String id, String sceneId, String title, String speakerId) {
        return new Builder(id, sceneId, title, speakerId);
    }

    public void applyArrival(WorldState worldState) {
        worldState.setCurrentNodeId(id);
        worldState.setStorySeedNodeId(id);
        worldState.setCurrentScene(sceneId);

        String visitedFlag = "visited:" + id;
        if (worldState.hasFlag(visitedFlag)) {
            return;
        }

        worldState.addFlag(visitedFlag);
        enterFlags.forEach(worldState::addFlag);
        enterAffinityChanges.forEach(worldState::adjustAffinity);
    }

    public List<StoryChoice> availableChoices(WorldState worldState) {
        return choices.stream().filter(choice -> choice.isAvailable(worldState)).toList();
    }

    public Optional<StoryChoice> findMatchingChoice(WorldState worldState, String input, String choice) {
        return choices.stream()
                .filter(candidate -> candidate.matchesSelection(worldState, input, choice))
                .findFirst();
    }

    public String id() {
        return id;
    }

    public String sceneId() {
        return sceneId;
    }

    public String title() {
        return title;
    }

    public String speakerId() {
        return speakerId;
    }

    public String narrative() {
        return narrative;
    }

    public List<String> tags() {
        return tags;
    }

    public Set<String> enterFlags() {
        return enterFlags;
    }

    public Map<String, Integer> enterAffinityChanges() {
        return enterAffinityChanges;
    }

    public List<StoryChoice> choices() {
        return choices;
    }

    public static class Builder {

        private final String id;
        private final String sceneId;
        private final String title;
        private final String speakerId;
        private String narrative = "";
        private final List<String> tags = new ArrayList<>();
        private final List<StoryChoice> choices = new ArrayList<>();
        private final Set<String> enterFlags = new LinkedHashSet<>();
        private final Map<String, Integer> enterAffinityChanges = new LinkedHashMap<>();

        private Builder(String id, String sceneId, String title, String speakerId) {
            this.id = id;
            this.sceneId = sceneId;
            this.title = title;
            this.speakerId = speakerId;
        }

        public Builder narrative(String narrative) {
            this.narrative = narrative;
            return this;
        }

        public Builder tag(String tag) {
            this.tags.add(tag);
            return this;
        }

        public Builder enterFlag(String flag) {
            this.enterFlags.add(flag);
            return this;
        }

        public Builder enterAffinity(String characterId, int delta) {
            this.enterAffinityChanges.merge(characterId, delta, Integer::sum);
            return this;
        }

        public Builder choice(StoryChoice choice) {
            this.choices.add(choice);
            return this;
        }

        public StoryNode build() {
            return new StoryNode(this);
        }
    }
}
