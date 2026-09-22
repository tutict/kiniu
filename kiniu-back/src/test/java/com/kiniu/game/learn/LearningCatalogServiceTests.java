package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LearningCatalogServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void shouldUnlockTasksSequentially() throws Exception {
        LearningCatalogService service = serviceWith(task("first", "min-length"), task("second", "min-length"));

        assertTrue(service.isUnlocked("first", LearningProgress.empty("first")));
        assertFalse(service.isUnlocked("second", LearningProgress.empty("first")));

        LearningProgress completedFirst = LearningProgress.empty("first")
                .record("first", 100, List.of(), "second");
        assertTrue(service.isUnlocked("second", completedFirst));
    }

    @Test
    void shouldRejectUnsupportedCheckTypes() throws Exception {
        assertThrows(IllegalStateException.class, () -> serviceWith(task("first", "unknown-check")));
    }

    @Test
    void shouldAcceptFrontmatterRegexCheckTypes() throws Exception {
        serviceWith(task("first", "frontmatter-regex"));
    }

    @Test
    void shouldRejectTasksWithoutRequiredChecks() throws Exception {
        LearningTaskDefinition task = new LearningTaskDefinition(
                "first",
                "First",
                "Summary",
                "beginner",
                "requirements",
                10,
                List.of("requirements"),
                "Objective",
                "Scenario",
                "project-agent",
                List.of(new LearningFileView("artifact.md", "starter")),
                List.of(new TaskCheckDefinition("optional", "min-length", "artifact.md", "3", false, 10, "Optional")));

        assertThrows(IllegalStateException.class, () -> serviceWith(task));
    }

    @Test
    void shouldRejectTasksWhoseChecksDoNotTotalOneHundredPoints() {
        assertThrows(IllegalStateException.class, () -> serviceWith(taskWithPoints("first", 99)));
    }

    @Test
    void shouldRejectStarterFilesThatExceedTheUtf8ByteLimit() {
        String oversizedInBytes = "学".repeat((TaskCheckService.MAX_FILE_BYTES / 3) + 1);
        assertTrue(oversizedInBytes.length() < TaskCheckService.MAX_FILE_CHARS);
        assertThrows(IllegalStateException.class, () -> serviceWith(taskWithStarterContent("first", oversizedInBytes)));
    }

    @Test
    void shouldUnlockBranchesOnlyAfterAllExplicitPrerequisitesAreComplete() throws Exception {
        LearningCatalogService service = serviceWith(
                task("first", "min-length"),
                taskWithPrerequisites("branch-a", List.of("first")),
                taskWithPrerequisites("branch-b", List.of("first")),
                taskWithPrerequisites("join", List.of("branch-a", "branch-b")));

        LearningProgress afterFirst = LearningProgress.empty("first")
                .record("first", 100, List.of(), "branch-a");
        assertTrue(service.isUnlocked("branch-a", afterFirst));
        assertTrue(service.isUnlocked("branch-b", afterFirst));
        assertFalse(service.isUnlocked("join", afterFirst));

        LearningProgress afterOneBranch = afterFirst.record("branch-a", 100, List.of(), "branch-b");
        assertFalse(service.isUnlocked("join", afterOneBranch));
        LearningProgress afterBothBranches = afterOneBranch.record("branch-b", 100, List.of(), "join");
        assertTrue(service.isUnlocked("join", afterBothBranches));
    }

    @Test
    void shouldRejectUnknownAndCyclicPrerequisites() {
        assertThrows(
                IllegalStateException.class,
                () -> serviceWith(taskWithPrerequisites("first", List.of("missing"))));
        assertThrows(
                IllegalStateException.class,
                () -> serviceWith(
                        taskWithPrerequisites("first", List.of("second")),
                        taskWithPrerequisites("second", List.of("first"))));
    }

    @Test
    void shouldReturnNoNextTaskWhenEverythingIsComplete() throws Exception {
        LearningCatalogService service = serviceWith(task("first", "min-length"), task("second", "min-length"));
        LearningProgress completed = LearningProgress.empty("first")
                .record("first", 100, List.of(), "second")
                .record("second", 100, List.of(), "second");

        assertEquals("", service.nextTaskId("second", completed));
    }

    @Test
    void productionCatalogShouldExposeVersionFourCurriculum() {
        LearningCatalogService service = new LearningCatalogService(
                objectMapper,
                Path.of("data", "learning-catalog.json").toString(),
                TaskCheckRegistryTestFactory.standard(objectMapper));
        LearningCatalogDefinition catalog = service.getCatalog();
        List<LearningTaskDefinition> tasks = catalog.modules().stream()
                .flatMap(module -> module.tasks().stream())
                .toList();

        assertEquals(4, catalog.version());
        assertEquals(8, catalog.modules().size());
        assertEquals(21, tasks.size());
        assertEquals(
                List.of(
                        "foundations",
                        "context-data",
                        "agent-systems",
                        "evaluation-agent",
                        "rag-engineering",
                        "genai-safety",
                        "protocol-interop",
                        "production-engineering"),
                catalog.modules().stream().map(LearningModuleDefinition::id).toList());
        assertEquals(
                List.of("evaluation-suite", "companion-agent"),
                catalog.modules().get(3).tasks().stream().map(LearningTaskDefinition::id).toList());
        assertEquals(
                List.of("observability-runbook", "release-safety", "architecture-collaboration"),
                catalog.modules().get(7).tasks().stream().map(LearningTaskDefinition::id).toList());
        assertTrue(tasks.stream().allMatch(task -> task.lesson().length() >= 300 && task.lesson().length() <= 600));
        assertEquals(tasks.size(), tasks.stream().map(LearningTaskDefinition::tonightPrompt).distinct().count());
        assertTrue(tasks.stream().allMatch(task -> {
            String prompt = task.tonightPrompt();
            return prompt.length() >= 40 && prompt.length() <= 180 && prompt.contains("今晚");
        }));
        assertEquals(
                "今晚我加班回家了。请只看我贴的待办，给最多 3 条下一步，不确定的标出来。不要改日历，不要发消息，也不要下单。待办：回客户邮件，买早餐，准备周会。",
                taskById(tasks, "requirements-contract").tonightPrompt());
        assertEquals(
                "先写清帮谁、做成什么样、不能做什么，再让它动手。",
                taskById(tasks, "requirements-contract").takeaway());
        assertEquals(tasks.size(), tasks.stream().map(LearningTaskDefinition::takeaway).distinct().count());
        assertTrue(tasks.stream().allMatch(task -> {
            String takeaway = task.takeaway();
            return takeaway.length() >= 16
                    && takeaway.length() <= 80
                    && !takeaway.contains("\n")
                    && !takeaway.contains("今晚");
        }));
        assertTrue(tasks.stream().map(LearningTaskDefinition::takeaway).noneMatch(takeaway ->
                takeaway.chars().anyMatch(ch -> ch < 128 && Character.isLetter(ch))));
        assertTrue(tasks.stream().allMatch(task -> task.references().size() >= 1 && task.references().size() <= 3));
        assertTrue(tasks.stream().allMatch(task -> "quiz".equals(task.evidenceMode()) && task.passingScore() == 80));
        assertEquals(
                List.of(
                        "a2a-collaboration",
                        "observability-runbook",
                        "release-safety",
                        "architecture-collaboration"),
                tasks.stream().filter(LearningTaskDefinition::elective).map(LearningTaskDefinition::id).toList());
        assertTrue(tasks.stream()
                .flatMap(task -> task.skills().stream())
                .noneMatch(skill -> skill.chars().anyMatch(ch -> ch < 128 && Character.isLetter(ch))));
        assertTrue(tasks.stream().allMatch(task -> {
            List<String> labels = task.quizQuestions().stream()
                    .flatMap(question -> question.options().stream())
                    .map(LearningQuizOption::label)
                    .toList();
            return labels.size() == Set.copyOf(labels).size();
        }));
        assertTrue(tasks.stream().allMatch(task -> {
            String learnerText = task.lesson() + task.title() + task.summary() + task.scenario() + task.objective()
                    + task.tonightPrompt()
                    + task.takeaway()
                    + String.join("", task.deliverables())
                    + task.quizQuestions().stream()
                    .flatMap(question -> java.util.stream.Stream.concat(
                            java.util.stream.Stream.of(question.prompt(), question.explanation()),
                            question.options().stream().map(LearningQuizOption::label)))
                    .reduce("", String::concat);
            return java.util.stream.Stream.of(
                            "PKCE", "OAuth", "ACL", "idempotency", "documentId", "requestId", "capturedAt",
                            "HTTP", "JSON", "网关", "状态码", "调用方", "正则", "解析", "请求编号",
                            "幂等", "租户", "契约", "注入", "脱敏", "评测集", "红队", "投毒", "哈希", "隔离", "实验室", "接口")
                    .noneMatch(term -> learnerText.toLowerCase().contains(term.toLowerCase()));
        }));
        assertEquals(
                List.of("资料还是工具", "令牌不能转发", "写入先问"),
                taskById(tasks, "mcp-integration").skills());
        assertTrue(tasks.stream().allMatch(task -> {
            if ("quiz".equals(task.evidenceMode())) {
                return task.checks().isEmpty()
                        && task.starterFiles().isEmpty()
                        && task.quizQuestions().stream().mapToInt(LearningQuizQuestion::points).sum() == 100;
            }
            return task.quizQuestions().isEmpty()
                    && task.checks().stream().mapToInt(TaskCheckDefinition::points).sum() == 100;
        }));
        LearningTaskDefinition requirements = taskById(tasks, "requirements-contract");
        assertEquals("quiz", requirements.evidenceMode());
        assertEquals(10, requirements.quizQuestions().size());
        assertEquals(
                List.of(
                        "first-move",
                        "user-scope",
                        "out-of-scope-user",
                        "business-outcome",
                        "input-ambiguity",
                        "data-boundary",
                        "risk-boundary",
                        "irreversible-request",
                        "missing-owner",
                        "acceptance-criteria"),
                requirements.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("define-contract", requirements.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition httpJson = taskById(tasks, "http-json-basics");
        assertEquals("quiz", httpJson.evidenceMode());
        assertEquals(10, httpJson.quizQuestions().size());
        assertEquals(
                List.of(
                        "method-choice",
                        "path-resource",
                        "json-role",
                        "status-contract",
                        "timeout-semantics",
                        "client-error",
                        "auth-error",
                        "idempotency-key",
                        "retry-safety",
                        "secret-boundary"),
                httpJson.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("post-plan", httpJson.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition modelContract = taskById(tasks, "model-response-contract");
        assertEquals("quiz", modelContract.evidenceMode());
        assertEquals(10, modelContract.quizQuestions().size());
        assertEquals(
                List.of(
                        "why-schema",
                        "required-fields",
                        "refusal-branch",
                        "three-failures",
                        "schema-not-truth",
                        "sampling-variance",
                        "run-metadata",
                        "numeric-fields",
                        "parsed-result",
                        "secret-prompt"),
                modelContract.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("shared-contract", modelContract.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition contextDesign = taskById(tasks, "prompt-context-design");
        assertEquals("quiz", contextDesign.evidenceMode());
        assertEquals(10, contextDesign.quizQuestions().size());
        assertEquals(
                List.of(
                        "layer-roles",
                        "priority",
                        "untrusted-data",
                        "conflict-calendar",
                        "injection",
                        "ignore-phrase",
                        "token-budget",
                        "dump-history",
                        "insufficient-evidence",
                        "separator"),
                contextDesign.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("stable-split", contextDesign.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition dataLifecycle = taskById(tasks, "data-lifecycle");
        assertEquals("quiz", dataLifecycle.evidenceMode());
        assertEquals(10, dataLifecycle.quizQuestions().size());
        assertEquals(
                List.of(
                        "minimize",
                        "entities",
                        "pii-paste",
                        "embeddings",
                        "retention",
                        "delete-propagate",
                        "versioning",
                        "rename-not-migration",
                        "migration-fail",
                        "eval-samples"),
                dataLifecycle.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("needed-entities", dataLifecycle.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition memoryBudget = taskById(tasks, "context-memory-budget");
        assertEquals("quiz", memoryBudget.evidenceMode());
        assertEquals(10, memoryBudget.quizQuestions().size());
        assertEquals(
                List.of(
                        "two-stores",
                        "write-gate",
                        "pii-phone",
                        "stale-pref",
                        "similarity-trap",
                        "pin-rules",
                        "compaction",
                        "last-n",
                        "forever-chat",
                        "delete-memory"),
                memoryBudget.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("working-only", memoryBudget.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition workflowDecision = taskById(tasks, "workflow-agent-decision");
        assertEquals("quiz", workflowDecision.evidenceMode());
        assertEquals(10, workflowDecision.quizQuestions().size());
        assertEquals(
                List.of(
                        "when-workflow",
                        "when-agent",
                        "plan-next-shape",
                        "calendar-autonomy",
                        "not-just-turns",
                        "no-demo-autonomy",
                        "stop-condition",
                        "human-handoff",
                        "dag-vs-search",
                        "compose"),
                workflowDecision.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("stable-rules", workflowDecision.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition toolContract = taskById(tasks, "tool-contract");
        assertEquals("quiz", toolContract.evidenceMode());
        assertEquals(10, toolContract.quizQuestions().size());
        assertEquals(
                List.of(
                        "namespace",
                        "schema",
                        "authorization",
                        "timeout",
                        "idempotency",
                        "error-recovery",
                        "confirmation",
                        "write-tool",
                        "tool-over",
                        "compose"),
                toolContract.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("stable", toolContract.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition skillAuthoringQuiz = taskById(tasks, "agent-skill-authoring");
        assertEquals("quiz", skillAuthoringQuiz.evidenceMode());
        assertEquals(10, skillAuthoringQuiz.quizQuestions().size());
        assertEquals(
                List.of(
                        "what-is-skill",
                        "name-format",
                        "description-trigger",
                        "progressive-disclosure",
                        "split-files",
                        "when-to-use",
                        "workflow-steps",
                        "boundaries",
                        "third-party",
                        "evaluation"),
                skillAuthoringQuiz.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("reusable-pack", skillAuthoringQuiz.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition traceRecovery = taskById(tasks, "agent-trace-recovery");
        assertEquals("quiz", traceRecovery.evidenceMode());
        assertEquals(10, traceRecovery.quizQuestions().size());
        assertEquals(
                List.of(
                        "what-is-trace",
                        "trace-ids",
                        "step-shape",
                        "redact-trace",
                        "timeout-write",
                        "failure-class",
                        "idempotent-replay",
                        "no-infinite-retry",
                        "stop-budget",
                        "human-handoff"),
                traceRecovery.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("state-effects", traceRecovery.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition evaluationSuite = taskById(tasks, "evaluation-suite");
        assertEquals("quiz", evaluationSuite.evidenceMode());
        assertEquals(10, evaluationSuite.quizQuestions().size());
        assertEquals(
                List.of(
                        "not-an-exam",
                        "case-mix",
                        "three-graders",
                        "self-grade",
                        "holdout",
                        "three-trials",
                        "golden-path",
                        "gate",
                        "run-evidence",
                        "pii-cases"),
                evaluationSuite.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("feedback-loop", evaluationSuite.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition companion = taskById(tasks, "companion-agent");
        assertEquals("quiz", companion.evidenceMode());
        assertEquals("agent-project", companion.kind());
        assertEquals(10, companion.quizQuestions().size());
        assertEquals(
                List.of(
                        "identity",
                        "goals",
                        "boundaries",
                        "personality-vs-policy",
                        "memory-policy",
                        "failure-policy",
                        "eval-mix",
                        "priority",
                        "publish-meaning",
                        "high-risk"),
                companion.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("evening-planner", companion.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition ragPipeline = taskById(tasks, "rag-pipeline");
        assertEquals("quiz", ragPipeline.evidenceMode());
        assertEquals(10, ragPipeline.quizQuestions().size());
        assertEquals(
                List.of(
                        "trust-before-similarity",
                        "tenant-isolation",
                        "chunking",
                        "metadata",
                        "hybrid",
                        "rerank",
                        "citations",
                        "empty-fallback",
                        "conflict",
                        "run-record"),
                ragPipeline.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("source-trust", ragPipeline.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition ragEval = taskById(tasks, "rag-evaluation");
        assertEquals("quiz", ragEval.evidenceMode());
        assertEquals(10, ragEval.quizQuestions().size());
        assertEquals(
                List.of(
                        "three-metrics",
                        "fluency-not-grounded",
                        "recall-at-k",
                        "ndcg-position",
                        "query-mix",
                        "no-answer",
                        "cross-tenant-fail",
                        "no-circular-label",
                        "thresholds",
                        "per-query"),
                ragEval.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("hit-ground-cite", ragEval.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition redTeam = taskById(tasks, "genai-red-team");
        assertEquals("quiz", redTeam.evidenceMode());
        assertEquals(10, redTeam.quizQuestions().size());
        assertEquals(
                List.of(
                        "prompt-not-enough",
                        "direct-injection",
                        "indirect-injection",
                        "disclosure",
                        "output-injection",
                        "excessive-agency",
                        "vector-poisoning",
                        "unbounded-consumption",
                        "one-refusal",
                        "residual-owner"),
                redTeam.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("not-enough", redTeam.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition accessConcurrency = taskById(tasks, "access-concurrency");
        assertEquals("quiz", accessConcurrency.evidenceMode());
        assertEquals(10, accessConcurrency.quizQuestions().size());
        assertEquals(
                List.of(
                        "authn-authz",
                        "no-escalate",
                        "api-key-not-acl",
                        "downstream-identity",
                        "idempotency-retry",
                        "optimistic-lock",
                        "lock-not-idempotent",
                        "rate-and-budget",
                        "confirmation",
                        "audit"),
                accessConcurrency.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("who-what", accessConcurrency.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition mcp = taskById(tasks, "mcp-integration");
        assertEquals("quiz", mcp.evidenceMode());
        assertEquals(10, mcp.quizQuestions().size());
        assertEquals(
                List.of(
                        "three-capabilities",
                        "resource-not-tool",
                        "prompt-not-authz",
                        "server-validate",
                        "oauth-pkce",
                        "scope-subset",
                        "audience",
                        "no-passthrough",
                        "confirm-audit",
                        "limits"),
                mcp.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("split", mcp.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition a2a = taskById(tasks, "a2a-collaboration");
        assertEquals("quiz", a2a.evidenceMode());
        assertTrue(a2a.elective());
        assertEquals(10, a2a.quizQuestions().size());
        assertEquals(
                List.of(
                        "agent-card",
                        "message-parts",
                        "task-not-message",
                        "identity",
                        "idempotency",
                        "cancel",
                        "callback-trust",
                        "retry-side-effect",
                        "streaming-callback",
                        "lifecycle"),
                a2a.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("skills-io-auth", a2a.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition observability = taskById(tasks, "observability-runbook");
        assertEquals("quiz", observability.evidenceMode());
        assertTrue(observability.elective());
        assertEquals(
                List.of(
                        "three-signals",
                        "genai-span",
                        "child-calls",
                        "redact",
                        "p99",
                        "alert-duration",
                        "runbook",
                        "no-full-prompt",
                        "no-trace",
                        "four-metrics"),
                observability.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("logs-metrics-traces", observability.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition releaseSafety = taskById(tasks, "release-safety");
        assertEquals("quiz", releaseSafety.evidenceMode());
        assertTrue(releaseSafety.elective());
        assertEquals(
                List.of(
                        "artifacts",
                        "eval-gate",
                        "gradual",
                        "rollback-checkpoint",
                        "index-compat",
                        "one-variable",
                        "avg-metrics",
                        "human-not-gate",
                        "owner-window",
                        "trigger"),
                releaseSafety.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("more-than-code", releaseSafety.quizQuestions().get(0).correctOptionId());
        LearningTaskDefinition architecture = taskById(tasks, "architecture-collaboration");
        assertEquals("quiz", architecture.evidenceMode());
        assertTrue(architecture.elective());
        assertEquals(
                List.of(
                        "value",
                        "diagram-not-contract",
                        "ownership",
                        "platform-owner",
                        "risk-register",
                        "adr",
                        "shared-semantics",
                        "failure-paths",
                        "evidence-index",
                        "six-modules"),
                architecture.quizQuestions().stream().map(LearningQuizQuestion::id).toList());
        assertEquals("boundaries-evidence", architecture.quizQuestions().get(0).correctOptionId());

        LearningTaskDefinition skillAuthoring = taskById(tasks, "agent-skill-authoring");
        assertEquals(List.of("tool-contract", "prompt-context-design"), skillAuthoring.prerequisiteTaskIds());
        assertEquals("quiz", skillAuthoring.evidenceMode());
        assertTrue(skillAuthoring.quizQuestions().stream().anyMatch(question -> "progressive-disclosure".equals(question.id())));
        assertTrue(skillAuthoring.quizQuestions().stream().anyMatch(question -> "evaluation".equals(question.id())));
    }

    private LearningTaskDefinition taskById(List<LearningTaskDefinition> tasks, String taskId) {
        return tasks.stream().filter(task -> taskId.equals(task.id())).findFirst().orElseThrow();
    }

    private LearningCatalogService serviceWith(LearningTaskDefinition... tasks) throws Exception {
        LearningCatalogDefinition catalog = new LearningCatalogDefinition(
                1,
                List.of(new LearningModuleDefinition("module", "Module", "Summary", "beginner", List.of(tasks))));
        Path path = tempDir.resolve("catalog-" + System.nanoTime() + ".json");
        objectMapper.writeValue(path.toFile(), catalog);
        return new LearningCatalogService(
                objectMapper,
                path.toString(),
                TaskCheckRegistryTestFactory.standard(objectMapper));
    }

    private LearningTaskDefinition task(String id, String type) {
        return new LearningTaskDefinition(
                id,
                id,
                "Summary",
                "beginner",
                "requirements",
                10,
                List.of("requirements"),
                "Objective",
                "Scenario",
                "project-agent",
                List.of(new LearningFileView("artifact.md", "starter text")),
                List.of(new TaskCheckDefinition("content", type, "artifact.md", "3", true, 100, "Content")));
    }

    private LearningTaskDefinition taskWithPoints(String id, int points) {
        return new LearningTaskDefinition(
                id,
                id,
                "Summary",
                "beginner",
                "requirements",
                10,
                List.of("requirements"),
                "Objective",
                "Scenario",
                "project-agent",
                List.of(new LearningFileView("artifact.md", "starter text")),
                List.of(new TaskCheckDefinition("content", "min-length", "artifact.md", "3", true, points, "Content")));
    }

    private LearningTaskDefinition taskWithStarterContent(String id, String content) {
        return new LearningTaskDefinition(
                id,
                id,
                "Summary",
                "beginner",
                "requirements",
                10,
                List.of("requirements"),
                "Objective",
                "Scenario",
                "project-agent",
                List.of(new LearningFileView("artifact.md", content)),
                List.of(new TaskCheckDefinition("content", "min-length", "artifact.md", "3", true, 100, "Content")));
    }


    @Test
    void shouldPreferCoreTasksOverElectivesWhenRecommendingNext() throws Exception {
        LearningCatalogService service = serviceWith(
                task("first", "min-length"),
                electiveTask("second", List.of("first")),
                taskWithPrerequisites("third", List.of("first")));

        LearningProgress afterFirst = LearningProgress.empty("first")
                .record("first", 80, List.of(), "second");

        assertTrue(service.isUnlocked("second", afterFirst));
        assertTrue(service.isUnlocked("third", afterFirst));
        assertEquals("third", service.nextTaskId("first", afterFirst));
    }

    @Test
    void shouldAcceptQuizTasksWithoutStarterFiles() throws Exception {
        LearningCatalogService service = serviceWithQuizCatalog("specific-scene");
        assertEquals("quiz", service.getTask("first").evidenceMode());
        assertTrue(service.getTask("first").starterFiles().isEmpty());
        assertEquals("specific-scene", service.getTask("first").quizQuestions().get(0).correctOptionId());
    }

    @Test
    void shouldRejectQuizTasksWithInvalidOptions() {
        assertThrows(IllegalStateException.class, () -> serviceWithQuizCatalog("missing-option"));
    }


    private LearningTaskDefinition electiveTask(String id, List<String> prerequisiteTaskIds) {
        return new LearningTaskDefinition(
                id,
                id,
                "Summary",
                "beginner",
                "requirements",
                10,
                List.of("requirements"),
                "Objective",
                "Scenario",
                "project-agent",
                List.of(new LearningFileView("artifact.md", "starter text")),
                List.of(new TaskCheckDefinition(
                        "content",
                        "min-length",
                        "artifact.md",
                        "3",
                        true,
                        100,
                        "Content")),
                "Lesson",
                List.of("artifact.md"),
                prerequisiteTaskIds,
                "document",
                List.of(),
                List.of(),
                100,
                true);
    }

    private LearningTaskDefinition taskWithPrerequisites(String id, List<String> prerequisiteTaskIds) {
        return new LearningTaskDefinition(
                id,
                id,
                "Summary",
                "beginner",
                "requirements",
                10,
                List.of("requirements"),
                "Objective",
                "Scenario",
                "project-agent",
                List.of(new LearningFileView("artifact.md", "starter text")),
                List.of(new TaskCheckDefinition(
                        "content",
                        "min-length",
                        "artifact.md",
                        "3",
                        true,
                        100,
                        "Content")),
                "Lesson",
                List.of("artifact.md"),
                prerequisiteTaskIds,
                "document",
                List.of());
    }

    private LearningCatalogService serviceWithQuizCatalog(String correctOptionId) throws Exception {
        Path path = tempDir.resolve("quiz-catalog-" + System.nanoTime() + ".json");
        Files.writeString(path, """
                {
                  "version": 1,
                  "modules": [
                    {
                      "id": "module",
                      "title": "Module",
                      "summary": "Summary",
                      "level": "beginner",
                      "tasks": [
                        {
                          "id": "first",
                          "title": "first",
                          "summary": "Summary",
                          "level": "beginner",
                          "kind": "requirements",
                          "estimatedMinutes": 10,
                          "skills": ["requirements"],
                          "objective": "Objective",
                          "scenario": "Scenario",
                          "mentorAgentId": "project-agent",
                          "starterFiles": [],
                          "checks": [],
                          "lesson": "Lesson",
                          "deliverables": ["Complete the quiz"],
                          "prerequisiteTaskIds": [],
                          "evidenceMode": "quiz",
                          "references": [],
                          "passingScore": 100,
                          "quizQuestions": [
                            {
                              "id": "user-scope",
                              "prompt": "Which user is specific?",
                              "options": [
                                {"id": "all-users", "label": "All users"},
                                {"id": "specific-scene", "label": "A specific user"}
                              ],
                              "correctOptionId": "%s",
                              "points": 100,
                              "explanation": "Need a concrete user."
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """.formatted(correctOptionId));
        return new LearningCatalogService(
                objectMapper,
                path.toString(),
                TaskCheckRegistryTestFactory.standard(objectMapper));
    }
}
