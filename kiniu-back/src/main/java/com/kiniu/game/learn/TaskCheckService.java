package com.kiniu.game.learn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class TaskCheckService {

    static final int MAX_FILES = 20;
    static final int MAX_PATH_CHARS = 200;
    static final int MAX_FILE_CHARS = 100_000;
    static final int MAX_TOTAL_CHARS = 500_000;

    private final ObjectMapper objectMapper;

    public TaskCheckService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<TaskCheckResult> check(LearningTaskDefinition task, Map<String, String> files) {
        Map<String, String> safeFiles = files == null ? Map.of() : new LinkedHashMap<>(files);
        validateSubmission(task, safeFiles);
        return task.checks().stream()
                .map(definition -> evaluate(definition, safeFiles.get(definition.path())))
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

    private TaskCheckResult evaluate(TaskCheckDefinition definition, String content) {
        if (content == null) {
            return result(definition, false, "文件不存在", "提交中缺少 " + definition.path());
        }
        try {
            boolean passed = switch (definition.type()) {
                case "contains" -> content.contains(definition.rule());
                case "markdown-section" -> markdownSectionHasContent(content, definition.rule());
                case "regex" -> Pattern.compile(definition.rule(), Pattern.MULTILINE).matcher(content).find();
                case "json-field" -> jsonField(content, definition.rule());
                case "json-array-min" -> jsonArrayMin(content, definition.rule());
                case "min-length" -> content.trim().length() >= Integer.parseInt(definition.rule());
                default -> false;
            };
            return result(
                    definition,
                    passed,
                    passed ? "规则已匹配且内容有效" : "规则未满足：" + definition.rule(),
                    definition.message());
        } catch (Exception exception) {
            return result(definition, false, "检查输入无法解析：" + safeMessage(exception), definition.message());
        }
    }

    private boolean markdownSectionHasContent(String content, String heading) {
        String[] lines = content.replace("\r", "").split("\n", -1);
        boolean inSection = false;
        StringBuilder section = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!inSection && trimmed.equals(heading.trim())) {
                inSection = true;
                continue;
            }
            if (inSection && trimmed.startsWith("#")) {
                break;
            }
            if (inSection) {
                section.append(line).append('\n');
            }
        }
        return inSection && !section.toString().trim().isBlank();
    }

    private boolean jsonField(String content, String field) throws Exception {
        JsonNode root = objectMapper.readTree(content);
        return root != null && root.has(field) && isMeaningful(root.get(field));
    }

    private boolean jsonArrayMin(String content, String rule) throws Exception {
        String[] parts = rule.split(":", 2);
        if (parts.length != 2) {
            return false;
        }
        JsonNode root = objectMapper.readTree(content);
        if (root == null || !root.path(parts[0]).isArray()) {
            return false;
        }
        int required = Integer.parseInt(parts[1]);
        long meaningfulItems = root.path(parts[0]).valueStream().filter(this::isMeaningful).count();
        return required > 0 && meaningfulItems >= required;
    }

    private boolean isMeaningful(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return false;
        }
        if (node.isTextual()) {
            return !node.asText().trim().isBlank();
        }
        if (node.isArray()) {
            return node.valueStream().anyMatch(this::isMeaningful);
        }
        if (node.isObject()) {
            return node.fields().hasNext() && node.valueStream().anyMatch(this::isMeaningful);
        }
        return true;
    }

    private void validateSubmission(LearningTaskDefinition task, Map<String, String> files) {
        if (files.size() > MAX_FILES) {
            throw new IllegalArgumentException("Submission contains too many files.");
        }
        Set<String> allowedPaths = new HashSet<>();
        task.starterFiles().forEach(file -> allowedPaths.add(file.path()));
        task.checks().forEach(check -> allowedPaths.add(check.path()));

        int totalChars = 0;
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
            if (length > MAX_FILE_CHARS) {
                throw new IllegalArgumentException("Submission file is too large: " + path);
            }
            totalChars += length;
            if (totalChars > MAX_TOTAL_CHARS) {
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

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 160 ? message : message.substring(0, 157) + "...";
    }

    private TaskCheckResult result(TaskCheckDefinition definition, boolean passed, String evidence, String message) {
        return new TaskCheckResult(definition.id(), passed, definition.required(), definition.points(), evidence, message);
    }
}