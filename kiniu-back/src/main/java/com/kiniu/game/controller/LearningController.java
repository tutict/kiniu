package com.kiniu.game.controller;

import com.kiniu.game.ai.AIRequestConfig;
import com.kiniu.game.ai.AIRuntimeContext;
import com.kiniu.game.ai.AIService;
import com.kiniu.game.ai.AITelemetryCollector;
import com.kiniu.game.dto.LearningCatalogView;
import com.kiniu.game.dto.LearningCheckRequest;
import com.kiniu.game.dto.LearningCheckResponse;
import com.kiniu.game.dto.LearningFeedbackRequest;
import com.kiniu.game.dto.LearningFeedbackResponse;
import com.kiniu.game.dto.LearningPublishRequest;
import com.kiniu.game.dto.LearningPublishResponse;
import com.kiniu.game.dto.LearningTaskView;
import com.kiniu.game.learn.LearningAgentPublisher;
import com.kiniu.game.learn.LearningAttempt;
import com.kiniu.game.learn.LearningAttemptService;
import com.kiniu.game.learn.LearningCatalogService;
import com.kiniu.game.learn.LearningProgress;
import com.kiniu.game.learn.LearningProgressService;
import com.kiniu.game.learn.LearningTaskDefinition;
import com.kiniu.game.learn.QuizCheckService;
import com.kiniu.game.learn.TaskCheckResult;
import com.kiniu.game.learn.TaskCheckService;
import com.kiniu.game.security.ProviderUrlValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/learn")
public class LearningController {

    private final LearningCatalogService catalogService;
    private final LearningProgressService progressService;
    private final LearningAttemptService attemptService;
    private final LearningAgentPublisher agentPublisher;
    private final TaskCheckService checkService;
    private final QuizCheckService quizCheckService;
    private final AIService aiService;
    private final AIRuntimeContext aiRuntimeContext;
    private final AITelemetryCollector aiTelemetryCollector;
    private final ProviderUrlValidator providerUrlValidator;

    public LearningController(
            LearningCatalogService catalogService,
            LearningProgressService progressService,
            LearningAttemptService attemptService,
            LearningAgentPublisher agentPublisher,
            TaskCheckService checkService,
            QuizCheckService quizCheckService,
            AIService aiService,
            AIRuntimeContext aiRuntimeContext,
            AITelemetryCollector aiTelemetryCollector,
            ProviderUrlValidator providerUrlValidator) {
        this.catalogService = catalogService;
        this.progressService = progressService;
        this.attemptService = attemptService;
        this.agentPublisher = agentPublisher;
        this.checkService = checkService;
        this.quizCheckService = quizCheckService;
        this.aiService = aiService;
        this.aiRuntimeContext = aiRuntimeContext;
        this.aiTelemetryCollector = aiTelemetryCollector;
        this.providerUrlValidator = providerUrlValidator;
    }

    @GetMapping("/catalog")
    public LearningCatalogView catalog() {
        return LearningCatalogView.from(catalogService.getCatalog());
    }

    @PostMapping("/catalog/reload")
    public LearningCatalogView reloadCatalog() {
        return LearningCatalogView.from(catalogService.reload());
    }

    @GetMapping("/progress")
    public LearningProgress progress() {
        return progressService.getProgress();
    }

    @PostMapping("/progress/reset")
    public LearningProgress resetProgress() {
        return progressService.reset();
    }

    @GetMapping("/tasks/{taskId}")
    public LearningTaskView task(@PathVariable String taskId) {
        return LearningTaskView.from(catalogService.getTask(taskId));
    }

    @PostMapping("/tasks/{taskId}/check")
    public LearningCheckResponse check(
            @PathVariable String taskId,
            @RequestBody LearningCheckRequest request) {
        LearningTaskDefinition task = catalogService.getTask(taskId);
        LearningProgress before = progressService.getProgress();
        if (!catalogService.isUnlocked(taskId, before)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Complete the previous learning task before submitting this one.");
        }

        Map<String, String> files = request == null || request.files() == null ? Map.of() : request.files();
        Map<String, String> answers = request == null || request.answers() == null ? Map.of() : request.answers();
        boolean quiz = "quiz".equals(task.evidenceMode());
        List<TaskCheckResult> results = quiz
                ? quizCheckService.check(task, answers)
                : checkService.check(task, files);
        int score = quiz ? quizCheckService.score(results) : checkService.score(results);
        boolean passed = quiz ? quizCheckService.passed(task, results) : checkService.passed(results);
        LearningAttempt attempt = attemptService.record(
                taskId,
                passed,
                score,
                quiz ? answers : files,
                results,
                request == null ? "" : request.notes());
        LearningProgress progress = passed
                ? progressService.recordAttempt(taskId, score, task.skills())
                : before;
        return new LearningCheckResponse(
                attempt.attemptId(),
                taskId,
                passed,
                score,
                results,
                progress);
    }

    @PostMapping("/tasks/{taskId}/feedback")
    public LearningFeedbackResponse feedback(
            @PathVariable String taskId,
            @RequestBody LearningFeedbackRequest request,
            HttpServletRequest httpRequest) {
        LearningTaskDefinition task = catalogService.getTask(taskId);
        String attemptId = request == null ? "" : safe(request.attemptId());
        if (attemptId.isBlank()) {
            throw new IllegalArgumentException("Run a deterministic check before requesting mentor feedback.");
        }
        LearningAttempt attempt = attemptService.getForTask(attemptId, taskId);
        String question = request == null ? "" : safe(request.question());
        aiTelemetryCollector.clear();
        aiRuntimeContext.set(new AIRequestConfig(
                providerUrlValidator.validate(headerOrBlank(httpRequest, "X-Provider-Url")),
                resolveApiKey(httpRequest),
                headerOrBlank(httpRequest, "X-Model")));
        try {
            String feedback = aiService.generateLearningFeedback(task, attempt, question);
            return new LearningFeedbackResponse(
                    taskId,
                    attempt.attemptId(),
                    feedback,
                    "Mentor feedback explains deterministic evidence and never changes the score.");
        } finally {
            aiRuntimeContext.clear();
            aiTelemetryCollector.clear();
        }
    }

    @PostMapping("/tasks/{taskId}/publish-agent")
    public LearningPublishResponse publishAgent(
            @PathVariable String taskId,
            @RequestBody LearningPublishRequest request) {
        LearningTaskDefinition task = catalogService.getTask(taskId);
        String attemptId = request == null ? "" : safe(request.attemptId());
        if (attemptId.isBlank()) {
            throw new IllegalArgumentException("A passed attempt is required before publishing an Agent.");
        }
        if (!progressService.getProgress().completedTaskIds().contains(taskId)) {
            throw new IllegalArgumentException("Complete the learning task before publishing an Agent.");
        }
        LearningAttempt attempt = attemptService.getForTask(attemptId, taskId);
        LearningAgentPublisher.PublishedLearningAgent published = agentPublisher.publish(task, attempt);
        return new LearningPublishResponse(taskId, attemptId, published.agent(), published.catalog());
    }

    private String resolveApiKey(HttpServletRequest request) {
        String xApiKey = headerOrBlank(request, "X-API-Key");
        if (!xApiKey.isBlank()) {
            return xApiKey;
        }
        String authorization = headerOrBlank(request, "Authorization");
        if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return authorization;
    }

    private String headerOrBlank(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? "" : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
