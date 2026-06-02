<script setup lang="ts">
import AgentConsoleView from '../components/GameConsoleView.vue'
import AgentStudioView from '../components/StoryEditorView.vue'
import SettingsPanelView from '../components/SettingsPanelView.vue'
import type {
  AgentCatalogResponse,
  ApiSettings,
  BranchOptionView,
  ChatMessage,
  GameResponse,
  OrchestrationTraceView,
  SandboxPlanDraft,
  SavedSandboxPlan,
  SessionExportResponse,
  StoryAnalysisRequest,
  StoryAnalysisResponse,
  StoryCatalogResponse,
  StoryGenerationRequest,
  StoryGenerationResponse,
  WorldState
} from '../types/game'

type ViewMode = 'chat' | 'studio' | 'settings'

const SETTINGS_STORAGE_KEY = 'kiniu.agent.settings'
const SESSION_STORAGE_KEY = 'kiniu.agent.session'
const STORY_DRAFT_STORAGE_KEY = 'kiniu.agent.flowDraft'
const AGENT_DRAFT_STORAGE_KEY = 'kiniu.agent.agentDraft'
const SESSION_EXPORT_STORAGE_KEY = 'kiniu.agent.sessionExport'
const SANDBOX_PLAN_STORAGE_KEY = 'kiniu.agent.sandboxPlans'

const defaultSettings: ApiSettings = {
  backendUrl: 'http://localhost:8080',
  providerUrl: '',
  apiKey: '',
  model: 'gpt-4.1-mini'
}

const activeView = ref<ViewMode>('chat')
const isHydrated = ref(false)
const isSending = ref(false)
const isLoadingStory = ref(false)
const isSavingStory = ref(false)
const isLoadingAgents = ref(false)
const isSavingAgents = ref(false)
const isLoadingSessionExport = ref(false)
const isGeneratingStory = ref(false)
const isValidatingStory = ref(false)
const saveStatus = ref('')
const errorMessage = ref('')
const storyStatus = ref('')
const storyError = ref('')
const agentStatus = ref('')
const agentError = ref('')
const sessionStatus = ref('')
const sessionError = ref('')
const generatorStatus = ref('')
const generatorError = ref('')
const validationStatus = ref('')
const validationError = ref('')
const playerInput = ref('')
const sessionId = ref('')
const settings = reactive<ApiSettings>({ ...defaultSettings })
const storyDraft = ref<StoryCatalogResponse | null>(null)
const agentDraft = ref<AgentCatalogResponse | null>(null)
const sessionExport = ref<SessionExportResponse | null>(null)
const sandboxPlans = ref<SavedSandboxPlan[]>([])
const storyAnalysis = ref<StoryAnalysisResponse | null>(null)
const currentOrchestration = ref<OrchestrationTraceView | null>(null)
const currentBranchOptions = ref<BranchOptionView[]>(toBranchOptions(['自由陪聊', 'Java/RAG 面试考查', '知识库问答', '项目助理']))
const worldState = ref<WorldState>({
  currentScene: 'agent-hub',
  currentNodeId: 'container.home',
  flags: [],
  affinityScores: {
    narrator: 0,
    companion: 0,
    'java-rag-interviewer': 0,
    'knowledge-curator': 0,
    'project-agent': 0,
    'writing-coach': 0
  }
})
const messages = ref<ChatMessage[]>([
  {
    id: 'intro-system',
    role: 'system',
    speaker: '系统',
    content: '这是通用 Agent 容器。你可以新建 Agent、配置任务流，也可以直接在当前会话里让容器选择合适的 Agent。'
  },
  {
    id: 'intro-narrator',
    role: 'assistant',
    speaker: 'Container Conductor',
    content: '先选择一个模式，或直接描述你想解决的问题。我会把这一轮路由给最合适的 Agent。'
  }
])

const sceneLabel = computed(() => {
  const labels: Record<string, string> = {
    'agent-hub': 'Agent 容器',
    'companion-check-in': '陪伴对话',
    'interview-java-rag': 'Java/RAG 面试',
    'interview-java-core': 'Java 深挖',
    'interview-rag-architecture': 'RAG 架构',
    'knowledge-qa': '知识库问答',
    'workspace-project': '项目助理',
    'writing-coach': '写作教练',
    'learning-review': '会话复盘',
    'session-review': '风险复盘'
  }
  return labels[worldState.value.currentScene] ?? worldState.value.currentScene
})

const affinityEntries = computed(() => Object.entries(worldState.value.affinityScores))

