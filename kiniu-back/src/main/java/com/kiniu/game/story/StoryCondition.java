package com.kiniu.game.story;

import com.kiniu.game.state.WorldState;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StoryCondition {

    private final Set<String> requiredFlags;
    private final Set<String> blockedFlags;
    private final Map<String, Integer> minimumAffinity;
    private final List<String> keywords;

    private StoryCondition(
            Set<String> requiredFlags,
            Set<String> blockedFlags,
            Map<String, Integer> minimumAffinity,
            List<String> keywords) {
        this.requiredFlags = Set.copyOf(requiredFlags);
        this.blockedFlags = Set.copyOf(blockedFlags);
        this.minimumAffinity = Map.copyOf(minimumAffinity);
        this.keywords = List.copyOf(keywords);
    }

    public static StoryCondition always() {
        return new StoryCondition(Set.of(), Set.of(), Map.of(), List.of());
    }

    public StoryCondition requiringFlags(String... flags) {
        Set<String> nextFlags = new LinkedHashSet<>(requiredFlags);
        for (String flag : flags) {
            nextFlags.add(flag);
        }
        return new StoryCondition(nextFlags, blockedFlags, minimumAffinity, keywords);
    }

    public StoryCondition blockingFlags(String... flags) {
        Set<String> nextFlags = new LinkedHashSet<>(blockedFlags);
        for (String flag : flags) {
            nextFlags.add(flag);
        }
        return new StoryCondition(requiredFlags, nextFlags, minimumAffinity, keywords);
    }

    public StoryCondition minAffinity(String characterId, int value) {
        Map<String, Integer> nextAffinity = new LinkedHashMap<>(minimumAffinity);
        nextAffinity.put(characterId, value);
        return new StoryCondition(requiredFlags, blockedFlags, nextAffinity, keywords);
    }

    public StoryCondition keywords(String... values) {
        return new StoryCondition(requiredFlags, blockedFlags, minimumAffinity, List.of(values));
    }

    public boolean isSatisfiedBy(WorldState worldState) {
        boolean hasRequiredFlags = requiredFlags.stream().allMatch(worldState::hasFlag);
        boolean avoidsBlockedFlags = blockedFlags.stream().noneMatch(worldState::hasFlag);
        boolean meetsAffinity = minimumAffinity.entrySet().stream()
                .allMatch(entry -> worldState.getAffinity(entry.getKey()) >= entry.getValue());

        return hasRequiredFlags && avoidsBlockedFlags && meetsAffinity;
    }

    public boolean hasKeywords() {
        return !keywords.isEmpty();
    }

    public boolean matchesKeywords(String text) {
        if (keywords.isEmpty()) {
            return false;
        }

        String normalized = text == null ? "" : text.toLowerCase();
        return keywords.stream().map(String::toLowerCase).anyMatch(normalized::contains);
    }

    public Set<String> requiredFlags() {
        return requiredFlags;
    }

    public Set<String> blockedFlags() {
        return blockedFlags;
    }

    public Map<String, Integer> minimumAffinity() {
        return minimumAffinity;
    }

    public List<String> keywords() {
        return keywords;
    }
}
