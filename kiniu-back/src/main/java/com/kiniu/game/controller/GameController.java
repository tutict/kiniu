package com.kiniu.game.controller;

import com.kiniu.game.agent.AgentManager;
import com.kiniu.game.ai.AIRequestConfig;
import com.kiniu.game.ai.AIRuntimeContext;
import com.kiniu.game.ai.AITelemetryCollector;
import com.kiniu.game.dto.AgentCatalogResponse;
import com.kiniu.game.dto.GameRequest;
import com.kiniu.game.dto.GameResponse;
import com.kiniu.game.dto.SandboxPlanRequest;
import com.kiniu.game.dto.SessionExportResponse;
import com.kiniu.game.dto.StoryAnalysisRequest;
import com.kiniu.game.dto.StoryAnalysisResponse;
import com.kiniu.game.dto.StoryCatalogResponse;
import com.kiniu.game.dto.StoryGenerationRequest;
import com.kiniu.game.dto.StoryGenerationResponse;
import com.kiniu.game.engine.GameEngine;
import com.kiniu.game.engine.SessionArchiveService;
import com.kiniu.game.story.StoryCompilerService;
import com.kiniu.game.story.StoryEngine;
import com.kiniu.game.story.StoryGeneratorService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping({"/game", "/agent"})
public class GameController {

    private final GameEngine gameEngine;
    private final StoryEngine storyEngine;
    private final StoryCompilerService storyCompilerService;
    private final StoryGeneratorService storyGeneratorService;
    private final AgentManager agentManager;
    private final SessionArchiveService sessionArchiveService;
    private final AIRuntimeContext aiRuntimeContext;
    private final AITelemetryCollector aiTelemetryCollector;

    public GameController(
            GameEngine gameEngine,
            StoryEngine storyEngine,
            StoryCompilerService storyCompilerService,
            StoryGeneratorService storyGeneratorService,
            AgentManager agentManager,
            SessionArchiveService sessionArchiveService,
            AIRuntimeContext aiRuntimeContext,
            AITelemetryCollector aiTelemetryCollector) {
        this.gameEngine = gameEngine;
        this.storyEngine = storyEngine;
        this.storyCompilerService = storyCompilerService;
        this.storyGeneratorService = storyGeneratorService;
        this.agentManager = agentManager;
        this.sessionArchiveService = sessionArchiveService;
        this.aiRuntimeContext = aiRuntimeContext;
        this.aiTelemetryCollector = aiTelemetryCollector;
    }

    @PostMapping("/next")
    public GameResponse next(@RequestBody GameRequest request, HttpServletRequest httpRequest) {
        aiTelemetryCollector.clear();
        aiRuntimeContext.set(new AIRequestConfig(
                headerOrBlank(httpRequest, "X-Provider-Url"),
                resolveApiKey(httpRequest),
                headerOrBlank(httpRequest, "X-Model")));
        try {
            return gameEngine.next(request);
        } finally {
            aiRuntimeContext.clear();
            aiTelemetryCollector.clear();
        }
    }

    @GetMapping("/story")
    public StoryCatalogResponse storyCatalog() {
        return storyEngine.getStoryCatalog();
    }

    @PutMapping("/story")
    public StoryCatalogResponse saveStoryCatalog(@RequestBody StoryCatalogResponse request) {
        return storyEngine.saveStoryCatalog(request);
    }

    @PostMapping("/story/analyze")
    public StoryAnalysisResponse analyzeStoryCatalog(@RequestBody StoryAnalysisRequest request) {
        AgentCatalogResponse agentCatalog = request.agents() == null ? agentManager.getAgentCatalog() : request.agents();
        return storyCompilerService.analyze(request.story(), agentCatalog);
    }

    @PostMapping("/story/generate")
    public StoryGenerationResponse generateStoryCatalog(@RequestBody StoryGenerationRequest request) {
        return storyGeneratorService.generate(request);
    }

    @GetMapping("/agents")
    public AgentCatalogResponse agentCatalog() {
        return agentManager.getAgentCatalog();
    }

    @PutMapping("/agents")
    public AgentCatalogResponse saveAgentCatalog(@RequestBody AgentCatalogResponse request) {
        return agentManager.saveAgentCatalog(request);
    }

    @GetMapping("/export/{sessionId}")
    public SessionExportResponse exportSession(@PathVariable String sessionId) {
        return sessionArchiveService.getSessionExport(sessionId);
    }

    @PostMapping("/export/{sessionId}/sandbox-plans")
    public SessionExportResponse saveSandboxPlan(@PathVariable String sessionId, @RequestBody SandboxPlanRequest request) {
        return sessionArchiveService.saveSandboxPlan(sessionId, request);
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
}