onMounted(() => {
  isHydrated.value = true

  try {
  const savedSettings = localStorage.getItem(SETTINGS_STORAGE_KEY)
  if (savedSettings) Object.assign(settings, defaultSettings, JSON.parse(savedSettings) as Partial<ApiSettings>)

  const savedSessionId = localStorage.getItem(SESSION_STORAGE_KEY)
  sessionId.value = savedSessionId || `session-${Date.now()}`
  localStorage.setItem(SESSION_STORAGE_KEY, sessionId.value)

  const savedDraft = localStorage.getItem(STORY_DRAFT_STORAGE_KEY)
  if (savedDraft) {
    storyDraft.value = normalizeStoryCatalog(JSON.parse(savedDraft) as StoryCatalogResponse)
    storyStatus.value = '已从本地恢复任务流草稿。'
  }

  const savedAgents = localStorage.getItem(AGENT_DRAFT_STORAGE_KEY)
  if (savedAgents) {
    agentDraft.value = normalizeAgentCatalog(JSON.parse(savedAgents) as AgentCatalogResponse)
    agentStatus.value = '已从本地恢复 Agent 草稿。'
  }

  const savedSessionExport = localStorage.getItem(SESSION_EXPORT_STORAGE_KEY)
  if (savedSessionExport) {
    sessionExport.value = normalizeSessionExport(JSON.parse(savedSessionExport) as SessionExportResponse)
    sessionStatus.value = '已从本地恢复 Session 导出。'
  }

  const savedSandboxPlans = localStorage.getItem(SANDBOX_PLAN_STORAGE_KEY)
  if (savedSandboxPlans) {
    sandboxPlans.value = normalizeSandboxPlans(JSON.parse(savedSandboxPlans) as SavedSandboxPlan[])
  }

  if (sessionExport.value) {
    replaceSessionSandboxPlans(sessionExport.value.sessionId, sessionExport.value.sandboxPlans)
  }
  } catch {
    errorMessage.value = 'Local cache could not be restored; using defaults instead.'
  }
})

watch(() => activeView.value, async (view) => {
  saveStatus.value = ''
  errorMessage.value = ''
  if (view === 'studio') {
    if (!storyDraft.value) await loadStoryCatalog()
    if (!agentDraft.value) await loadAgentCatalog()
  }
})

function persistSettings() {
  localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(settings))
  saveStatus.value = '设置已保存，本地刷新后仍会保留。'
}

function resetSettings() {
  Object.assign(settings, defaultSettings)
  persistSettings()
}

function persistStoryDraft(status = '草稿已保存到本地。') {
  if (!storyDraft.value) return
  localStorage.setItem(STORY_DRAFT_STORAGE_KEY, JSON.stringify(storyDraft.value))
  storyStatus.value = status
  storyError.value = ''
  validationStatus.value = ''
  validationError.value = ''
  generatorStatus.value = ''
  generatorError.value = ''
}

function persistAgentDraft(status = 'Agent 草稿已保存到本地。') {
  if (!agentDraft.value) return
  localStorage.setItem(AGENT_DRAFT_STORAGE_KEY, JSON.stringify(agentDraft.value))
  agentStatus.value = status
  agentError.value = ''
}

function persistSessionExport(status = 'Session export cached locally.') {
  if (!sessionExport.value) return
  localStorage.setItem(SESSION_EXPORT_STORAGE_KEY, JSON.stringify(sessionExport.value))
  sessionStatus.value = status
  sessionError.value = ''
}

function persistSandboxPlans(status = 'Sandbox plans cached locally.') {
  localStorage.setItem(SANDBOX_PLAN_STORAGE_KEY, JSON.stringify(sandboxPlans.value))
  sessionStatus.value = status
  sessionError.value = ''
}

function replaceSessionSandboxPlans(targetSessionId: string, plans: SavedSandboxPlan[] | null | undefined) {
  const normalizedPlans = normalizeSandboxPlans(plans)
  const existingPlans = sandboxPlans.value.filter(plan => plan.sessionId === targetSessionId)
  sandboxPlans.value = [
    ...(normalizedPlans.length ? normalizedPlans : existingPlans),
    ...sandboxPlans.value.filter(plan => plan.sessionId !== targetSessionId)
  ]
}

function normalizeStoryCatalog(catalog: StoryCatalogResponse): StoryCatalogResponse {
  return {
    entryNodeId: catalog.entryNodeId,
    nodes: catalog.nodes.map(node => ({
      ...node,
      enterFlags: node.enterFlags ?? [],
      enterAffinityChanges: node.enterAffinityChanges ?? {},
      choices: node.choices.map(choice => ({
        ...choice,
        requiredFlags: choice.requiredFlags ?? [],
        blockedFlags: choice.blockedFlags ?? [],
        minimumAffinity: choice.minimumAffinity ?? {},
        keywords: choice.keywords ?? [],
        flagsToAdd: choice.flagsToAdd ?? [],
        affinityChanges: choice.affinityChanges ?? {}
      }))
    }))
  }
}

