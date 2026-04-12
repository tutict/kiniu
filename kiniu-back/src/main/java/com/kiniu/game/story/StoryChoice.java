package com.kiniu.game.story;

import com.kiniu.game.state.WorldState;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class StoryChoice {

    private final String id;
    private final String label;
    private final String description;
    private final String targetNodeId;
    private final StoryCondition condition;
    private final Set<String> flagsToAdd;
    private final Map<String, Integer> affinityChanges;

    private StoryChoice(
            String id,
            String label,
            String description,
            String targetNodeId,
            StoryCondition condition,
            Set<String> flagsToAdd,
            Map<String, Integer> affinityChanges) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.targetNodeId = targetNodeId;
        this.condition = condition;
        this.flagsToAdd = Set.copyOf(flagsToAdd);
        this.affinityChanges = Map.copyOf(affinityChanges);
    }

    public static StoryChoice of(String id, String label, String description, String targetNodeId) {
        return new StoryChoice(
                id,
                label,
                description,
                targetNodeId,
                StoryCondition.always(),
                Set.of(),
                Map.of());
    }

    public StoryChoice whenKeywords(String... keywords) {
        return new StoryChoice(id, label, description, targetNodeId, condition.keywords(keywords), flagsToAdd, affinityChanges);
    }

    public StoryChoice requiringFlags(String... flags) {
        return new StoryChoice(id, label, description, targetNodeId, condition.requiringFlags(flags), flagsToAdd, affinityChanges);
    }

    public StoryChoice blockingFlags(String... flags) {
        return new StoryChoice(id, label, description, targetNodeId, condition.blockingFlags(flags), flagsToAdd, affinityChanges);
    }

    public StoryChoice minAffinity(String characterId, int value) {
        return new StoryChoice(id, label, description, targetNodeId, condition.minAffinity(characterId, value), flagsToAdd, affinityChanges);
    }

    public StoryChoice addFlag(String flag) {
        Set<String> nextFlags = new LinkedHashSet<>(flagsToAdd);
        nextFlags.add(flag);
        return new StoryChoice(id, label, description, targetNodeId, condition, nextFlags, affinityChanges);
    }

    public StoryChoice changeAffinity(String characterId, int delta) {
        Map<String, Integer> nextAffinity = new LinkedHashMap<>(affinityChanges);
        nextAffinity.merge(characterId, delta, Integer::sum);
        return new StoryChoice(id, label, description, targetNodeId, condition, flagsToAdd, nextAffinity);
    }

    public boolean isAvailable(WorldState worldState) {
        return condition.isSatisfiedBy(worldState);
    }

    public boolean matchesSelection(WorldState worldState, String input, String choice) {
        if (!isAvailable(worldState)) {
            return false;
        }

        String normalizedChoice = normalize(choice);
        if (!normalizedChoice.isBlank()
                && (normalize(label).equals(normalizedChoice) || normalize(id).equals(normalizedChoice))) {
            return true;
        }

        if (!condition.hasKeywords()) {
            return false;
        }

        String combined = (normalize(input) + " " + normalizedChoice).trim();
        return !combined.isBlank() && condition.matchesKeywords(combined);
    }

    public void apply(WorldState worldState) {
        flagsToAdd.forEach(worldState::addFlag);
        affinityChanges.forEach(worldState::adjustAffinity);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public String targetNodeId() {
        return targetNodeId;
    }

    public StoryCondition condition() {
        return condition;
    }

    public Set<String> flagsToAdd() {
        return flagsToAdd;
    }

    public Map<String, Integer> affinityChanges() {
        return affinityChanges;
    }
}
