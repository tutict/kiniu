package com.kiniu.game.learn;

import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskCheckService {

    private static final Logger log = LoggerFactory.getLogger(TaskCheckService.class);

    static final int MAX_FILES = 20;
    static final int MAX_PATH_CHARS = 200;
    static final int MAX_FILE_CHARS = 100_000;
    static final int MAX_TOTAL_CHARS = 500_000;
    static final int MAX_FILE_BYTES = 100_000;
    static final int MAX_TOTAL_BYTES = 500_000;

    private final TaskCheckRegistry registry;

    public TaskCheckService(TaskCheckRegistry registry) {
        this.registry = registry;
    }

    public List<TaskCheckResult> check(LearningTaskDefinition task, Map<String, String> files) {
        Map<String, String> safeFiles = files == null ? Map.of() : new LinkedHashMap<>(files);
        validateSubmission(task, safeFiles);
        SubmissionSnapshot submission = new SubmissionSnapshot(safeFiles);
        return task.checks().stream()
                .map(definition -> evaluateRegistered(definition, submission))
                .toList();
    }

    public int score(List<TaskCheckResult> results) {
        int total = results.stream().mapToInt(result -> Math.max(0, result.points())).sum();
        int earned = results.stream().filter(TaskCheckResult::passed).mapToInt(result -> Math.max(0, result.points())).sum();
        return total == 0 ? 0 : Math.round((earned * 100f) / total);
    }

    public boolean passed(List<TaskCheckResult> results) {
        List<TaskCheckResult> required = results.stream().filter(TaskCheckResult::required).toList();
        return !required.isEmpty() && required.stream().allMatch(TaskCheckResult::passed);
    }

    private TaskCheckResult evaluateRegistered(
            TaskCheckDefinition definition,
            SubmissionSnapshot submission) {
        try {
            RuleCheckOutcome outcome = registry.evaluate(definition, submission);
            return result(definition, outcome.passed(), outcome.evidence(), definition.message());
        } catch (RuntimeException exception) {
            return failedResult(definition, exception);
        }
    }

    private TaskCheckResult failedResult(TaskCheckDefinition definition, RuntimeException exception) {
        log.error(
                "Learning check failed unexpectedly: id={}, type={}",
                definition.id(),
                definition.type(),
                exception);
        return result(definition, false,
                "检查输入无法解析：内部检查器错误",
                definition.message());
    }

    private void validateSubmission(LearningTaskDefinition task, Map<String, String> files) {
        if (files.size() > MAX_FILES) {
            throw new IllegalArgumentException("Submission contains too many files.");
        }
        Set<String> allowedPaths = new HashSet<>();
        task.starterFiles().forEach(file -> allowedPaths.add(file.path()));
        task.checks().forEach(check -> allowedPaths.add(check.path()));

        int totalChars = 0;
        int totalBytes = 0;
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String path = entry.getKey();
            String content = entry.getValue();
            if (!isSafeRelativePath(path) || !allowedPaths.contains(path)) {
                throw new IllegalArgumentException("Submission contains an unsupported file path.");
            }
            if (content == null) {
                throw new IllegalArgumentException("Submission file content must not be null: " + path);
            }
            int length = content.length();
            int byteLength = content.getBytes(StandardCharsets.UTF_8).length;
            if (length > MAX_FILE_CHARS || byteLength > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("Submission file is too large: " + path);
            }
            totalChars += length;
            totalBytes += byteLength;
            if (totalChars > MAX_TOTAL_CHARS || totalBytes > MAX_TOTAL_BYTES) {
                throw new IllegalArgumentException("Submission is too large.");
            }
        }
    }

    private boolean isSafeRelativePath(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_PATH_CHARS) {
            return false;
        }
        try {
            Path path = Path.of(value);
            return !path.isAbsolute()
                    && path.normalize().toString().equals(value.replace('/', java.io.File.separatorChar))
                    && !value.contains("..");
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    private TaskCheckResult result(TaskCheckDefinition definition, boolean passed, String evidence, String message) {
        return new TaskCheckResult(definition.id(), passed, definition.required(), definition.points(), evidence, message);
    }
}