function normalizeAgentCatalog(catalog: AgentCatalogResponse): AgentCatalogResponse {
  return {
    agents: (catalog.agents ?? []).map(agent => ({
      ...agent,
      activeScenes: agent.activeScenes ?? [],
      personalityParameters: agent.personalityParameters ?? {},
      coreGoals: agent.coreGoals ?? [],
      hiddenMotives: agent.hiddenMotives ?? [],
      initiative: Number.isFinite(agent.initiative) ? agent.initiative : 5,
      memoryStyle: agent.memoryStyle ?? 'episodic'
    }))
  }
}

function toBranchOptions(labels: string[]): BranchOptionView[] {
  return (labels ?? []).map(label => ({
    label,
    intent: 'pivot',
    risk: 'medium',
    targetMood: 'volatile',
    targetAgentId: '',
    consequenceSummary: 'This next action keeps the Agent session moving without a stored prediction.',
    relationshipDelta: 0,
    addedFlags: [],
    removedFlags: [],
      source: 'container'
  }))
}

function normalizeBranchOptions(options: BranchOptionView[] | null | undefined, fallbackLabels: string[] = []) {
  const normalized = (options ?? [])
    .map(option => ({
      label: option.label ?? '',
      intent: option.intent ?? 'pivot',
      risk: option.risk ?? 'medium',
      targetMood: option.targetMood ?? 'volatile',
      targetAgentId: option.targetAgentId ?? '',
      consequenceSummary: option.consequenceSummary ?? '',
      relationshipDelta: Number.isFinite(option.relationshipDelta) ? option.relationshipDelta : 0,
      addedFlags: option.addedFlags ?? [],
      removedFlags: option.removedFlags ?? [],
      source: option.source ?? 'container'
    }))
    .filter(option => option.label.trim().length > 0)

  return normalized.length > 0 ? normalized : toBranchOptions(fallbackLabels)
}

function normalizeSandboxPlan(plan: SavedSandboxPlan): SavedSandboxPlan {
  const steps = normalizeBranchOptions(plan.steps)
  return {
    id: plan.id ?? `sandbox-${Date.now()}`,
    sessionId: plan.sessionId ?? '',
    createdAt: plan.createdAt ?? new Date().toISOString(),
    sceneId: plan.sceneId ?? '',
    nodeId: plan.nodeId ?? '',
    title: plan.title?.trim() || `Sandbox ${steps[0]?.label ?? 'plan'}`,
    summary: plan.summary?.trim() || steps.map(step => step.consequenceSummary).join(' '),
    steps,
    totalRelationshipDelta: Number.isFinite(plan.totalRelationshipDelta) ? plan.totalRelationshipDelta : 0,
    finalFlags: plan.finalFlags ?? [],
    finalAffinityScores: plan.finalAffinityScores ?? {}
  }
}

function normalizeSandboxPlans(plans: SavedSandboxPlan[] | null | undefined) {
  return (Array.isArray(plans) ? plans : []).map(plan => normalizeSandboxPlan(plan))
}

function normalizeOrchestration(orchestration: OrchestrationTraceView | null | undefined) {
  if (!orchestration) return null
  return {
    ...orchestration,
    planner: {
      sceneGoal: orchestration.planner?.sceneGoal ?? '',
      tensionLabel: orchestration.planner?.tensionLabel ?? '',
      pacingLabel: orchestration.planner?.pacingLabel ?? '',
      directorIntent: orchestration.planner?.directorIntent ?? '',
      risks: orchestration.planner?.risks ?? []
    },
    critic: {
      verdict: orchestration.critic?.verdict ?? 'unknown',
      focusScore: Number.isFinite(orchestration.critic?.focusScore) ? orchestration.critic.focusScore : 0,
      castCoverageScore: Number.isFinite(orchestration.critic?.castCoverageScore) ? orchestration.critic.castCoverageScore : 0,
      choicePressureScore: Number.isFinite(orchestration.critic?.choicePressureScore) ? orchestration.critic.choicePressureScore : 0,
      notes: orchestration.critic?.notes ?? []
    },
    aiInvocations: (orchestration.aiInvocations ?? []).map(invocation => ({
      operation: invocation.operation ?? 'unknown',
      targetId: invocation.targetId ?? '',
      providerAttempted: !!invocation.providerAttempted,
      providerSucceeded: !!invocation.providerSucceeded,
      fallbackUsed: !!invocation.fallbackUsed,
      providerUrl: invocation.providerUrl ?? '',
      model: invocation.model ?? '',
      latencyMs: Number.isFinite(invocation.latencyMs) ? invocation.latencyMs : 0,
      errorMessage: invocation.errorMessage ?? ''
    })),
    spotlightAgentIds: orchestration.spotlightAgentIds ?? [],
    activeAgentIds: orchestration.activeAgentIds ?? [],
    speakingAgentIds: orchestration.speakingAgentIds ?? [],
    nextChoices: orchestration.nextChoices ?? [],
    nextBranchOptions: normalizeBranchOptions(orchestration.nextBranchOptions, orchestration.nextChoices ?? []),
    plans: (orchestration.plans ?? []).map(plan => ({
      ...plan,
      scoreFactors: (plan.scoreFactors ?? []).map(factor => ({
        code: factor.code ?? 'unknown',
        delta: Number.isFinite(factor.delta) ? factor.delta : 0,
        reason: factor.reason ?? ''
      }))
    }))
  }
}

