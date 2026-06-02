package com.kiniu.game.story;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiniu.game.dto.StoryCatalogResponse;
import com.kiniu.game.dto.StoryChoiceView;
import com.kiniu.game.dto.StoryNodeView;
import com.kiniu.game.state.WorldState;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StoryEngine {

    private static final String ENTRY_NODE_ID = "container.home";

    private final ObjectMapper objectMapper;
    private final Path catalogPath;
    private volatile String entryNodeId = ENTRY_NODE_ID;
    private volatile Map<String, StoryNode> storyNodes = createAgentContainerFlow();

    @Autowired
    public StoryEngine(
            ObjectMapper objectMapper,
            @Value("${game.story.catalog-path:data/story-catalog.json}") String catalogPath) {
        this(objectMapper, Paths.get(catalogPath));
    }

    StoryEngine(ObjectMapper objectMapper, Path catalogPath) {
        this.objectMapper = objectMapper;
        this.catalogPath = catalogPath.toAbsolutePath();
        loadPersistedCatalog();
    }

    public Optional<StoryEvent> findTriggeredEvent(WorldState worldState, String input, String choice) {
        StoryNode currentNode = getCurrentNode(worldState);
        return currentNode.findMatchingChoice(worldState, input, choice)
                .map(selectedChoice -> moveToNode(worldState, currentNode, selectedChoice));
    }

    public List<String> getDefaultChoices(WorldState worldState) {
        return getCurrentNode(worldState).availableChoices(worldState).stream()
                .map(StoryChoice::label)
                .toList();
    }

    public String getCurrentSpeakerId(WorldState worldState) {
        return getCurrentNode(worldState).speakerId();
    }

    public StoryCatalogResponse getStoryCatalog() {
        List<StoryNodeView> nodes = storyNodes.values().stream()
                .map(node -> new StoryNodeView(
                        node.id(),
                        node.sceneId(),
                        node.title(),
                        node.speakerId(),
                        node.narrative(),
                        node.tags(),
                        List.copyOf(node.enterFlags()),
                        node.enterAffinityChanges(),
                        node.choices().stream()
                                .map(choice -> new StoryChoiceView(
                                        choice.id(),
                                        choice.label(),
                                        choice.description(),
                                        choice.targetNodeId(),
                                        List.copyOf(choice.condition().requiredFlags()),
                                        List.copyOf(choice.condition().blockedFlags()),
                                        choice.condition().minimumAffinity(),
                                        choice.condition().keywords(),
                                        List.copyOf(choice.flagsToAdd()),
                                        choice.affinityChanges()))
                                .toList()))
                .toList();

        return new StoryCatalogResponse(entryNodeId, nodes);
    }

    public synchronized StoryCatalogResponse saveStoryCatalog(StoryCatalogResponse catalog) {
        validateCatalog(catalog);
        this.entryNodeId = catalog.entryNodeId();
        this.storyNodes = toStoryNodes(catalog);
        StoryCatalogResponse savedCatalog = getStoryCatalog();
        persistCatalog(savedCatalog);
        return savedCatalog;
    }

    private StoryEvent moveToNode(WorldState worldState, StoryNode currentNode, StoryChoice selectedChoice) {
        selectedChoice.apply(worldState);

        StoryNode targetNode = requireNode(selectedChoice.targetNodeId());
        targetNode.applyArrival(worldState);
        worldState.setStorySeedNodeId(targetNode.id());

        return StoryEvent.seed(
                selectedChoice.id(),
                currentNode.id(),
                targetNode.id(),
                targetNode.sceneId(),
                targetNode.speakerId(),
                targetNode.title(),
                targetNode.narrative(),
                targetNode.availableChoices(worldState).stream().map(StoryChoice::label).toList());
    }

    private StoryNode getCurrentNode(WorldState worldState) {
        String currentNodeId = worldState.getStorySeedNodeId();
        if (currentNodeId == null || currentNodeId.isBlank()) {
            StoryNode entryNode = requireNode(entryNodeId);
            worldState.setStorySeedNodeId(entryNode.id());
            if (worldState.getCurrentNodeId() == null || worldState.getCurrentNodeId().isBlank()) {
                worldState.setCurrentNodeId(entryNode.id());
            }
            if (worldState.getCurrentScene() == null || worldState.getCurrentScene().isBlank()) {
                worldState.setCurrentScene(entryNode.sceneId());
            }
            return entryNode;
        }

        StoryNode node = storyNodes.get(currentNodeId);
        if (node == null) {
            StoryNode entryNode = requireNode(entryNodeId);
            worldState.setStorySeedNodeId(entryNode.id());
            if (worldState.getCurrentNodeId() == null || worldState.getCurrentNodeId().isBlank()) {
                worldState.setCurrentNodeId(entryNode.id());
            }
            if (worldState.getCurrentScene() == null || worldState.getCurrentScene().isBlank()) {
                worldState.setCurrentScene(entryNode.sceneId());
            }
            return entryNode;
        }
        return node;
    }

    private void loadPersistedCatalog() {
        try {
            if (Files.exists(catalogPath)) {
                StoryCatalogResponse catalog = objectMapper.readValue(catalogPath.toFile(), StoryCatalogResponse.class);
                validateCatalog(catalog);
                this.entryNodeId = catalog.entryNodeId();
                this.storyNodes = toStoryNodes(catalog);
                return;
            }

            createCatalogDirectories();
            persistCatalog(getStoryCatalog());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize story catalog from " + catalogPath, exception);
        }
    }

    private void persistCatalog(StoryCatalogResponse catalog) {
        try {
            createCatalogDirectories();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(catalogPath.toFile(), catalog);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist story catalog to " + catalogPath, exception);
        }
    }

    private void createCatalogDirectories() throws IOException {
        Path parent = catalogPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private Map<String, StoryNode> createAgentContainerFlow() {
        Map<String, StoryNode> nodes = new LinkedHashMap<>();

        StoryNode containerHome = StoryNode.builder(
                        "container.home",
                        "agent-hub",
                        "Agent Container Hub",
                        "narrator")
                .narrative("这里是通用 Agent 容器。你可以从陪伴、面试、知识库问答、项目助理或写作教练开始，也可以直接描述当前想解决的问题。")
                .tag("entry")
                .tag("agent-container")
                .choice(StoryChoice.of(
                                "start-companion",
                                "自由陪聊",
                                "进入日常陪伴模式，适合开放聊天、轻量计划和状态整理。",
                                "companion.check-in")
                        .whenKeywords("陪聊", "闲聊", "状态", "companion", "chat")
                        .addFlag("mode-companion")
                        .changeAffinity("companion", 1))
                .choice(StoryChoice.of(
                                "start-java-rag-interview",
                                "Java/RAG 面试考查",
                                "进入可追问、可评分、可复盘的 Java 与 RAG 面试模式。",
                                "interview.java-rag")
                        .whenKeywords("java", "rag", "面试", "八股", "interview")
                        .addFlag("mode-interview")
                        .changeAffinity("java-rag-interviewer", 1))
                .choice(StoryChoice.of(
                                "start-knowledge-qa",
                                "知识库问答",
                                "进入基于知识包和上下文的问答模式，强调来源、假设和缺口。",
                                "knowledge.qa")
                        .whenKeywords("知识库", "文档", "检索", "问答", "knowledge", "docs")
                        .addFlag("mode-knowledge")
                        .changeAffinity("knowledge-curator", 1))
                .choice(StoryChoice.of(
                                "start-project-agent",
                                "项目助理",
                                "进入项目推进模式，适合拆任务、做方案、查风险和复盘进展。",
                                "workspace.project")
                        .whenKeywords("项目", "代码", "任务", "计划", "project", "code")
                        .addFlag("mode-project")
                        .changeAffinity("project-agent", 1))
                .choice(StoryChoice.of(
                                "start-writing-coach",
                                "写作教练",
                                "进入写作辅助模式，适合梳理文章、改稿、提纲和表达。",
                                "writing.coach")
                        .whenKeywords("写作", "文章", "改稿", "提纲", "writing")
                        .addFlag("mode-writing")
                        .changeAffinity("writing-coach", 1))
                .build();

        StoryNode companionCheckIn = StoryNode.builder(
                        "companion.check-in",
                        "companion-check-in",
                        "Daily Companion Check-in",
                        "companion")
                .narrative("陪伴 Agent 会先确认你现在要聊天、整理状态，还是把一个模糊目标变成下一步行动。")
                .tag("companion")
                .tag("open-chat")
                .choice(StoryChoice.of(
                                "continue-companion",
                                "继续聊当前状态",
                                "保持轻量陪伴，让 Agent 追问一个真正有用的问题。",
                                "companion.check-in")
                        .whenKeywords("继续", "聊", "状态", "心情", "plan"))
                .choice(StoryChoice.of(
                                "turn-into-review",
                                "整理成复盘计划",
                                "把聊天内容收束成目标、障碍、下一步和提醒。",
                                "learning.review")
                        .whenKeywords("复盘", "计划", "总结", "review")
                        .changeAffinity("companion", 1))
                .choice(StoryChoice.of(
                                "back-to-container-from-companion",
                                "回到 Agent 容器",
                                "返回容器入口，切换到其他 Agent 或任务流。",
                                "container.home")
                        .whenKeywords("返回", "切换", "容器", "agent"))
                .build();

        StoryNode javaRagInterview = StoryNode.builder(
                        "interview.java-rag",
                        "interview-java-rag",
                        "Java & RAG Interview Room",
                        "java-rag-interviewer")
                .narrative("面试官会一次只问一个问题，根据你的回答追问，并在必要时给出评分、漏洞和参考答案。")
                .tag("interview")
                .tag("java")
                .tag("rag")
                .choice(StoryChoice.of(
                                "ask-java-core",
                                "先问 Java 基础",
                                "从集合、异常、泛型、反射、I/O 等基础知识开始。",
                                "interview.java-core")
                        .whenKeywords("java", "基础", "集合", "hashmap", "core")
                        .changeAffinity("java-rag-interviewer", 1))
                .choice(StoryChoice.of(
                                "ask-jvm-concurrency",
                                "追问 JVM/并发",
                                "切到 JVM、内存模型、锁、线程池和并发容器。",
                                "interview.java-core")
                        .whenKeywords("jvm", "并发", "线程", "锁", "concurrency")
                        .addFlag("interview-deep-dive"))
                .choice(StoryChoice.of(
                                "ask-rag-architecture",
                                "切到 RAG 架构",
                                "围绕切分、Embedding、召回、重排、上下文压缩和评估追问。",
                                "interview.rag-architecture")
                        .whenKeywords("rag", "向量", "召回", "embedding", "rerank")
                        .changeAffinity("knowledge-curator", 1))
                .choice(StoryChoice.of(
                                "review-interview",
                                "总结薄弱点",
                                "把当前回答整理成薄弱点、复习顺序和下一轮问题。",
                                "learning.review")
                        .whenKeywords("总结", "薄弱", "复习", "score", "review"))
                .build();

        StoryNode javaCore = StoryNode.builder(
                        "interview.java-core",
                        "interview-java-core",
                        "Java Core Drill",
                        "java-rag-interviewer")
                .narrative("这一轮聚焦 Java 基础和 JVM/并发。先让用户回答，再根据答案追问边界条件、底层机制和实际工程取舍。")
                .tag("interview")
                .tag("java-core")
                .choice(StoryChoice.of(
                                "continue-java-follow-up",
                                "继续追问 Java",
                                "围绕上一题追问实现细节、反例和工程场景。",
                                "interview.java-core")
                        .whenKeywords("继续", "追问", "java", "jvm"))
                .choice(StoryChoice.of(
                                "switch-to-rag-from-java",
                                "转到 RAG",
                                "从 Java 后端工程视角切到 RAG 系统设计。",
                                "interview.rag-architecture")
                        .whenKeywords("rag", "向量", "检索", "系统设计"))
                .choice(StoryChoice.of(
                                "java-review",
                                "生成复习卡片",
                                "把 Java 回答沉淀成可复习的知识卡和错题。",
                                "learning.review")
                        .whenKeywords("卡片", "错题", "复习", "总结"))
                .build();

        StoryNode ragArchitecture = StoryNode.builder(
                        "interview.rag-architecture",
                        "interview-rag-architecture",
                        "RAG Architecture Drill",
                        "java-rag-interviewer")
                .narrative("这一轮聚焦 RAG：文档切分、Embedding、向量库、召回、重排、上下文装配、评估和线上观测。")
                .tag("interview")
                .tag("rag")
                .choice(StoryChoice.of(
                                "continue-rag-follow-up",
                                "继续追问 RAG",
                                "追问一个 RAG 系统在真实业务里的失效模式和优化方案。",
                                "interview.rag-architecture")
                        .whenKeywords("继续", "追问", "rag", "评估", "召回"))
                .choice(StoryChoice.of(
                                "switch-to-knowledge-qa",
                                "转成知识库问答",
                                "把面试问题切换成基于知识包的 grounded QA。",
                                "knowledge.qa")
                        .whenKeywords("知识库", "文档", "问答", "grounded")
                        .addFlag("rag-to-knowledge"))
                .choice(StoryChoice.of(
                                "rag-review",
                                "生成 RAG 复盘",
                                "整理 RAG 薄弱点、术语表和下一轮追问。",
                                "learning.review")
                        .whenKeywords("复盘", "总结", "薄弱", "术语"))
                .build();

        StoryNode knowledgeQa = StoryNode.builder(
                        "knowledge.qa",
                        "knowledge-qa",
                        "Knowledge QA Workspace",
                        "knowledge-curator")
                .narrative("知识库 Agent 会优先区分：已有上下文能支持什么、哪些是推断、还缺哪些资料。")
                .tag("knowledge")
                .tag("rag")
                .choice(StoryChoice.of(
                                "ask-with-sources",
                                "基于资料回答",
                                "要求 Agent 明确来源、证据和不确定性。",
                                "knowledge.qa")
                        .whenKeywords("回答", "来源", "证据", "资料"))
                .choice(StoryChoice.of(
                                "design-retrieval",
                                "设计检索策略",
                                "讨论切分、召回、重排、过滤和评估指标。",
                                "interview.rag-architecture")
                        .whenKeywords("检索", "召回", "重排", "评估"))
                .choice(StoryChoice.of(
                                "knowledge-review",
                                "整理知识包缺口",
                                "列出下一批应该接入的文档、笔记或项目资料。",
                                "learning.review")
                        .whenKeywords("缺口", "接入", "文档", "整理"))
                .build();

        StoryNode workspaceProject = StoryNode.builder(
                        "workspace.project",
                        "workspace-project",
                        "Project Agent Workspace",
                        "project-agent")
                .narrative("项目 Agent 会把目标拆成可执行步骤，指出依赖、风险、验收标准和适合沉淀为模板的流程。")
                .tag("project")
                .tag("workspace")
                .choice(StoryChoice.of(
                                "break-down-task",
                                "拆解当前任务",
                                "把目标拆成可执行的小步和验收标准。",
                                "workspace.project")
                        .whenKeywords("拆解", "任务", "计划", "issue"))
                .choice(StoryChoice.of(
                                "review-project-risk",
                                "检查项目风险",
                                "从依赖、范围、测试和交付角度找风险。",
                                "session.review")
                        .whenKeywords("风险", "测试", "交付", "review"))
                .choice(StoryChoice.of(
                                "back-to-container-from-project",
                                "回到 Agent 容器",
                                "返回容器入口，切换到其他 Agent 或任务流。",
                                "container.home")
                        .whenKeywords("返回", "切换", "容器"))
                .build();

        StoryNode writingCoach = StoryNode.builder(
                        "writing.coach",
                        "writing-coach",
                        "Writing Coach Desk",
                        "writing-coach")
                .narrative("写作教练会先找意图、读者和结构，再帮你改写、扩展或收束，而不是把文本改成另一个人的声音。")
                .tag("writing")
                .tag("coach")
                .choice(StoryChoice.of(
                                "shape-outline",
                                "梳理提纲",
                                "把想法整理成标题、段落顺序和论证线。",
                                "writing.coach")
                        .whenKeywords("提纲", "结构", "标题", "outline"))
                .choice(StoryChoice.of(
                                "revise-draft",
                                "修改草稿",
                                "聚焦清晰度、节奏和具体表达。",
                                "writing.coach")
                        .whenKeywords("修改", "改稿", "润色", "draft"))
                .choice(StoryChoice.of(
                                "writing-review",
                                "生成写作复盘",
                                "整理这次写作的主题、素材缺口和下一步。",
                                "learning.review")
                        .whenKeywords("复盘", "总结", "下一步"))
                .build();

        StoryNode learningReview = StoryNode.builder(
                        "learning.review",
                        "learning-review",
                        "Session Review",
                        "narrator")
                .narrative("复盘节点会把本轮会话整理为目标、已知事实、薄弱点、下一步和可沉淀的 Agent 模板。")
                .tag("review")
                .tag("memory")
                .choice(StoryChoice.of(
                                "continue-review",
                                "继续复盘",
                                "继续压缩本轮信息，形成下一步行动。",
                                "learning.review")
                        .whenKeywords("继续", "复盘", "总结", "下一步"))
                .choice(StoryChoice.of(
                                "return-container-from-review",
                                "回到 Agent 容器",
                                "返回容器入口，选择新的 Agent 或任务流。",
                                "container.home")
                        .whenKeywords("返回", "容器", "切换", "agent"))
                .build();

        StoryNode sessionReview = StoryNode.builder(
                        "session.review",
                        "session-review",
                        "Session Risk Review",
                        "project-agent")
                .narrative("项目复盘会关注：目标是否明确、上下文是否充分、风险是否可验证、下一步是否足够小。")
                .tag("review")
                .tag("project")
                .choice(StoryChoice.of(
                                "continue-session-review",
                                "继续检查风险",
                                "继续围绕范围、测试、依赖和验收追问。",
                                "session.review")
                        .whenKeywords("继续", "风险", "测试", "验收"))
                .choice(StoryChoice.of(
                                "return-container-from-session-review",
                                "回到 Agent 容器",
                                "返回容器入口，选择新的 Agent 或任务流。",
                                "container.home")
                        .whenKeywords("返回", "容器", "切换"))
                .build();

        for (StoryNode node : List.of(
                containerHome,
                companionCheckIn,
                javaRagInterview,
                javaCore,
                ragArchitecture,
                knowledgeQa,
                workspaceProject,
                writingCoach,
                learningReview,
                sessionReview)) {
            nodes.put(node.id(), node);
        }

        return Map.copyOf(nodes);
    }

    private void validateCatalog(StoryCatalogResponse catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("Story catalog must not be null.");
        }
        if (catalog.entryNodeId() == null || catalog.entryNodeId().isBlank()) {
            throw new IllegalArgumentException("Story catalog entry node id must not be blank.");
        }
        if (catalog.nodes() == null || catalog.nodes().isEmpty()) {
            throw new IllegalArgumentException("Story catalog must contain at least one node.");
        }

        long distinctNodeCount = catalog.nodes().stream().map(StoryNodeView::id).distinct().count();
        if (distinctNodeCount != catalog.nodes().size()) {
            throw new IllegalArgumentException("Story catalog contains duplicate node ids.");
        }
        boolean entryExists = catalog.nodes().stream().anyMatch(node -> node.id().equals(catalog.entryNodeId()));
        if (!entryExists) {
            throw new IllegalArgumentException("Story catalog entry node id does not exist in nodes.");
        }
    }

    private Map<String, StoryNode> toStoryNodes(StoryCatalogResponse catalog) {
        Map<String, StoryNode> nodes = new LinkedHashMap<>();

        for (StoryNodeView nodeView : catalog.nodes()) {
            StoryNode.Builder builder = StoryNode.builder(
                            nodeView.id(),
                            nodeView.sceneId(),
                            nodeView.title(),
                            nodeView.speakerId())
                    .narrative(nodeView.narrative());

            safeList(nodeView.tags()).forEach(builder::tag);
            safeList(nodeView.enterFlags()).forEach(builder::enterFlag);
            safeMap(nodeView.enterAffinityChanges()).forEach(builder::enterAffinity);

            for (StoryChoiceView choiceView : safeList(nodeView.choices())) {
                StoryChoice choice = StoryChoice.of(
                        choiceView.id(),
                        choiceView.label(),
                        choiceView.description(),
                        choiceView.targetNodeId());

                for (String keyword : safeList(choiceView.keywords())) {
                    choice = choice.whenKeywords(keyword);
                }
                for (String flag : safeList(choiceView.requiredFlags())) {
                    choice = choice.requiringFlags(flag);
                }
                for (String flag : safeList(choiceView.blockedFlags())) {
                    choice = choice.blockingFlags(flag);
                }
                for (Map.Entry<String, Integer> affinity : safeMap(choiceView.minimumAffinity()).entrySet()) {
                    choice = choice.minAffinity(affinity.getKey(), affinity.getValue());
                }
                for (String flag : safeList(choiceView.flagsToAdd())) {
                    choice = choice.addFlag(flag);
                }
                for (Map.Entry<String, Integer> affinity : safeMap(choiceView.affinityChanges()).entrySet()) {
                    choice = choice.changeAffinity(affinity.getKey(), affinity.getValue());
                }

                builder.choice(choice);
            }

            StoryNode node = builder.build();
            nodes.put(node.id(), node);
        }

        return Map.copyOf(nodes);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Map<String, Integer> safeMap(Map<String, Integer> values) {
        return values == null ? Map.of() : values;
    }

    private StoryNode requireNode(String nodeId) {
        StoryNode node = storyNodes.get(nodeId);
        if (node == null) {
            throw new IllegalStateException("Unknown story node: " + nodeId);
        }
        return node;
    }

    private Map<String, StoryNode> createStoryGraph() {
        Map<String, StoryNode> nodes = new LinkedHashMap<>();

        StoryNode openingThreshold = StoryNode.builder(
                        "opening.threshold",
                        "opening",
                        "Forest Threshold",
                        "narrator")
                .narrative("The fog loosens around three possible routes: a trail of footprints, a lantern glow, and a quiet ridge where hunters might be listening.")
                .tag("entry")
                .choice(StoryChoice.of(
                                "follow-footprints",
                                "沿着脚印前进",
                                "直接进入主线会面场景。",
                                "crossroads.arrival")
                        .whenKeywords("explore", "path", "footprint", "road", "forward"))
                .choice(StoryChoice.of(
                                "inspect-lantern",
                                "调查远处灯光",
                                "先拿到灯坛线索，为后续密库分支做准备。",
                                "shrine.lantern")
                        .whenKeywords("inspect", "lantern", "light", "altar", "glow"))
                .choice(StoryChoice.of(
                                "wait-and-listen",
                                "留在原地倾听",
                                "偷听猎人的对话，开启预警与伏击分支。",
                                "camp.watchfire")
                        .whenKeywords("wait", "listen", "hide", "observe", "silent"))
                .build();

        StoryNode crossroadsArrival = StoryNode.builder(
                        "crossroads.arrival",
                        "moonlit-crossroads",
                        "Moonlit Crossroads",
                        "lyra")
                .narrative("Lyra waits beneath a broken signpost, studying you as if she expected this meeting long before you reached the crossroads.")
                .tag("hub")
                .enterFlag("met-lyra")
                .enterAffinity("lyra", 1)
                .choice(StoryChoice.of(
                                "ally-with-lyra",
                                "告诉 Lyra 你愿意合作",
                                "快速进入结盟线，提高 Lyra 好感。",
                                "grove.alliance")
                        .whenKeywords("trust", "help", "ally", "follow", "cooperate")
                        .addFlag("trust-offered")
                        .changeAffinity("lyra", 1))
                .choice(StoryChoice.of(
                                "question-lyra",
                                "询问她是否隐瞒真相",
                                "进入怀疑线，保留对抗和和解的空间。",
                                "grove.interrogation")
                        .whenKeywords("doubt", "truth", "question", "suspect", "lie")
                        .changeAffinity("lyra", -1))
                .choice(StoryChoice.of(
                                "warn-about-hunters",
                                "提起猎人的动向",
                                "需要先偷听到猎人情报，解锁预警分支。",
                                "grove.warning")
                        .requiringFlags("overheard-hunters")
                        .whenKeywords("hunter", "warn", "danger", "ambush")
                        .changeAffinity("lyra", 1))
                .choice(StoryChoice.of(
                                "ask-about-relic",
                                "要求 Lyra 揭示圣物位置",
                                "只有拿到灯坛线索且好感足够时才会开启密库主线。",
                                "vault.threshold")
                        .requiringFlags("lantern-attuned")
                        .minAffinity("lyra", 2)
                        .whenKeywords("relic", "vault", "reveal", "artifact"))
                .build();

        StoryNode shrineLantern = StoryNode.builder(
                        "shrine.lantern",
                        "lantern-altar",
                        "Lantern Shrine",
                        "narrator")
                .narrative("Ancient lanterns answer your presence. Their runes pulse in a rhythm that feels less like magic and more like a lock waiting for the correct witness.")
                .tag("lore")
                .tag("key-item")
                .enterFlag("lantern-attuned")
                .choice(StoryChoice.of(
                                "accept-echo",
                                "触碰灯芯接受共鸣",
                                "直接深入回声支线，提前理解密库机制。",
                                "vault.echo")
                        .whenKeywords("touch", "echo", "accept", "lantern"))
                .choice(StoryChoice.of(
                                "take-runes-to-lyra",
                                "带着符文去找 Lyra",
                                "携带线索回到岔路口，解锁更高层分支。",
                                "crossroads.arrival")
                        .whenKeywords("lyra", "bring", "rune", "return")
                        .changeAffinity("lyra", 1))
                .choice(StoryChoice.of(
                                "record-runes",
                                "记录符文纹理",
                                "进入档案支线，为后续世界观创作留出说明节点。",
                                "archive.notes")
                        .whenKeywords("record", "study", "notes", "symbols")
                        .addFlag("archived-runes"))
                .build();

        StoryNode campWatchfire = StoryNode.builder(
                        "camp.watchfire",
                        "hunter-camp",
                        "Hunter Watchfire",
                        "narrator")
                .narrative("From the ridge above the campfire you hear hunters trading rumors: someone has already marked Lyra's route, and the relic is expected to move before dawn.")
                .tag("intel")
                .enterFlag("overheard-hunters")
                .choice(StoryChoice.of(
                                "warn-lyra-now",
                                "去警告 Lyra",
                                "将情报带回主线，开启伏击准备。",
                                "grove.warning")
                        .whenKeywords("warn", "lyra", "danger", "return")
                        .changeAffinity("lyra", 1))
                .choice(StoryChoice.of(
                                "keep-listening",
                                "继续偷听更多情报",
                                "拿到隐秘小路情报，为战术分支铺路。",
                                "camp.secret")
                        .whenKeywords("listen", "more", "spy", "secret")
                        .addFlag("hidden-route-known"))
                .choice(StoryChoice.of(
                                "leave-for-crossroads",
                                "转回岔路口",
                                "保留情报，返回主要会面节点。",
                                "crossroads.arrival")
                        .whenKeywords("crossroads", "leave", "back", "return"))
                .build();

        StoryNode groveAlliance = StoryNode.builder(
                        "grove.alliance",
                        "whispering-grove",
                        "Whispering Grove Alliance",
                        "lyra")
                .narrative("Lyra lowers her guard and speaks plainly for the first time. She admits the relic is hidden beyond a living vault that responds to trust, not force.")
                .tag("relationship")
                .enterFlag("allied-lyra")
                .enterAffinity("lyra", 1)
                .choice(StoryChoice.of(
                                "ask-for-relic-history",
                                "追问圣物的来历",
                                "展开世界观说明，适合承载可拓展 lore 章节。",
                                "lore.relic")
                        .whenKeywords("history", "origin", "relic", "story"))
                .choice(StoryChoice.of(
                                "request-vault-entry",
                                "请求进入密库",
                                "需要灯坛共鸣，正式进入主线密库分支。",
                                "vault.threshold")
                        .requiringFlags("lantern-attuned")
                        .minAffinity("lyra", 2)
                        .whenKeywords("vault", "enter", "relic", "open"))
                .choice(StoryChoice.of(
                                "prepare-ambush",
                                "先去布置伏击",
                                "需要猎人情报，进入战术分支。",
                                "ambush.prep")
                        .requiringFlags("overheard-hunters")
                        .whenKeywords("ambush", "hunters", "prepare", "trap"))
                .build();

        StoryNode groveInterrogation = StoryNode.builder(
                        "grove.interrogation",
                        "whispering-grove",
                        "Whispering Grove Interrogation",
                        "lyra")
                .narrative("Lyra does not leave, but every answer becomes careful. The grove quiets with her, as if your doubt has changed what the forest is willing to show.")
                .tag("relationship")
                .choice(StoryChoice.of(
                                "push-harder",
                                "继续追问并施压",
                                "进入对立线，后续可以发展出分道扬镳。",
                                "rift.break")
                        .whenKeywords("press", "harder", "force", "demand")
                        .changeAffinity("lyra", -1))
                .choice(StoryChoice.of(
                                "offer-limited-trust",
                                "表示你愿意暂时合作",
                                "从怀疑线转回合作线，但保留张力。",
                                "grove.alliance")
                        .whenKeywords("cooperate", "temporary", "trust", "work together"))
                .choice(StoryChoice.of(
                                "mention-lantern-proof",
                                "拿出灯坛符文当证据",
                                "需要灯坛线索，利用证据迫使剧情推进。",
                                "vault.threshold")
                        .requiringFlags("lantern-attuned")
                        .whenKeywords("rune", "proof", "lantern", "evidence"))
                .build();

        StoryNode groveWarning = StoryNode.builder(
                        "grove.warning",
                        "whispering-grove",
                        "Warning in the Grove",
                        "lyra")
                .narrative("When you describe the hunters, Lyra immediately redraws the route in the dirt. The scene stops being mystical and becomes tactical.")
                .tag("tactics")
                .enterFlag("lyra-warned")
                .enterAffinity("lyra", 1)
                .choice(StoryChoice.of(
                                "take-hidden-route",
                                "走隐秘小路绕过猎人",
                                "需要提前知道密道，走规避路线。",
                                "route.hidden")
                        .requiringFlags("hidden-route-known")
                        .whenKeywords("hidden", "route", "bypass", "avoid"))
                .choice(StoryChoice.of(
                                "set-counter-ambush",
                                "和 Lyra 一起布置反伏击",
                                "开启战术战斗分支。",
                                "ambush.prep")
                        .whenKeywords("ambush", "counter", "trap", "fight"))
                .choice(StoryChoice.of(
                                "rush-vault",
                                "趁夜色直接冲向密库",
                                "需要灯坛线索才知道密库入口。",
                                "vault.threshold")
                        .requiringFlags("lantern-attuned")
                        .whenKeywords("vault", "rush", "relic", "now"))
                .build();

        StoryNode vaultThreshold = StoryNode.builder(
                        "vault.threshold",
                        "sealed-vault",
                        "Threshold of the Living Vault",
                        "narrator")
                .narrative("The vault door is grown from roots and bronze. It reacts to Lyra's presence, but the final pattern only appears because you carry the lantern's resonance.")
                .tag("mainline")
                .choice(StoryChoice.of(
                                "open-vault-with-lyra",
                                "与 Lyra 一起开启密库",
                                "主线成功推进，适合作为章节终点或转场。",
                                "vault.opened")
                        .requiringFlags("lantern-attuned", "allied-lyra")
                        .minAffinity("lyra", 3)
                        .whenKeywords("open", "together", "vault", "relic"))
                .choice(StoryChoice.of(
                                "decode-echo-alone",
                                "独自解析回声结构",
                                "转入更偏探索/谜题的支线。",
                                "vault.echo")
                        .requiringFlags("lantern-attuned")
                        .whenKeywords("decode", "echo", "alone", "study"))
                .choice(StoryChoice.of(
                                "retreat-and-plan",
                                "暂时后撤重新规划",
                                "回到战术节点，为后续分卷留出口。",
                                "ambush.prep")
                        .requiringFlags("lyra-warned")
                        .whenKeywords("retreat", "plan", "later", "prepare"))
                .build();

        StoryNode vaultEcho = StoryNode.builder(
                        "vault.echo",
                        "echo-chamber",
                        "Echo Chamber",
                        "narrator")
                .narrative("Inside the echo chamber, every inscription answers with a fragment of memory. This is a good branch for lore drops, puzzles, and identity reveals.")
                .tag("puzzle")
                .enterFlag("echo-chamber-opened")
                .choice(StoryChoice.of(
                                "return-with-answers",
                                "带着答案回到密库门前",
                                "把回声线的成果带回主线。",
                                "vault.threshold")
                        .whenKeywords("return", "answers", "back", "vault"))
                .choice(StoryChoice.of(
                                "seek-memory-origin",
                                "追查记忆属于谁",
                                "进入身份揭示支线。",
                                "identity.fragment")
                        .whenKeywords("memory", "origin", "identity", "who"))
                .build();

        StoryNode archiveNotes = StoryNode.builder(
                        "archive.notes",
                        "archive-room",
                        "Rune Archive",
                        "narrator")
                .narrative("Your notes begin turning raw symbols into a usable authoring bible: history, lock patterns, names, and narrative hooks ready for future branches.")
                .tag("authoring")
                .choice(StoryChoice.of(
                                "bring-notes-to-lyra",
                                "拿着笔记去找 Lyra",
                                "让 lore 创作反哺主线。",
                                "crossroads.arrival")
                        .whenKeywords("lyra", "notes", "share", "return"))
                .choice(StoryChoice.of(
                                "expand-archive",
                                "继续整理档案",
                                "保留为未来补写世界观章节的支点。",
                                "archive.notes")
                        .whenKeywords("archive", "expand", "write", "organize"))
                .build();

        StoryNode campSecret = StoryNode.builder(
                        "camp.secret",
                        "hunter-camp",
                        "Secret Route Intel",
                        "narrator")
                .narrative("The hunters mention a maintenance trail beneath the roots. It is narrow, flooded, and exactly the sort of branch a later stealth chapter can reuse.")
                .tag("tactics")
                .choice(StoryChoice.of(
                                "report-secret-route",
                                "把密道告诉 Lyra",
                                "强化预警线并解锁绕路选项。",
                                "grove.warning")
                        .whenKeywords("lyra", "route", "tell", "report"))
                .choice(StoryChoice.of(
                                "test-route-alone",
                                "独自试探密道",
                                "开启偏个人冒险的侧支。",
                                "route.hidden")
                        .whenKeywords("alone", "test", "route", "crawl"))
                .build();

        StoryNode ambushPrep = StoryNode.builder(
                        "ambush.prep",
                        "grove-edge",
                        "Counter-Ambush Preparation",
                        "lyra")
                .narrative("Together you turn the grove edge into a choice architecture of its own: decoys, false lights, and escape lines that can produce several combat or stealth outcomes.")
                .tag("tactics")
                .choice(StoryChoice.of(
                                "launch-decoy-plan",
                                "启动诱饵计划",
                                "适合作为战术分支的结果节点。",
                                "ending.decoy-success")
                        .whenKeywords("decoy", "plan", "launch", "start"))
                .choice(StoryChoice.of(
                                "abort-and-enter-vault",
                                "放弃伏击改去密库",
                                "回归主线。",
                                "vault.threshold")
                        .requiringFlags("lantern-attuned")
                        .whenKeywords("vault", "abort", "relic", "switch"))
                .build();

        StoryNode routeHidden = StoryNode.builder(
                        "route.hidden",
                        "root-tunnel",
                        "Hidden Root Route",
                        "narrator")
                .narrative("The hidden route folds under the grove like an unfinished sentence. It is the kind of node that makes future chapters easy to insert without rewriting the core arc.")
                .tag("stealth")
                .choice(StoryChoice.of(
                                "surface-near-vault",
                                "从密库附近出入口现身",
                                "潜行线并回主线。",
                                "vault.threshold")
                        .requiringFlags("lantern-attuned")
                        .whenKeywords("vault", "surface", "emerge", "near"))
                .choice(StoryChoice.of(
                                "retreat-from-tunnel",
                                "退出地道返回林地",
                                "退回战术节点。",
                                "grove.warning")
                        .whenKeywords("retreat", "back", "leave", "return"))
                .build();

        StoryNode loreRelic = StoryNode.builder(
                        "lore.relic",
                        "whispering-grove",
                        "Relic Lore",
                        "lyra")
                .narrative("Lyra explains the relic is less an object than a witness. This node exists to hold long-form lore without clogging the main progression.")
                .tag("lore")
                .choice(StoryChoice.of(
                                "continue-to-vault",
                                "听完后继续去密库",
                                "把 lore 支线接回主线。",
                                "vault.threshold")
                        .requiringFlags("lantern-attuned")
                        .whenKeywords("continue", "vault", "go", "relic"))
                .choice(StoryChoice.of(
                                "stay-with-lyra",
                                "继续和 Lyra 深聊",
                                "保留更多角色塑造空间。",
                                "grove.alliance")
                        .whenKeywords("stay", "talk", "lyra", "more"))
                .build();

        StoryNode riftBreak = StoryNode.builder(
                        "rift.break",
                        "fractured-path",
                        "Fractured Path",
                        "narrator")
                .narrative("The conversation breaks the temporary alliance. This node can later fan out into solo routes, betrayal routes, or a difficult reconciliation arc.")
                .tag("conflict")
                .choice(StoryChoice.of(
                                "walk-away-alone",
                                "独自离开",
                                "保留单人探索线接口。",
                                "route.hidden")
                        .whenKeywords("alone", "leave", "walk away", "solo"))
                .choice(StoryChoice.of(
                                "attempt-reconciliation",
                                "尝试和解",
                                "重新接回合作线。",
                                "grove.interrogation")
                        .whenKeywords("reconcile", "sorry", "trust", "again")
                        .changeAffinity("lyra", 1))
                .build();

        StoryNode identityFragment = StoryNode.builder(
                        "identity.fragment",
                        "echo-chamber",
                        "Fragmented Identity",
                        "narrator")
                .narrative("A memory fragment implies the relic remembers both you and Lyra. This is a dedicated reveal node for future long-arc plotting.")
                .tag("reveal")
                .choice(StoryChoice.of(
                                "return-to-lyra-with-fragment",
                                "带着记忆碎片回去见 Lyra",
                                "把揭示信息带回角色线。",
                                "grove.alliance")
                        .whenKeywords("lyra", "fragment", "return", "memory"))
                .choice(StoryChoice.of(
                                "face-vault-immediately",
                                "立刻面对密库",
                                "把 reveal 直接导向主线高潮。",
                                "vault.threshold")
                        .requiringFlags("lantern-attuned")
                        .whenKeywords("vault", "immediately", "face", "now"))
                .build();

        StoryNode endingDecoySuccess = StoryNode.builder(
                        "ending.decoy-success",
                        "grove-edge",
                        "Decoy Success",
                        "lyra")
                .narrative("The hunters chase the false lights. You and Lyra gain a clean window to reach the vault, ending this chapter on a tactical victory.")
                .tag("chapter-end")
                .choice(StoryChoice.of(
                                "advance-to-vault-finale",
                                "趁机进入密库终局",
                                "把战术胜利接回主线高潮。",
                                "vault.threshold")
                        .requiringFlags("lantern-attuned")
                        .whenKeywords("vault", "advance", "finale", "now"))
                .choice(StoryChoice.of(
                                "hold-position",
                                "先稳住局面",
                                "留一个章节收束缓冲节点。",
                                "ambush.prep")
                        .whenKeywords("hold", "wait", "position", "steady"))
                .build();

        StoryNode vaultOpened = StoryNode.builder(
                        "vault.opened",
                        "sealed-vault",
                        "Vault Opened",
                        "lyra")
                .narrative("The living vault opens with both trust and proof satisfied. This node is intentionally written as a clean chapter handoff for future acts.")
                .tag("chapter-end")
                .choice(StoryChoice.of(
                                "hold-on-threshold",
                                "停在门前整理线索",
                                "为下一章节保留切入点。",
                                "vault.opened")
                        .whenKeywords("wait", "pause", "threshold", "prepare"))
                .build();

        for (StoryNode node : List.of(
                openingThreshold,
                crossroadsArrival,
                shrineLantern,
                campWatchfire,
                groveAlliance,
                groveInterrogation,
                groveWarning,
                vaultThreshold,
                vaultEcho,
                archiveNotes,
                campSecret,
                ambushPrep,
                routeHidden,
                loreRelic,
                riftBreak,
                identityFragment,
                endingDecoySuccess,
                vaultOpened)) {
            nodes.put(node.id(), node);
        }

        return Map.copyOf(nodes);
    }
}
