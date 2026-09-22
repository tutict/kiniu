package com.kiniu.game.learn;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LearningCatalogService {

    private static final Set<String> SUPPORTED_EVIDENCE_MODES = Set.of("document", "import", "quiz");

    private final ObjectMapper objectMapper;
    private final Path catalogPath;
    private final TaskCheckRegistry taskCheckRegistry;
    private final LearningCatalogDefinition catalog;

    @Autowired
    public LearningCatalogService(
            ObjectMapper objectMapper,
            @Value("${game.learning.catalog-path:data/learning-catalog.json}") String catalogPath,
            TaskCheckRegistry taskCheckRegistry) {
        this.objectMapper = objectMapper;
        this.catalogPath = Paths.get(catalogPath).toAbsolutePath().normalize();
        this.taskCheckRegistry = taskCheckRegistry;
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
        String coreNext = firstUnlockedIncomplete(progress, false);
        return coreNext.isBlank() ? firstUnlockedIncomplete(progress, true) : coreNext;
    }

    private String firstUnlockedIncomplete(LearningProgress progress, boolean elective) {
        return flattenTasks().stream()
                .filter(task -> task.elective() == elective)
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
        } catch (JacksonException exception) {
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
        if (!taskIds.add(task.id()) || task.estimatedMinutes() <= 0
                || task.skills().isEmpty() || task.skills().stream().anyMatch(skill -> skill == null || skill.isBlank())) {
            throw new IllegalStateException("Learning task ids must be unique and tasks need skills and duration.");
        }
        if (!SUPPORTED_EVIDENCE_MODES.contains(task.evidenceMode())) {
            throw new IllegalStateException("Learning task evidence mode is unsupported: " + task.id());
        }
        if (modernCatalog) {
            requireText(task.lesson(), "Learning task lesson");
            requireText(task.tonightPrompt(), "Learning task tonight prompt");
            int tonightLength = task.tonightPrompt().length();
            if (tonightLength < 40 || tonightLength > 180) {
                throw new IllegalStateException("Learning task tonight prompt must be a sendable sentence: " + task.id());
            }
            requireText(task.takeaway(), "Learning task takeaway");
            int takeawayLength = task.takeaway().length();
            if (takeawayLength < 16 || takeawayLength > 80 || task.takeaway().indexOf('\n') >= 0) {
                throw new IllegalStateException("Learning task takeaway must be one sentence: " + task.id());
            }
            if (task.deliverables().isEmpty()
                    || task.deliverables().stream().anyMatch(item -> item == null || item.isBlank())) {
                throw new IllegalStateException("Learning tasks need non-blank deliverables.");
            }
            validateReferences(task);
        }
        if ("quiz".equals(task.evidenceMode())) {
            validateQuizTask(task);
            return;
        }
        if (!task.quizQuestions().isEmpty()) {
            throw new IllegalStateException("Only quiz tasks may contain quiz questions.");
        }
        if (task.starterFiles().isEmpty()) {
            throw new IllegalStateException("Learning task ids must be unique and tasks need starter files.");
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
            requireText(check.message(), "Learning check message");
            if (!checkIds.add(check.id()) || !starterPaths.contains(check.path()) || check.points() <= 0) {
                throw new IllegalStateException("Learning checks must use starter paths and positive points.");
            }
            taskCheckRegistry.validateRule(check);
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
                throw new IllegalStateException("Every modern catalog task after the first needs prerequisites.");
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

    private void validateQuizTask(LearningTaskDefinition task) {
        if (!task.starterFiles().isEmpty() || !task.checks().isEmpty()) {
            throw new IllegalStateException("Quiz tasks must not include starter files or document checks.");
        }
        if (task.quizQuestions().isEmpty()) {
            throw new IllegalStateException("Quiz tasks must contain questions.");
        }
        if (task.passingScore() < 1 || task.passingScore() > 100) {
            throw new IllegalStateException("Quiz passing score must be between 1 and 100.");
        }
        Set<String> questionIds = new HashSet<>();
        int points = 0;
        for (LearningQuizQuestion question : task.quizQuestions()) {
            requireText(question.id(), "Learning quiz question id");
            requireText(question.prompt(), "Learning quiz question prompt");
            requireText(question.correctOptionId(), "Learning quiz correct option");
            requireText(question.explanation(), "Learning quiz explanation");
            if (!questionIds.add(question.id()) || question.points() <= 0 || question.options().size() < 2) {
                throw new IllegalStateException("Quiz questions must have unique ids, points, and at least two options.");
            }
            Set<String> optionIds = new HashSet<>();
            question.options().forEach(option -> {
                requireText(option.id(), "Learning quiz option id");
                requireText(option.label(), "Learning quiz option label");
                if (!optionIds.add(option.id())) {
                    throw new IllegalStateException("Quiz options must have unique ids.");
                }
            });
            if (!optionIds.contains(question.correctOptionId())) {
                throw new IllegalStateException("Quiz correct option must match an available choice.");
            }
            points += question.points();
        }
        if (points != 100) {
            throw new IllegalStateException("Quiz question points must total 100.");
        }
    }

    private void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + " must not be blank.");
        }
    }
}