function normalizeSessionExport(exportData: SessionExportResponse): SessionExportResponse {
  return {
    ...exportData,
    agents: exportData.agents ?? [],
    currentState: {
      ...exportData.currentState,
      flags: exportData.currentState?.flags ?? [],
      affinityScores: exportData.currentState?.affinityScores ?? {},
      relationships: exportData.currentState?.relationships ?? {}
    },
    sandboxPlans: normalizeSandboxPlans(exportData.sandboxPlans),
    turns: (exportData.turns ?? []).map(turn => ({
      ...turn,
      parentTurnId: turn.parentTurnId ?? null,
      storyEventId: turn.storyEventId ?? null,
      directorMessage: turn.directorMessage ?? '',
      presentedChoices: turn.presentedChoices ?? [],
      presentedBranchOptions: normalizeBranchOptions(turn.presentedBranchOptions, turn.presentedChoices ?? []),
      agentReplies: turn.agentReplies ?? [],
      orchestration: normalizeOrchestration(turn.orchestration),
      stateSnapshot: {
        ...turn.stateSnapshot,
        flags: turn.stateSnapshot?.flags ?? [],
        affinityScores: turn.stateSnapshot?.affinityScores ?? {},
        relationships: turn.stateSnapshot?.relationships ?? {}
      }
    }))
  }
}

function normalizeStoryAnalysis(analysis: StoryAnalysisResponse): StoryAnalysisResponse {
  return {
    ...analysis,
    reachableNodeIds: analysis.reachableNodeIds ?? [],
    unreachableNodeIds: analysis.unreachableNodeIds ?? [],
    endingNodeIds: analysis.endingNodeIds ?? [],
    sceneIds: analysis.sceneIds ?? [],
    issues: (analysis.issues ?? []).map(issue => ({
      severity: issue.severity ?? 'warning',
      code: issue.code ?? 'unknown',
      message: issue.message ?? 'Unknown issue.',
      nodeId: issue.nodeId ?? null,
      choiceId: issue.choiceId ?? null
    }))
  }
}

async function loadStoryCatalog() {
  if (!settings.backendUrl.trim()) {
    activeView.value = 'settings'
    storyError.value = '请先填写后端地址，再加载任务流。'
    return
  }

  isLoadingStory.value = true
  storyStatus.value = ''
  storyError.value = ''

  try {
    const response = await $fetch<StoryCatalogResponse>('/agent/story', {
      baseURL: settings.backendUrl.trim(),
      headers: buildHeaders()
    })
    storyDraft.value = normalizeStoryCatalog(JSON.parse(JSON.stringify(response)) as StoryCatalogResponse)
    storyAnalysis.value = null
    persistStoryDraft('已从后端载入任务流，当前进入本地草稿编辑模式。')
  } catch (error) {
    storyError.value = error instanceof Error ? `加载失败：${error.message}` : '加载失败，请检查后端地址或接口状态。'
  } finally {
    isLoadingStory.value = false
  }
}

async function loadAgentCatalog() {
  if (!settings.backendUrl.trim()) {
    activeView.value = 'settings'
    agentError.value = '请先填写后端地址，再加载 Agent 编排。'
    return
  }

  isLoadingAgents.value = true
  agentStatus.value = ''
  agentError.value = ''

  try {
    const response = await $fetch<AgentCatalogResponse>('/agent/agents', {
      baseURL: settings.backendUrl.trim(),
      headers: buildHeaders()
    })
    agentDraft.value = normalizeAgentCatalog(JSON.parse(JSON.stringify(response)) as AgentCatalogResponse)
    persistAgentDraft('已从后端载入 Agent 编排草稿。')
  } catch (error) {
    agentError.value = error instanceof Error ? `加载失败：${error.message}` : '加载失败，请检查后端地址或接口状态。'
  } finally {
    isLoadingAgents.value = false
  }
}

