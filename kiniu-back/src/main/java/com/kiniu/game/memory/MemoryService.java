package com.kiniu.game.memory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MemoryService {

    private static final int MAX_DIALOGUE_ENTRIES = 12;
    private static final int MAX_AGENT_MEMORY_ENTRIES = 8;

    private final int maxTrackedSessions;
    private final Map<String, Boolean> trackedSessions = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Deque<String>> sessionMemory = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Map<String, Deque<String>>> agentMemory = new LinkedHashMap<>(16, 0.75f, true);

    public MemoryService(@Value("${game.sessions.max-memory-sessions:3}") int maxTrackedSessions) {
        this.maxTrackedSessions = Math.max(1, maxTrackedSessions);
    }

    public synchronized void storeDialogue(String sessionId, String speaker, String content) {
        touchSession(sessionId);
        Deque<String> memory = sessionMemory.computeIfAbsent(sessionId, key -> new ArrayDeque<>());
        memory.addLast(speaker + ": " + content);
        while (memory.size() > MAX_DIALOGUE_ENTRIES) {
            memory.removeFirst();
        }
    }

    public synchronized List<String> getRecentDialogue(String sessionId) {
        Deque<String> memory = sessionMemory.get(sessionId);
        if (memory == null) {
            return List.of();
        }
        touchSession(sessionId);
        return new ArrayList<>(memory);
    }

    public synchronized void storeAgentMemory(String sessionId, String agentId, String content) {
        touchSession(sessionId);
        Map<String, Deque<String>> sessionAgentMemory =
                agentMemory.computeIfAbsent(sessionId, key -> new LinkedHashMap<>(16, 0.75f, true));
        Deque<String> memory = sessionAgentMemory.computeIfAbsent(agentId, key -> new ArrayDeque<>());
        memory.addLast(content);
        while (memory.size() > MAX_AGENT_MEMORY_ENTRIES) {
            memory.removeFirst();
        }
    }

    public synchronized List<String> getRecentAgentMemory(String sessionId, String agentId) {
        Map<String, Deque<String>> sessionAgentMemory = agentMemory.get(sessionId);
        if (sessionAgentMemory == null) {
            return List.of();
        }
        touchSession(sessionId);

        Deque<String> memory = sessionAgentMemory.get(agentId);
        if (memory == null) {
            return List.of();
        }

        return new ArrayList<>(memory);
    }

    public String summarizeAgentMemory(String sessionId, String agentId, int limit) {
        List<String> memory = getRecentAgentMemory(sessionId, agentId);
        if (memory.isEmpty()) {
            return "No private memory yet.";
        }

        int start = Math.max(0, memory.size() - Math.max(limit, 1));
        return String.join(" | ", memory.subList(start, memory.size()));
    }

    private void touchSession(String sessionId) {
        trackedSessions.put(sessionId, Boolean.TRUE);
        trimTrackedSessions();
    }

    private void trimTrackedSessions() {
        while (trackedSessions.size() > maxTrackedSessions) {
            String eldestSessionId = trackedSessions.keySet().iterator().next();
            trackedSessions.remove(eldestSessionId);
            sessionMemory.remove(eldestSessionId);
            agentMemory.remove(eldestSessionId);
        }
    }
}
