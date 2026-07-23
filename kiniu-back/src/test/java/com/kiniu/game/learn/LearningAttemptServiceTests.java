package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LearningAttemptServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldPersistFailedEvidenceAndReloadItByAttemptId() {
        Path path = tempDir.resolve("attempts.json");
        LearningAttemptService writer = new LearningAttemptService(new ObjectMapper(), path);
        LearningAttempt first = writer.record(
                "task",
                false,
                40,
                Map.of("artifact.md", "draft"),
                List.of(new TaskCheckResult("check", false, true, 100, "missing", "Fix it")),
                "first try");
        LearningAttempt second = writer.record(
                "task",
                true,
                100,
                Map.of("artifact.md", "done"),
                List.of(new TaskCheckResult("check", true, true, 100, "valid", "Good")),
                "second try");

        LearningAttemptService reloaded = new LearningAttemptService(new ObjectMapper(), path);

        assertEquals("draft", reloaded.get(first.attemptId()).files().get("artifact.md"));
        assertEquals("first try", reloaded.get(first.attemptId()).notes());
        assertEquals(2, reloaded.listForTask("task").size());
        assertNotEquals(first.attemptId(), second.attemptId());
    }
}