async function loadSessionExport(targetSessionId = sessionId.value) {
  const normalizedSessionId = targetSessionId.trim()
  if (!normalizedSessionId) {
    sessionError.value = '请先提供 sessionId。'
    return
  }
  if (!settings.backendUrl.trim()) {
    activeView.value = 'settings'
    sessionError.value = '请先填写后端地址，再读取 session 导出。'
    return
  }

  isLoadingSessionExport.value = true
  sessionStatus.value = ''
  sessionError.value = ''

  try {
    const response = await $fetch<SessionExportResponse>(`/agent/export/${encodeURIComponent(normalizedSessionId)}`, {
      baseURL: settings.backendUrl.trim(),
      headers: buildHeaders()
    })
    sessionExport.value = normalizeSessionExport(JSON.parse(JSON.stringify(response)) as SessionExportResponse)
    replaceSessionSandboxPlans(sessionExport.value.sessionId, sessionExport.value.sandboxPlans)
    localStorage.setItem(SANDBOX_PLAN_STORAGE_KEY, JSON.stringify(sandboxPlans.value))
    persistSessionExport('Session export loaded from backend.')
  } catch (error) {
    sessionError.value = error instanceof Error ? `读取失败：${error.message}` : '读取失败，请检查 sessionId 或后端接口。'
  } finally {
    isLoadingSessionExport.value = false
  }
}

async function exportStoryDraft() {
  if (!storyDraft.value) return
  const payload = JSON.stringify(storyDraft.value, null, 2)
  try {
    await navigator.clipboard.writeText(payload)
    storyStatus.value = '任务流草稿 JSON 已复制到剪贴板。'
  } catch {
    storyStatus.value = '浏览器未允许写入剪贴板，但草稿仍已保存在本地。'
  }
}

async function exportAgentDraft() {
  if (!agentDraft.value) return
  const payload = JSON.stringify(agentDraft.value, null, 2)
  try {
    await navigator.clipboard.writeText(payload)
    agentStatus.value = 'Agent 编排 JSON 已复制到剪贴板。'
  } catch {
    agentStatus.value = '浏览器未允许写入剪贴板，但 Agent 草稿仍已保存在本地。'
  }
}

async function exportSessionJson() {
  if (!sessionExport.value) return
  const payload = JSON.stringify(sessionExport.value, null, 2)
  try {
    await navigator.clipboard.writeText(payload)
    sessionStatus.value = 'Session export JSON copied to clipboard.'
  } catch {
    sessionStatus.value = 'Clipboard write was blocked, but the session export remains cached locally.'
  }
}

async function exportSandboxPlans() {
  const payload = JSON.stringify(sandboxPlans.value, null, 2)
  try {
    await navigator.clipboard.writeText(payload)
    sessionStatus.value = 'Sandbox plans JSON copied to clipboard.'
    sessionError.value = ''
  } catch {
    sessionStatus.value = 'Clipboard write was blocked, but sandbox plans remain cached locally.'
    sessionError.value = ''
  }
}

async function saveStoryDraftToBackend() {
  if (!storyDraft.value) {
    storyError.value = '当前没有可保存的任务流草稿。'
    return
  }
  if (!settings.backendUrl.trim()) {
    activeView.value = 'settings'
    storyError.value = '请先填写后端地址，再保存任务流。'
    return
  }

  isSavingStory.value = true
  storyError.value = ''
  try {
    const response = await $fetch<StoryCatalogResponse>('/agent/story', {
      baseURL: settings.backendUrl.trim(),
      method: 'PUT',
      body: storyDraft.value,
      headers: buildHeaders()
    })
    storyDraft.value = normalizeStoryCatalog(response)
    storyAnalysis.value = null
    persistStoryDraft('已保存到后端，并同步更新本地草稿。')
  } catch (error) {
    storyError.value = error instanceof Error ? `保存失败：${error.message}` : '保存失败，请检查后端接口状态。'
  } finally {
    isSavingStory.value = false
  }
}

async function validateStoryDraft() {
  if (!storyDraft.value) {
    validationError.value = 'No draft is loaded.'
    return
  }
  if (!settings.backendUrl.trim()) {
    activeView.value = 'settings'
    validationError.value = 'Configure the backend URL before validating.'
    return
  }

  isValidatingStory.value = true
  validationStatus.value = ''
  validationError.value = ''
  try {
    const request: StoryAnalysisRequest = {
      story: storyDraft.value,
      agents: agentDraft.value
    }
    const response = await $fetch<StoryAnalysisResponse>('/agent/story/analyze', {
      baseURL: settings.backendUrl.trim(),
      method: 'POST',
      body: request,
      headers: buildHeaders()
    })
    storyAnalysis.value = normalizeStoryAnalysis(response)
    validationStatus.value = `Validation complete: ${response.errorCount} errors, ${response.warningCount} warnings.`
  } catch (error) {
    validationError.value = error instanceof Error ? `Validation failed: ${error.message}` : 'Validation failed.'
  } finally {
    isValidatingStory.value = false
  }
}

