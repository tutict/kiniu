package com.kiniu.game.learn;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LearningCatalogService {

    private static final Set<String> SUPPORTED_CHECK_TYPES = Set.of(
            "contains",
            "markdown-section",
            "regex",
            "json-field",
            "json-array-min",
            "min-length");

    private final ObjectMapper objectMapper;
    private final Path catalogPath;
    private final LearningCatalogDefinition catalog;

    @Autowired
    public LearningCatalogService(
            ObjectMapper objectMapper,
            @Value("${game.learning.catalog-path:data/learning-catalog.json}") String catalogPath) {
        this.objectMapper = objectMapper;
        this.catalogPath = Paths.get(catalogPath).toAbsolutePath().normalize();
        this.catalog = loadCatalog();
        validateCatalog(this.catalog);
    }

    public LearningCatalogDefinition getCatalog() {
        return catalog;
    }

    public LearningTaskDefinition getTask(String taskId) {
        return catalog.modules().stream()
                .flatMap(module -> module.tasks().stream())
                .filter(task -> task.id().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown learning task: " + taskId));
    }

    public String firstTaskId() {
        return flattenTasks().get(0).id();
    }

    public boolean isUnlocked(String taskId, LearningProgress progress) {
        List<LearningTaskDefinition> tasks = flattenTasks();
        int index = indexOf(tasks, taskId);
        if (index < 0) {
            return false;
        }
        if (progress != null && progress.completedTaskIds().contains(taskId)) {
            return true;
        }
        return index == 0 || progress != null && progress.completedTaskIds().contains(tasks.get(index - 1).id());
    }

    public String nextTaskId(String completedTaskId) {
        List<LearningTaskDefinition> tasks = flattenTasks();
        int index = indexOf(tasks, completedTaskId);
        return index >= 0 && index + 1 < tasks.size() ? tasks.get(index + 1).id() : completedTaskId;
    }

    public List<String> unlockedTaskIds(LearningProgress progress) {
        return flattenTasks().stream()
                .filter(task -> isUnlocked(task.id(), progress))
                .map(LearningTaskDefinition::id)
                .toList();
    }

    private List<LearningTaskDefinition> flattenTasks() {
        return catalog.modules().stream()
                .flatMap(module -> module.tasks().stream())
                .toList();
    }

    private int indexOf(List<LearningTaskDefinition> tasks, String taskId) {
        for (int index = 0; index < tasks.size(); index++) {
            if (tasks.get(index).id().equals(taskId)) {
                return index;
            }
        }
        return -1;
    }

    private LearningCatalogDefinition loadCatalog() {
        try {
            if (!Files.exists(catalogPath)) {
                throw new IllegalStateException("Learning catalog does not exist: " + catalogPath);
            }
            return objectMapper.readValue(catalogPath.toFile(), LearningCatalogDefinition.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load learning catalog from " + catalogPath, exception);
        }
    }

    private void validateCatalog(LearningCatalogDefinition definition) {
        if (definition == null || definition.version() <= 0 || definition.modules().isEmpty()) {
            throw new IllegalStateException("Learning catalog must contain a positive version and modules.");
        }
        Set<String> moduleIds = new HashSet<>();
        Set<String> taskIds = new HashSet<>();
        definition.modules().forEach(module -> {
            requireText(module.id(), "Learning module id");
            requireText(module.title(), "Learning module title");
            requireText(module.summary(), "Learning module summary");
            requireText(module.level(), "Learning module level");
            if (!moduleIds.add(module.id()) || module.tasks().isEmpty()) {
                throw new IllegalStateException("Learning module ids must be unique and modules must contain tasks.");
            }
            module.tasks().forEach(task -> validateTask(task, taskIds));
        });
    }

    private void validateTask(LearningTaskDefinition task, Set<String> taskIds) {
        requireText(task.id(), "Learning task id");
        requireText(task.title(), "Learning task title");
        requireText(task.summary(), "Learning task summary");
        requireText(task.level(), "Learning task level");
        requireText(task.kind(), "Learning task kind");
        requireText(task.objective(), "Learning task objective");
        requireText(task.scenario(), "Learning task scenario");
        requireText(task.mentorAgentId(), "Learning task mentor Agent id");
        if (!taskIds.add(task.id()) || task.estimatedMinutes() <= 0 || task.starterFiles().isEmpty()
                || task.skills().isEmpty() || task.skills().stream().anyMatch(skill -> skill == null || skill.isBlank())) {
            throw new IllegalStateException("Learning task ids must be unique and tasks need starter files.");
        }

        Set<String> starterPaths = new HashSet<>();
        task.starterFiles().forEach(file -> {
            requireText(file.path(), "Learning starter file path");
            if (!starterPaths.add(file.path()) || file.content() == null
                    || file.content().length() > TaskCheckService.MAX_FILE_CHARS) {
                throw new IllegalStateException("Learning starter files must have unique safe content.");
            }
        });

        if (task.checks().isEmpty()) {
            throw new IllegalStateException("Learning tasks must contain checks.");
        }
        Set<String> checkIds = new HashSet<>();
        task.checks().forEach(check -> {
            requireText(check.id(), "Learning check id");
            requireText(check.type(), "Learning check type");
            requireText(check.path(), "Learning check path");
            requireText(check.rule(), "Learning check rule");
            requireText(check.message(), "Learning check message");
            if (!checkIds.add(check.id()) || !SUPPORTED_CHECK_TYPES.contains(check.type())
                    || !starterPaths.contains(check.path()) || check.points() <= 0) {
                throw new IllegalStateException("Learning checks must use supported types, starter paths, and positive points.");
            }
            validateRule(check);
        });
        boolean hasRequired = task.checks().stream().anyMatch(TaskCheckDefinition::required);
        if (!hasRequired) {
            throw new IllegalStateException("Learning tasks must contain at least one required check.");
        }
    }

    private void validateRule(TaskCheckDefinition check) {
        try {
            switch (check.type()) {
                case "regex" -> {
                    if (check.rule().length() > 500) {
                        throw new IllegalArgumentException("Regex rule too long");
                    }
                    Pattern.compile(check.rule());
                }
                case "json-array-min" -> {
                    String[] parts = check.rule().split(":", 2);
                    if (parts.length != 2 || Integer.parseInt(parts[1]) <= 0) {
                        throw new IllegalArgumentException("Invalid array minimum rule");
                    }
                }
                case "min-length" -> {
                    if (Integer.parseInt(check.rule()) <= 0) {
                        throw new IllegalArgumentException("Invalid minimum length rule");
                    }
                }
                default -> {
                    // contains, markdown-section and json-field use non-blank rules.
                }
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid learning check rule: " + check.id(), exception);
        }
    }

    private void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + " must not be blank.");
        }
    }
}