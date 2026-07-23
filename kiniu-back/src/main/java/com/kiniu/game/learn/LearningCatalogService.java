package com.kiniu.game.learn;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
            "json-pointer-present",
            "json-array-shape",
            "json-number-range",
            "min-length");
    private static final Set<String> SUPPORTED_EVIDENCE_MODES = Set.of("document", "import");

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
        LearningTaskDefinition task = tasks.get(index);
        if (!task.prerequisiteTaskIds().isEmpty()) {
            return progress != null && progress.completedTaskIds().containsAll(task.prerequisiteTaskIds());
        }
        return index == 0 || progress != null && progress.completedTaskIds().contains(tasks.get(index - 1).id());
    }

    public String nextTaskId(String completedTaskId) {
        List<LearningTaskDefinition> tasks = flattenTasks();
        int index = indexOf(tasks, completedTaskId);
        return index >= 0 && index + 1 < tasks.size() ? tasks.get(index + 1).id() : completedTaskId;
    }

    public String nextTaskId(String completedTaskId, LearningProgress progress) {
        return flattenTasks().stream()
                .filter(task -> !progress.completedTaskIds().contains(task.id()))
                .filter(task -> isUnlocked(task.id(), progress))
                .map(LearningTaskDefinition::id)
                .findFirst()
                .orElse("");
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
        boolean modernCatalog = definition.version() >= 3;
        definition.modules().forEach(module -> {
            requireText(module.id(), "Learning module id");
            requireText(module.title(), "Learning module title");
            requireText(module.summary(), "Learning module summary");
            requireText(module.level(), "Learning module level");
            if (!moduleIds.add(module.id()) || module.tasks().isEmpty()) {
                throw new IllegalStateException("Learning module ids must be unique and modules must contain tasks.");
            }
            module.tasks().forEach(task -> validateTask(task, taskIds, modernCatalog));
        });
        validatePrerequisites(taskIds, modernCatalog);
    }

    private void validateTask(LearningTaskDefinition task, Set<String> taskIds, boolean modernCatalog) {
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
        if (!SUPPORTED_EVIDENCE_MODES.contains(task.evidenceMode())) {
            throw new IllegalStateException("Learning task evidence mode is unsupported: " + task.id());
        }
        if (modernCatalog) {
            requireText(task.lesson(), "Learning task lesson");
            if (task.deliverables().isEmpty()
                    || task.deliverables().stream().anyMatch(item -> item == null || item.isBlank())) {
                throw new IllegalStateException("Learning tasks need non-blank deliverables.");
            }
            validateReferences(task);
        }

        Set<String> starterPaths = new HashSet<>();
        task.starterFiles().forEach(file -> {
            requireText(file.path(), "Learning starter file path");
            if (!starterPaths.add(file.path()) || file.content() == null
                    || file.content().length() > TaskCheckService.MAX_FILE_CHARS
                    || file.content().getBytes(StandardCharsets.UTF_8).length > TaskCheckService.MAX_FILE_BYTES) {
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
        if (task.checks().stream().mapToInt(TaskCheckDefinition::points).sum() != 100) {
            throw new IllegalStateException("Learning task checks must total 100 points.");
        }
        boolean hasRequired = task.checks().stream().anyMatch(TaskCheckDefinition::required);
        if (!hasRequired) {
            throw new IllegalStateException("Learning tasks must contain at least one required check.");
        }
    }

    private void validateReferences(LearningTaskDefinition task) {
        if (task.references().isEmpty()) {
            throw new IllegalStateException("Learning tasks need official references.");
        }
        task.references().forEach(reference -> {
            requireText(reference.title(), "Learning reference title");
            requireText(reference.publisher(), "Learning reference publisher");
            requireText(reference.version(), "Learning reference version");
            requireText(reference.accessedAt(), "Learning reference accessedAt");
            try {
                URI uri = URI.create(reference.url());
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
                    throw new IllegalArgumentException("Reference must use HTTPS");
                }
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Invalid learning reference URL for " + task.id(), exception);
            }
        });
    }

    private void validatePrerequisites(Set<String> taskIds, boolean modernCatalog) {
        List<LearningTaskDefinition> tasks = flattenTasks();
        Map<String, List<String>> dependencies = new HashMap<>();
        for (int index = 0; index < tasks.size(); index++) {
            LearningTaskDefinition task = tasks.get(index);
            Set<String> unique = new LinkedHashSet<>(task.prerequisiteTaskIds());
            if (unique.size() != task.prerequisiteTaskIds().size()
                    || unique.contains(task.id())
                    || !taskIds.containsAll(unique)) {
                throw new IllegalStateException("Learning task prerequisites must be unique, known, and non-self references.");
            }
            if (modernCatalog && index > 0 && unique.isEmpty()) {
                throw new IllegalStateException("Every version 3 task after the first needs prerequisites.");
            }
            dependencies.put(task.id(), List.copyOf(unique));
        }

        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String taskId : taskIds) {
            visitDependency(taskId, dependencies, visiting, visited);
        }
    }

    private void visitDependency(
            String taskId,
            Map<String, List<String>> dependencies,
            Set<String> visiting,
            Set<String> visited) {
        if (visited.contains(taskId)) {
            return;
        }
        if (!visiting.add(taskId)) {
            throw new IllegalStateException("Learning task prerequisites must not contain cycles.");
        }
        dependencies.getOrDefault(taskId, List.of())
                .forEach(dependency -> visitDependency(dependency, dependencies, visiting, visited));
        visiting.remove(taskId);
        visited.add(taskId);
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
                case "json-pointer-present" -> {
                    JsonPointer.compile(check.rule());
                }
                case "json-array-shape" -> {
                    String[] parts = check.rule().split(":", 3);
                    if (parts.length != 3 || Integer.parseInt(parts[1]) <= 0 || parts[2].isBlank()) {
                        throw new IllegalArgumentException("Invalid array shape rule");
                    }
                    JsonPointer.compile(parts[0]);
                }
                case "json-number-range" -> {
                    String[] parts = check.rule().split(":", 3);
                    if (parts.length != 3
                            || new java.math.BigDecimal(parts[1]).compareTo(new java.math.BigDecimal(parts[2])) > 0) {
                        throw new IllegalArgumentException("Invalid number range rule");
                    }
                    JsonPointer.compile(parts[0]);
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