async function generateStoryDraft(request: StoryGenerationRequest) {
  if (!settings.backendUrl.trim()) {
    activeView.value = 'settings'
    generatorError.value = 'Configure the backend URL before generating.'
    return
  }

  isGeneratingStory.value = true
  generatorStatus.value = ''
  generatorError.value = ''
  try {
    const response = await $fetch<StoryGenerationResponse>('/agent/story/generate', {
      baseURL: settings.backendUrl.trim(),
      method: 'POST',
      body: request,
      headers: buildHeaders()
    })
    storyDraft.value = normalizeStoryCatalog(response.story)
    agentDraft.value = normalizeAgentCatalog(response.agents)
    storyAnalysis.value = normalizeStoryAnalysis(response.analysis)
    persistStoryDraft('Generated task-flow draft cached locally.')
    persistAgentDraft('Generated Agent draft cached locally.')
    generatorStatus.value = response.summary
    validationStatus.value = `Validation complete: ${response.analysis.errorCount} errors, ${response.analysis.warningCount} warnings.`
    validationError.value = ''
  } catch (error) {
    generatorError.value = error instanceof Error ? `Generation failed: ${error.message}` : 'Generation failed.'
  } finally {
    isGeneratingStory.value = false
  }
}

async function saveAgentDraftToBackend() {
  if (!agentDraft.value) {
    agentError.value = '当前没有可保存的 Agent 草稿。'
    return
  }
  if (!settings.backendUrl.trim()) {
    activeView.value = 'settings'
    agentError.value = '请先填写后端地址，再保存 Agent 编排。'
    return
  }

  isSavingAgents.value = true
  agentError.value = ''
  try {
    const response = await $fetch<AgentCatalogResponse>('/agent/agents', {
      baseURL: settings.backendUrl.trim(),
      method: 'PUT',
      body: agentDraft.value,
      headers: buildHeaders()
    })
    agentDraft.value = normalizeAgentCatalog(response)
    persistAgentDraft('已保存到后端，并同步更新本地 Agent 草稿。')
  } catch (error) {
    agentError.value = error instanceof Error ? `保存失败：${error.message}` : '保存失败，请检查后端接口状态。'
  } finally {
    isSavingAgents.value = false
  }
}

function resetStoryDraft() {
  localStorage.removeItem(STORY_DRAFT_STORAGE_KEY)
  storyDraft.value = null
  storyAnalysis.value = null
  storyStatus.value = '本地草稿已清空。'
  storyError.value = ''
}

function resetAgentDraft() {
  localStorage.removeItem(AGENT_DRAFT_STORAGE_KEY)
  agentDraft.value = null
  agentStatus.value = '本地 Agent 草稿已清空。'
  agentError.value = ''
}

function resetSessionExport() {
  localStorage.removeItem(SESSION_EXPORT_STORAGE_KEY)
  sessionExport.value = null
  sessionStatus.value = 'Local session export cache cleared.'
  sessionError.value = ''
}

async function saveSandboxPlan(plan: SandboxPlanDraft) {
  if (!settings.backendUrl.trim()) {
    const savedPlan = normalizeSandboxPlan({
      ...plan,
      id: `sandbox-${Date.now()}`,
      sessionId: sessionId.value,
      createdAt: new Date().toISOString()
    })
    sandboxPlans.value = [savedPlan, ...sandboxPlans.value]
    persistSandboxPlans(`Sandbox run saved locally for ${savedPlan.sceneId || 'current workspace'}.`)
    return
  }

  try {
    const response = await $fetch<SessionExportResponse>(`/agent/export/${encodeURIComponent(sessionId.value)}/sandbox-plans`, {
      baseURL: settings.backendUrl.trim(),
      method: 'POST',
      body: plan,
      headers: buildHeaders()
    })
    sessionExport.value = normalizeSessionExport(JSON.parse(JSON.stringify(response)) as SessionExportResponse)
    replaceSessionSandboxPlans(sessionExport.value.sessionId, sessionExport.value.sandboxPlans)
    localStorage.setItem(SANDBOX_PLAN_STORAGE_KEY, JSON.stringify(sandboxPlans.value))
    persistSessionExport(`Sandbox run saved to backend export for ${plan.sceneId || 'current workspace'}.`)
  } catch (error) {
    const fallbackPlan = normalizeSandboxPlan({
      ...plan,
      id: `sandbox-${Date.now()}`,
      sessionId: sessionId.value,
      createdAt: new Date().toISOString()
    })
    sandboxPlans.value = [fallbackPlan, ...sandboxPlans.value]
    persistSandboxPlans(`Sandbox run saved locally for ${fallbackPlan.sceneId || 'current workspace'}.`)
    sessionError.value = error instanceof Error
      ? `Sandbox sync failed, kept local copy: ${error.message}`
      : 'Sandbox sync failed, kept local copy.'
  }
}

