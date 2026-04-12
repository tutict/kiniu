package com.kiniu.game.memory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class MemoryService {

    private static final int MAX_DIALOGUE_ENTRIES = 12;
    private static final int MAX_AGENT_MEMORY_ENTRIES = 8;

    private final ConcurrentMap<String, Deque<String>> sessionMemory = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, Deque<String>>> agentMemory = new ConcurrentHashMap<>();

    public void storeDialogue(String sessionId, String speaker, String content) {
        Deque<String> memory = sessionMemory.computeIfAbsent(sessionId, key -> new ArrayDeque<>());
        synchronized (memory) {
            memory.addLast(speaker + ": " + content);
            while (memory.size() > MAX_DIALOGUE_ENTRIES) {
                memory.removeFirst();
            }
        }
    }

    public List<String> getRecentDialogue(String sessionId) {
        Deque<String> memory = sessionMemory.get(sessionId);
        if (memory == null) {
            return List.of();
        }
        synchronized (memory) {
            return new ArrayList<>(memory);
        }
    }

    public void storeAgentMemory(String sessionId, String agentId, String content) {
        ConcurrentMap<String, Deque<String>> sessionAgentMemory =
                agentMemory.computeIfAbsent(sessionId, key -> new ConcurrentHashMap<>());
        Deque<String> memory = sessionAgentMemory.computeIfAbsent(agentId, key -> new ArrayDeque<>());
        synchronized (memory) {
            memory.addLast(content);
            while (memory.size() > MAX_AGENT_MEMORY_ENTRIES) {
                memory.removeFirst();
            }
        }
    }

    public List<String> getRecentAgentMemory(String sessionId, String agentId) {
        ConcurrentMap<String, Deque<String>> sessionAgentMemory = agentMemory.get(sessionId);
        if (sessionAgentMemory == null) {
            return List.of();
        }

        Deque<String> memory = sessionAgentMemory.get(agentId);
        if (memory == null) {
            return List.of();
        }

        synchronized (memory) {
            return new ArrayList<>(memory);
        }
    }

    public String summarizeAgentMemory(String sessionId, String agentId, int limit) {
        List<String> memory = getRecentAgentMemory(sessionId, agentId);
        if (memory.isEmpty()) {
            return "No private memory yet.";
        }

        int start = Math.max(0, memory.size() - Math.max(limit, 1));
        return String.join(" | ", memory.subList(start, memory.size()));
    }
}
