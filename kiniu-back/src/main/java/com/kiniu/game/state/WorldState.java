package com.kiniu.game.state;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class WorldState {

    private String currentScene;
    private String currentNodeId;
    private String storySeedNodeId;
    private Set<String> flags;
    private Map<String, RelationshipState> relationships;
    private Map<String, Integer> affinityScores;

    public WorldState() {
        this.currentScene = "opening";
        this.currentNodeId = "opening.threshold";
        this.storySeedNodeId = "opening.threshold";
        this.flags = new LinkedHashSet<>();
        this.relationships = new LinkedHashMap<>();
        this.affinityScores = new LinkedHashMap<>();
        ensureRelationship("narrator");
    }

    public static WorldState initial() {
        return new WorldState();
    }

    public WorldState snapshot() {
        WorldState copy = new WorldState();
        copy.setCurrentScene(this.currentScene);
        copy.setCurrentNodeId(this.currentNodeId);
        copy.setStorySeedNodeId(this.storySeedNodeId);
        copy.setFlags(new LinkedHashSet<>(this.flags));
        Map<String, RelationshipState> copiedRelationships = new LinkedHashMap<>();
        this.relationships.forEach((key, value) -> copiedRelationships.put(key, value.copy()));
        copy.setRelationships(copiedRelationships);
        copy.setAffinityScores(new LinkedHashMap<>(this.affinityScores));
        return copy;
    }

    public void addFlag(String flag) {
        flags.add(flag);
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    public void adjustAffinity(String characterId, int delta) {
        adjustRelationship(characterId, delta, 0, 0);
    }

    public int getAffinity(String characterId) {
        return affinityScores.getOrDefault(characterId, 0);
    }

    public void adjustRelationship(String characterId, int trustDelta, int affectionDelta, int curiosityDelta) {
        RelationshipState relationshipState = ensureRelationship(characterId);
        relationshipState.adjust(trustDelta, affectionDelta, curiosityDelta);
        affinityScores.put(characterId, relationshipState.aggregate());
    }

    public RelationshipState getRelationship(String characterId) {
        return ensureRelationship(characterId);
    }

    private RelationshipState ensureRelationship(String characterId) {
        RelationshipState relationshipState = relationships.computeIfAbsent(characterId, key -> new RelationshipState());
        affinityScores.put(characterId, relationshipState.aggregate());
        return relationshipState;
    }

    public String getCurrentScene() {
        return currentScene;
    }

    public void setCurrentScene(String currentScene) {
        this.currentScene = currentScene;
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    public String getStorySeedNodeId() {
        return storySeedNodeId;
    }

    public void setStorySeedNodeId(String storySeedNodeId) {
        this.storySeedNodeId = storySeedNodeId;
    }

    public Set<String> getFlags() {
        return flags;
    }

    public void setFlags(Set<String> flags) {
        this.flags = flags;
    }

    public Map<String, RelationshipState> getRelationships() {
        return relationships;
    }

    public void setRelationships(Map<String, RelationshipState> relationships) {
        this.relationships = relationships;
        this.affinityScores = new LinkedHashMap<>();
        this.relationships.forEach((key, value) -> this.affinityScores.put(key, value.aggregate()));
    }

    public Map<String, Integer> getAffinityScores() {
        return affinityScores;
    }

    public void setAffinityScores(Map<String, Integer> affinityScores) {
        this.affinityScores = affinityScores;
    }
}