function resetSandboxPlans() {
  localStorage.removeItem(SANDBOX_PLAN_STORAGE_KEY)
  sandboxPlans.value = []
  sessionStatus.value = 'Local sandbox plan cache cleared.'
  sessionError.value = ''
}

function buildHeaders() {
  return {
    ...(settings.apiKey.trim()
      ? { Authorization: `Bearer ${settings.apiKey.trim()}`, 'X-API-Key': settings.apiKey.trim() }
      : {}),
    ...(settings.providerUrl.trim() ? { 'X-Provider-Url': settings.providerUrl.trim() } : {}),
    ...(settings.model.trim() ? { 'X-Model': settings.model.trim() } : {})
  }
}

async function sendTurn(choice = '') {
  if (isSending.value) return
  const trimmedInput = playerInput.value.trim()
  const trimmedChoice = choice.trim()

  if (!trimmedInput && !trimmedChoice) {
    errorMessage.value = '请输入一句需求描述，或直接选择一个 Agent 模式。'
    return
  }
  if (!settings.backendUrl.trim()) {
    activeView.value = 'settings'
    errorMessage.value = '请先在设置页填写后端地址。'
    return
  }

  messages.value.push({
    id: `player-${Date.now()}`,
    role: 'player',
    speaker: '你',
    content: trimmedInput || `选择了「${trimmedChoice}」`
  })

  errorMessage.value = ''
  isSending.value = true
  try {
    const response = await $fetch<GameResponse>('/agent/next', {
      baseURL: settings.backendUrl.trim(),
      method: 'POST',
      body: { sessionId: sessionId.value, input: trimmedInput, choice: trimmedChoice },
      headers: buildHeaders()
    })
    messages.value.push({
      id: `assistant-${Date.now()}`,
      role: 'assistant',
      speaker: 'Agent 容器',
      content: response.message
    })
    currentBranchOptions.value = normalizeBranchOptions(response.branchOptions, response.choices)
    worldState.value = response.state
    currentOrchestration.value = normalizeOrchestration(response.orchestration)
    playerInput.value = ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? `请求失败：${error.message}` : '请求失败，请检查后端地址或 API 配置。'
  } finally {
    isSending.value = false
  }
}
</script>

<template>
  <div class="shell">
    <NuxtRouteAnnouncer />

    <div v-if="isHydrated" class="frame">
      <header class="topbar">
        <div>
          <p class="eyebrow">Kiniu Agent Container</p>
          <h1>Agent 容器</h1>
        </div>

        <nav class="nav">
          <button class="nav-button" :class="{ active: activeView === 'chat' }" type="button" @click="activeView = 'chat'">对话</button>
          <button class="nav-button" :class="{ active: activeView === 'studio' }" type="button" @click="activeView = 'studio'">Agent Studio</button>
          <button class="nav-button" :class="{ active: activeView === 'settings' }" type="button" @click="activeView = 'settings'">设置</button>
        </nav>
      </header>

      <main class="workspace">
        <AgentConsoleView
          v-if="activeView === 'chat'"
          v-model:player-input="playerInput"
          :settings="settings"
          :world-state="worldState"
          :scene-label="sceneLabel"
          :affinity-entries="affinityEntries"
          :current-branch-options="currentBranchOptions"
          :orchestration="currentOrchestration"
          :messages="messages"
          :is-sending="isSending"
          @save-sandbox="saveSandboxPlan"
          @send-turn="sendTurn"
        />

        <AgentStudioView
          v-else-if="activeView === 'studio'"
          :draft="storyDraft"
          :agent-draft="agentDraft"
          :session-export="sessionExport"
          :sandbox-plans="sandboxPlans"
          :story-analysis="storyAnalysis"
          :current-session-id="sessionId"
          :backend-url="settings.backendUrl"
          :is-loading-story="isLoadingStory"
          :is-saving-story="isSavingStory"
          :is-loading-agents="isLoadingAgents"
          :is-saving-agents="isSavingAgents"
          :is-loading-session="isLoadingSessionExport"
          :is-generating-story="isGeneratingStory"
          :is-validating-story="isValidatingStory"
          :story-status="storyStatus"
          :story-error="storyError"
          :agent-status="agentStatus"
          :agent-error="agentError"
          :session-status="sessionStatus"
          :session-error="sessionError"
          :generator-status="generatorStatus"
          :generator-error="generatorError"
          :validation-status="validationStatus"
          :validation-error="validationError"
          @load-story="loadStoryCatalog"
          @persist-draft="persistStoryDraft"
          @publish-draft="saveStoryDraftToBackend"
          @export-draft="exportStoryDraft"
          @reset-draft="resetStoryDraft"
          @load-agents="loadAgentCatalog"
          @persist-agents="persistAgentDraft"
          @publish-agents="saveAgentDraftToBackend"
          @export-agents="exportAgentDraft"
          @reset-agents="resetAgentDraft"
          @validate-story="validateStoryDraft"
          @generate-story="generateStoryDraft"
          @load-session="loadSessionExport"
          @export-session="exportSessionJson"
          @reset-session="resetSessionExport"
          @export-sandbox-plans="exportSandboxPlans"
          @reset-sandbox-plans="resetSandboxPlans"
        />

        <SettingsPanelView
          v-else
          :settings="settings"
          :save-status="saveStatus"
          @persist="persistSettings"
          @reset="resetSettings"
        />
      </main>

      <div class="status-row">
        <p v-if="errorMessage" class="status error">{{ errorMessage }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
:global(:root){
  --color-primary:#0d9488;
  --color-primary-strong:#0f766e;
  --color-secondary:#14b8a6;
  --color-accent:#ea580c;
  --color-bg:#f0fdfa;
  --color-bg-soft:#f7fffd;
  --color-surface:#ffffff;
  --color-surface-muted:#ecfdf5;
  --color-border:#b6e7df;
  --color-border-strong:#5eead4;
  --color-text:#123c3a;
  --color-muted:#55706d;
  --color-faint:#78918d;
  --color-danger:#dc2626;
  --color-success:#15803d;
  --shadow-soft:0 18px 48px rgba(15,118,110,.12);
  --shadow-card:0 1px 2px rgba(15,23,42,.06),0 10px 28px rgba(15,118,110,.08);
  --radius:8px;
  --ease:cubic-bezier(.2,.8,.2,1);
}
:global(body){
  margin:0;
  font-family:Inter,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;
  background:
    radial-gradient(circle at 8% 0%,rgba(20,184,166,.18),transparent 30%),
    linear-gradient(180deg,#f0fdfa 0%,#f7fffd 42%,#eef9f6 100%);
  color:var(--color-text);
  -webkit-font-smoothing:antialiased;
}
:global(*){box-sizing:border-box}
:global(button),:global(input),:global(textarea){font:inherit}
:global(:focus-visible){outline:3px solid rgba(234,88,12,.32);outline-offset:2px}
.shell{min-height:100dvh}
.frame{width:min(1480px,calc(100vw - 32px));margin:0 auto;padding:24px 0 40px}
.topbar{display:flex;align-items:center;justify-content:space-between;gap:24px;padding:10px 0 24px}
.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:.18em;text-transform:uppercase;color:var(--color-primary-strong);font-weight:700}
h1,p{margin:0}
h1{font-size:clamp(34px,5vw,54px);line-height:1;letter-spacing:0;color:#102f2d}
.nav{display:inline-flex;gap:4px;padding:4px;border:1px solid var(--color-border);border-radius:var(--radius);background:rgba(255,255,255,.78);box-shadow:var(--shadow-card)}
.nav-button{appearance:none;border:0;cursor:pointer;min-height:44px;padding:0 18px;border-radius:6px;color:var(--color-muted);background:transparent;font-size:14px;font-weight:700;white-space:nowrap;transition:background 180ms var(--ease),color 180ms var(--ease),box-shadow 180ms var(--ease)}
.nav-button.active{background:var(--color-primary);color:#fff;box-shadow:0 8px 22px rgba(13,148,136,.22)}
.nav-button:hover{background:#e6fffb;color:var(--color-primary-strong)}
.nav-button.active:hover{background:var(--color-primary-strong);color:#fff}
.workspace{display:grid;min-height:calc(100dvh - 166px)}
.status-row{display:flex;justify-content:flex-start;gap:12px;flex-wrap:wrap;margin-top:14px}
.status{display:inline-flex;align-items:center;min-height:44px;padding:10px 14px;border-radius:var(--radius);line-height:1.5}
.status.error{color:#991b1b;background:#fee2e2;border:1px solid #fecaca}
@media (max-width:960px){
  .frame{width:min(100vw - 20px,1480px);padding-top:16px}
  .topbar{display:grid;align-items:start}
  .nav{width:100%;display:grid;grid-template-columns:repeat(3,minmax(0,1fr))}
  .nav-button{padding:0 8px;font-size:13px}
}
@media (prefers-reduced-motion:reduce){
  .nav-button{transition:none}
}
</style>
