<script setup lang="ts">
import AgentConsoleView from '../components/GameConsoleView.vue'
import AgentStudioView from '../components/StoryEditorView.vue'
import SettingsPanelView from '../components/SettingsPanelView.vue'
import { normalizeLocale, provideUiI18n, type I18nKey } from '../i18n'
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

type ViewMode = 'chat' | 'flow' | 'agents' | 'debug' | 'settings'
type ThemeMode = ApiSettings['theme']

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
  model: 'gpt-4.1-mini',
  locale: 'zh-CN',
  theme: 'light'
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
const currentLocale = computed(() => normalizeLocale(settings.locale))
const currentTheme = computed(() => normalizeTheme(settings.theme))
const { t } = provideUiI18n(currentLocale)
const storyDraft = ref<StoryCatalogResponse | null>(null)
const agentDraft = ref<AgentCatalogResponse | null>(null)
const sessionExport = ref<SessionExportResponse | null>(null)
const sandboxPlans = ref<SavedSandboxPlan[]>([])
const storyAnalysis = ref<StoryAnalysisResponse | null>(null)
const currentOrchestration = ref<OrchestrationTraceView | null>(null)
const currentBranchOptions = ref<BranchOptionView[]>(toBranchOptions(defaultModeLabels()))
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
  ...createIntroMessages()
])

const navigationItems = computed(() => [
  { id: 'chat' as const, label: t('navChat'), meta: sceneLabel.value, key: 'C' },
  { id: 'flow' as const, label: t('studioTitle'), meta: storyDraft.value?.entryNodeId || t('fieldNotEntered'), key: 'F' },
  { id: 'agents' as const, label: t('agentStageTitle'), meta: `${agentDraft.value?.agents.length ?? 0}`, key: 'A' },
  { id: 'debug' as const, label: t('sessionDebugTitle'), meta: `${sessionExport.value?.turns.length ?? 0}`, key: 'D' },
  { id: 'settings' as const, label: t('navSettings'), meta: settings.backendUrl || t('fieldNotConfigured'), key: 'S' }
])

const sceneLabel = computed(() => {
  const labels: Record<string, I18nKey> = {
    'agent-hub': 'sceneAgentHub',
    'companion-check-in': 'sceneCompanion',
    'interview-java-rag': 'sceneInterviewJavaRag',
    'interview-java-core': 'sceneInterviewJavaCore',
    'interview-rag-architecture': 'sceneRagArchitecture',
    'knowledge-qa': 'sceneKnowledgeQa',
    'workspace-project': 'sceneProject',
    'writing-coach': 'sceneWriting',
    'learning-review': 'sceneLearningReview',
    'session-review': 'sceneSessionReview'
  }
  const key = labels[worldState.value.currentScene]
  return key ? t(key) : worldState.value.currentScene
})

const affinityEntries = computed(() => Object.entries(worldState.value.affinityScores))

watch(currentLocale, (locale) => {
  if (settings.locale !== locale) settings.locale = locale
  if (messages.value.every(message => message.id.startsWith('intro-'))) {
    messages.value = createIntroMessages()
  }
  if (!currentOrchestration.value) {
    currentBranchOptions.value = toBranchOptions(defaultModeLabels())
  }
})

watch(currentTheme, (theme) => {
  if (settings.theme !== theme) settings.theme = theme
  applyTheme(theme)
}, { immediate: true })

function normalizeTheme(theme?: string): ThemeMode {
  return theme === 'dark' ? 'dark' : 'light'
}

function applyTheme(theme: ThemeMode) {
  if (typeof document === 'undefined') return
  document.documentElement.dataset.theme = theme
  document.documentElement.style.colorScheme = theme
}

function defaultModeLabels() {
  return [t('modeCompanion'), t('modeInterview'), t('modeKnowledge'), t('modeProject')]
}

function createIntroMessages(): ChatMessage[] {
  return [
    {
      id: 'intro-system',
      role: 'system',
      speaker: t('systemSpeaker'),
      content: t('introSystem')
    },
    {
      id: 'intro-narrator',
      role: 'assistant',
      speaker: t('conductorSpeaker'),
      content: t('introConductor')
    }
  ]
}

onMounted(() => {
  isHydrated.value = true

  try {
  const savedSettings = localStorage.getItem(SETTINGS_STORAGE_KEY)
  if (savedSettings) {
    const parsedSettings = JSON.parse(savedSettings) as Partial<ApiSettings>
    Object.assign(settings, defaultSettings, parsedSettings, {
      locale: normalizeLocale(parsedSettings.locale),
      theme: normalizeTheme(parsedSettings.theme)
    })
  } else {
    settings.locale = normalizeLocale(settings.locale)
    settings.theme = normalizeTheme(settings.theme)
  }

  const savedSessionId = localStorage.getItem(SESSION_STORAGE_KEY)
  sessionId.value = savedSessionId || `session-${Date.now()}`
  localStorage.setItem(SESSION_STORAGE_KEY, sessionId.value)

  const savedDraft = localStorage.getItem(STORY_DRAFT_STORAGE_KEY)
  if (savedDraft) {
    storyDraft.value = normalizeStoryCatalog(JSON.parse(savedDraft) as StoryCatalogResponse)
    storyStatus.value = t('storyDraftRestored')
  }

  const savedAgents = localStorage.getItem(AGENT_DRAFT_STORAGE_KEY)
  if (savedAgents) {
    agentDraft.value = normalizeAgentCatalog(JSON.parse(savedAgents) as AgentCatalogResponse)
    agentStatus.value = t('agentDraftRestored')
  }

  const savedSessionExport = localStorage.getItem(SESSION_EXPORT_STORAGE_KEY)
  if (savedSessionExport) {
    sessionExport.value = normalizeSessionExport(JSON.parse(savedSessionExport) as SessionExportResponse)
    sessionStatus.value = t('sessionExportRestored')
  }

  const savedSandboxPlans = localStorage.getItem(SANDBOX_PLAN_STORAGE_KEY)
  if (savedSandboxPlans) {
    sandboxPlans.value = normalizeSandboxPlans(JSON.parse(savedSandboxPlans) as SavedSandboxPlan[])
  }

  if (sessionExport.value) {
    replaceSessionSandboxPlans(sessionExport.value.sessionId, sessionExport.value.sandboxPlans)
  }
  } catch {
    errorMessage.value = t('cacheRestoreFailed')
  }
})

watch(() => activeView.value, async (view) => {
  saveStatus.value = ''
  errorMessage.value = ''
  if (view === 'flow' && !storyDraft.value) {
    await loadStoryCatalog()
  }
  if (view === 'agents' && !agentDraft.value) {
    await loadAgentCatalog()
  }
  if (view === 'debug' && !sessionExport.value && settings.backendUrl.trim()) {
    await loadSessionExport()
  }
})

function persistSettings() {
  localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(settings))
  saveStatus.value = t('settingsSaved')
}

function resetSettings() {
  Object.assign(settings, defaultSettings)
  persistSettings()
}

function persistStoryDraft(status = t('flowDraftSaved')) {
  if (!storyDraft.value) return
  localStorage.setItem(STORY_DRAFT_STORAGE_KEY, JSON.stringify(storyDraft.value))
  storyStatus.value = status
  storyError.value = ''
  validationStatus.value = ''
  validationError.value = ''
  generatorStatus.value = ''
  generatorError.value = ''
}

function persistAgentDraft(status = t('agentDraftSaved')) {
  if (!agentDraft.value) return
  localStorage.setItem(AGENT_DRAFT_STORAGE_KEY, JSON.stringify(agentDraft.value))
  agentStatus.value = status
  agentError.value = ''
}

function persistSessionExport(status = t('sessionExportCached')) {
  if (!sessionExport.value) return
  localStorage.setItem(SESSION_EXPORT_STORAGE_KEY, JSON.stringify(sessionExport.value))
  sessionStatus.value = status
  sessionError.value = ''
}

function persistSandboxPlans(status = t('sandboxPlansCached')) {
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
    consequenceSummary: t('branchFallbackSummary'),
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
    title: plan.title?.trim() || t('sandboxDefaultTitle', { label: steps[0]?.label ?? t('labelPlans') }),
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
      message: issue.message ?? t('unknownIssue'),
      nodeId: issue.nodeId ?? null,
      choiceId: issue.choiceId ?? null
    }))
  }
}

async function loadStoryCatalog() {
  if (!settings.backendUrl.trim()) {
    activeView.value = 'settings'
    storyError.value = t('needBackendForStory')
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
    persistStoryDraft(t('storyDraftLoaded'))
  } catch (error) {
    storyError.value = error instanceof Error ? t('loadFailed', { message: error.message }) : t('loadFailedGeneric')
  } finally {
    isLoadingStory.value = false
  }
}

async function loadAgentCatalog() {
  if (!settings.backendUrl.trim()) {
    activeView.value = 'settings'
    agentError.value = t('needBackendForAgents')
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
    persistAgentDraft(t('agentDraftLoaded'))
  } catch (error) {
    agentError.value = error instanceof Error ? t('loadFailed', { message: error.message }) : t('loadFailedGeneric')
  } finally {
    isLoadingAgents.value = false
  }
}

async function loadSessionExport(targetSessionId = sessionId.value) {
  const normalizedSessionId = targetSessionId.trim()
  if (!normalizedSessionId) {
    sessionError.value = t('sessionIdRequired')
    return
  }
  if (!settings.backendUrl.trim()) {
    activeView.value = 'settings'
    sessionError.value = t('needBackendForSession')
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
    persistSessionExport(t('sessionExportLoaded'))
  } catch (error) {
    sessionError.value = error instanceof Error ? t('readFailed', { message: error.message }) : t('readFailedGeneric')
  } finally {
    isLoadingSessionExport.value = false
  }
}

async function exportStoryDraft() {
  if (!storyDraft.value) return
  const payload = JSON.stringify(storyDraft.value, null, 2)
  try {
    await navigator.clipboard.writeText(payload)
    storyStatus.value = t('storyJsonCopied')
  } catch {
    storyStatus.value = t('clipboardBlockedStory')
  }
}

async function exportAgentDraft() {
  if (!agentDraft.value) return
  const payload = JSON.stringify(agentDraft.value, null, 2)
  try {
    await navigator.clipboard.writeText(payload)
    agentStatus.value = t('agentJsonCopied')
  } catch {
    agentStatus.value = t('clipboardBlockedAgent')
  }
}

async function exportSessionJson() {
  if (!sessionExport.value) return
  const payload = JSON.stringify(sessionExport.value, null, 2)
  try {
    await navigator.clipboard.writeText(payload)
    sessionStatus.value = t('sessionJsonCopied')
  } catch {
    sessionStatus.value = t('clipboardBlockedSession')
  }
}

async function exportSandboxPlans() {
  const payload = JSON.stringify(sandboxPlans.value, null, 2)
  try {
    await navigator.clipboard.writeText(payload)
    sessionStatus.value = t('sandboxJsonCopied')
    sessionError.value = ''
  } catch {
    sessionStatus.value = t('clipboardBlockedSandbox')
    sessionError.value = ''
  }
}

async function saveStoryDraftToBackend() {
  if (!storyDraft.value) {
    storyError.value = t('noStoryDraftToSave')
    return
  }
  if (!settings.backendUrl.trim()) {
    activeView.value = 'settings'
    storyError.value = t('needBackendForStory')
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
    persistStoryDraft(t('storyDraftSavedBackend'))
  } catch (error) {
    storyError.value = error instanceof Error ? t('saveFailed', { message: error.message }) : t('saveFailedGeneric')
  } finally {
    isSavingStory.value = false
  }
}

async function validateStoryDraft() {
  if (!storyDraft.value) {
    validationError.value = t('validationNoDraft')
    return
  }
  if (!settings.backendUrl.trim()) {
    activeView.value = 'settings'
    validationError.value = t('validationNeedBackend')
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
    validationStatus.value = t('validationComplete', { errors: response.errorCount, warnings: response.warningCount })
  } catch (error) {
    validationError.value = error instanceof Error ? t('validationFailed', { message: error.message }) : t('validationFailedGeneric')
  } finally {
    isValidatingStory.value = false
  }
}

async function generateStoryDraft(request: StoryGenerationRequest) {
  if (!settings.backendUrl.trim()) {
    activeView.value = 'settings'
    generatorError.value = t('generationNeedBackend')
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
    persistStoryDraft(t('generatedFlowCached'))
    persistAgentDraft(t('generatedAgentCached'))
    generatorStatus.value = response.summary
    validationStatus.value = t('validationComplete', { errors: response.analysis.errorCount, warnings: response.analysis.warningCount })
    validationError.value = ''
  } catch (error) {
    generatorError.value = error instanceof Error ? t('generationFailed', { message: error.message }) : t('generationFailedGeneric')
  } finally {
    isGeneratingStory.value = false
  }
}

async function saveAgentDraftToBackend() {
  if (!agentDraft.value) {
    agentError.value = t('noAgentDraftToSave')
    return
  }
  if (!settings.backendUrl.trim()) {
    activeView.value = 'settings'
    agentError.value = t('needBackendForAgents')
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
    persistAgentDraft(t('agentDraftSavedBackend'))
  } catch (error) {
    agentError.value = error instanceof Error ? t('saveFailed', { message: error.message }) : t('saveFailedGeneric')
  } finally {
    isSavingAgents.value = false
  }
}

function resetStoryDraft() {
  localStorage.removeItem(STORY_DRAFT_STORAGE_KEY)
  storyDraft.value = null
  storyAnalysis.value = null
  storyStatus.value = t('storyDraftCleared')
  storyError.value = ''
}

function resetAgentDraft() {
  localStorage.removeItem(AGENT_DRAFT_STORAGE_KEY)
  agentDraft.value = null
  agentStatus.value = t('agentDraftCleared')
  agentError.value = ''
}

function resetSessionExport() {
  localStorage.removeItem(SESSION_EXPORT_STORAGE_KEY)
  sessionExport.value = null
  sessionStatus.value = t('sessionExportCleared')
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
    persistSandboxPlans(t('sandboxSavedLocal', { workspace: savedPlan.sceneId || t('fieldWorkspace') }))
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
    persistSessionExport(t('sandboxSavedBackend', { workspace: plan.sceneId || t('fieldWorkspace') }))
  } catch (error) {
    const fallbackPlan = normalizeSandboxPlan({
      ...plan,
      id: `sandbox-${Date.now()}`,
      sessionId: sessionId.value,
      createdAt: new Date().toISOString()
    })
    sandboxPlans.value = [fallbackPlan, ...sandboxPlans.value]
    persistSandboxPlans(t('sandboxSavedLocal', { workspace: fallbackPlan.sceneId || t('fieldWorkspace') }))
    sessionError.value = error instanceof Error
      ? t('sandboxSyncFailed', { message: error.message })
      : t('sandboxSyncFailedGeneric')
  }
}

function resetSandboxPlans() {
  localStorage.removeItem(SANDBOX_PLAN_STORAGE_KEY)
  sandboxPlans.value = []
  sessionStatus.value = t('sandboxPlansCleared')
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
    errorMessage.value = t('needInput')
    return
  }
  if (!settings.backendUrl.trim()) {
    activeView.value = 'settings'
    errorMessage.value = t('needBackendForChat')
    return
  }

  messages.value.push({
    id: `player-${Date.now()}`,
    role: 'player',
    speaker: t('userSpeaker'),
    content: trimmedInput || t('selectedChoice', { choice: trimmedChoice })
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
      speaker: t('containerSpeaker'),
      content: response.message
    })
    currentBranchOptions.value = normalizeBranchOptions(response.branchOptions, response.choices)
    worldState.value = response.state
    currentOrchestration.value = normalizeOrchestration(response.orchestration)
    playerInput.value = ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? t('requestFailed', { message: error.message }) : t('requestFailedGeneric')
  } finally {
    isSending.value = false
  }
}
</script>

<template>
  <div class="shell" :data-theme="currentTheme">
    <NuxtRouteAnnouncer />

    <div v-if="isHydrated" class="workbench">
      <aside class="rail">
        <div class="brand-lockup">
          <span class="brand-mark">K</span>
          <div>
            <p class="eyebrow">{{ t('appBrand') }}</p>
            <h1>{{ t('appTitle') }}</h1>
          </div>
        </div>

        <nav class="nav" aria-label="Workspace">
          <button
            v-for="item in navigationItems"
            :key="item.id"
            class="nav-button"
            :class="{ active: activeView === item.id }"
            type="button"
            @click="activeView = item.id"
          >
            <span class="nav-key">{{ item.key }}</span>
            <span class="nav-copy">
              <strong>{{ item.label }}</strong>
              <small>{{ item.meta }}</small>
            </span>
          </button>
        </nav>

        <div class="rail-status">
          <span>{{ t('labelSession') }}</span>
          <strong>{{ sessionId }}</strong>
        </div>
      </aside>

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
          v-else-if="activeView === 'flow' || activeView === 'agents' || activeView === 'debug'"
          :mode="activeView"
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
  --color-primary:#0f766e;
  --color-primary-strong:#115e59;
  --color-secondary:#475569;
  --color-accent:#b45309;
  --color-bg:#f5f6f3;
  --color-bg-soft:#fafaf8;
  --color-surface:#ffffff;
  --color-surface-panel:rgba(255,255,255,.94);
  --color-surface-panel-strong:rgba(255,255,255,.96);
  --color-surface-muted:#eef4f2;
  --color-input:#ffffff;
  --color-hover:#eef6f4;
  --color-row:#f8faf9;
  --color-on-primary:#ffffff;
  --color-on-accent:#ffffff;
  --color-heading:#182322;
  --color-heading-soft:#263b38;
  --color-border:#d1d9d7;
  --color-border-soft:#e5ebe9;
  --color-border-strong:#7fb9af;
  --color-text:#243231;
  --color-muted:#61716f;
  --color-faint:#8a9895;
  --color-danger:#dc2626;
  --color-success:#15803d;
  --color-token-bg:#d8eee9;
  --color-token-text:#115e59;
  --color-token-muted-bg:#f0f2ef;
  --color-warning-bg:#fff7ed;
  --color-warning-text:#9a3412;
  --color-danger-bg:#fee2e2;
  --color-danger-border:#fecaca;
  --color-danger-text:#991b1b;
  --color-success-bg:#dcfce7;
  --color-success-border:#bbf7d0;
  --color-success-text:#166534;
  --color-danger-action:#b91c1c;
  --color-accent-hover:#92400e;
  --color-focus-ring:rgba(15,118,110,.14);
  --color-focus-global:rgba(180,83,9,.32);
  --color-hero-surface:#f8faf9;
  --color-graph-bg:#f8faf9;
  --color-graph-edge:rgba(15,118,110,.34);
  --color-graph-arrow:#c5b59a;
  --color-graph-node:#ffffff;
  --color-graph-node-border:#d1d9d7;
  --color-graph-node-selected:#d8eee9;
  --color-graph-entry:#16a34a;
  --page-background:linear-gradient(180deg,#f4f5f1 0%,#f8faf9 48%,#eef3f1 100%);
  --shadow-soft:0 18px 42px rgba(24,35,34,.08);
  --shadow-card:0 1px 2px rgba(15,23,42,.06),0 12px 28px rgba(24,35,34,.08);
  --shadow-active:0 8px 18px rgba(15,118,110,.12);
  --shadow-primary:0 8px 20px rgba(15,118,110,.2);
  --shadow-accent:0 10px 22px rgba(180,83,9,.18);
  --radius:8px;
  --ease:cubic-bezier(.2,.8,.2,1);
}
:global(:root[data-theme='dark']){
  --color-primary:#2dd4bf;
  --color-primary-strong:#5eead4;
  --color-secondary:#94a3b8;
  --color-accent:#fb923c;
  --color-bg:#0f1413;
  --color-bg-soft:#151b1a;
  --color-surface:#18211f;
  --color-surface-panel:rgba(24,33,31,.94);
  --color-surface-panel-strong:rgba(20,29,27,.96);
  --color-surface-muted:#1d3430;
  --color-input:#101817;
  --color-hover:#203632;
  --color-row:#151f1d;
  --color-on-primary:#05201d;
  --color-on-accent:#241008;
  --color-heading:#f4fbf8;
  --color-heading-soft:#d7e7e3;
  --color-border:#33413e;
  --color-border-soft:#263331;
  --color-border-strong:#2dd4bf;
  --color-text:#e2eeeb;
  --color-muted:#a3b7b3;
  --color-faint:#7f928e;
  --color-danger:#f87171;
  --color-success:#4ade80;
  --color-token-bg:#123f39;
  --color-token-text:#9df7ea;
  --color-token-muted-bg:#142927;
  --color-warning-bg:#3a2415;
  --color-warning-text:#fed7aa;
  --color-danger-bg:#3f1518;
  --color-danger-border:#7f1d1d;
  --color-danger-text:#fecaca;
  --color-success-bg:#12351f;
  --color-success-border:#166534;
  --color-success-text:#bbf7d0;
  --color-danger-action:#fca5a5;
  --color-accent-hover:#fdba74;
  --color-focus-ring:rgba(45,212,191,.18);
  --color-focus-global:rgba(251,146,60,.36);
  --color-hero-surface:#151f1d;
  --color-graph-bg:#101817;
  --color-graph-edge:rgba(45,212,191,.42);
  --color-graph-arrow:#5eead4;
  --color-graph-node:#10211f;
  --color-graph-node-border:#24534d;
  --color-graph-node-selected:#123f39;
  --color-graph-entry:#4ade80;
  --page-background:linear-gradient(180deg,#0f1413 0%,#151b1a 50%,#0f1413 100%);
  --shadow-soft:0 18px 48px rgba(0,0,0,.28);
  --shadow-card:0 1px 2px rgba(0,0,0,.35),0 16px 36px rgba(0,0,0,.22);
  --shadow-active:0 8px 18px rgba(0,0,0,.28);
  --shadow-primary:0 8px 22px rgba(45,212,191,.18);
  --shadow-accent:0 10px 22px rgba(251,146,60,.14);
}
:global(body){
  margin:0;
  font-family:Inter,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;
  background:var(--page-background);
  color:var(--color-text);
  -webkit-font-smoothing:antialiased;
}
:global(*){box-sizing:border-box}
:global(button),:global(input),:global(textarea){font:inherit}
:global(:focus-visible){outline:3px solid var(--color-focus-global);outline-offset:2px}
.shell{min-height:100dvh;background:var(--page-background);color:var(--color-text)}
.workbench{display:grid;grid-template-columns:248px minmax(0,1fr);gap:16px;width:min(1640px,calc(100vw - 28px));min-height:calc(100dvh - 28px);margin:0 auto;padding:14px 0}
.rail{position:sticky;top:14px;display:grid;grid-template-rows:auto 1fr auto;gap:18px;height:calc(100dvh - 28px);padding:16px;border:1px solid var(--color-border);border-radius:var(--radius);background:var(--color-surface-panel);box-shadow:var(--shadow-card)}
.brand-lockup{display:grid;grid-template-columns:38px minmax(0,1fr);gap:12px;align-items:center;min-width:0}
.brand-mark{display:grid;place-items:center;width:38px;height:38px;border-radius:var(--radius);background:var(--color-heading);color:var(--color-bg);font-size:16px;font-weight:900}
.eyebrow{margin:0 0 4px;font-size:11px;letter-spacing:.12em;text-transform:uppercase;color:var(--color-primary-strong);font-weight:800;line-height:1.2}
h1,p{margin:0}
h1{font-size:20px;line-height:1.1;letter-spacing:0;color:var(--color-heading);overflow-wrap:anywhere}
.nav{display:grid;align-content:start;gap:6px;min-height:0}
.nav-button{appearance:none;border:1px solid transparent;cursor:pointer;display:grid;grid-template-columns:32px minmax(0,1fr);gap:10px;align-items:center;min-height:56px;padding:8px;border-radius:var(--radius);color:var(--color-muted);background:transparent;text-align:left;transition:background 180ms var(--ease),color 180ms var(--ease),border-color 180ms var(--ease),box-shadow 180ms var(--ease)}
.nav-key{display:grid;place-items:center;width:32px;height:32px;border-radius:6px;background:var(--color-token-muted-bg);color:var(--color-faint);font-size:12px;font-weight:900}
.nav-copy{display:grid;gap:2px;min-width:0}
.nav-copy strong{font-size:14px;line-height:1.2;color:inherit;overflow-wrap:anywhere}
.nav-copy small{font-size:12px;line-height:1.25;color:var(--color-faint);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.nav-button.active{border-color:var(--color-border-strong);background:var(--color-surface-muted);color:var(--color-primary-strong);box-shadow:var(--shadow-active)}
.nav-button.active .nav-key{background:var(--color-primary);color:var(--color-on-primary)}
.nav-button:hover{border-color:var(--color-border);background:var(--color-hover);color:var(--color-primary-strong)}
.rail-status{display:grid;gap:6px;padding-top:14px;border-top:1px solid var(--color-border-soft);min-width:0}
.rail-status span{font-size:11px;letter-spacing:.12em;text-transform:uppercase;color:var(--color-faint);font-weight:800}
.rail-status strong{font-size:12px;line-height:1.35;color:var(--color-muted);overflow-wrap:anywhere}
.workspace{display:grid;min-width:0;min-height:calc(100dvh - 28px)}
.status-row{grid-column:2;display:flex;justify-content:flex-start;gap:12px;flex-wrap:wrap}
.status{display:inline-flex;align-items:center;min-height:44px;padding:10px 14px;border-radius:var(--radius);line-height:1.5}
.status.error{color:var(--color-danger-text);background:var(--color-danger-bg);border:1px solid var(--color-danger-border)}
@media (max-width:1100px){
  .workbench{grid-template-columns:1fr;width:min(100vw - 20px,1640px)}
  .rail{position:static;height:auto;grid-template-rows:auto auto;gap:12px}
  .nav{grid-template-columns:repeat(5,minmax(0,1fr))}
  .nav-button{grid-template-columns:1fr;justify-items:center;min-height:62px;text-align:center}
  .nav-copy small{display:none}
  .rail-status{display:none}
  .workspace{min-height:auto}
  .status-row{grid-column:1}
}
@media (max-width:720px){
  .workbench{width:min(100vw - 16px,1640px);padding:8px 0}
  .rail{padding:12px}
  .brand-lockup{grid-template-columns:32px minmax(0,1fr)}
  .brand-mark{width:32px;height:32px}
  h1{font-size:18px}
  .nav{grid-template-columns:repeat(3,minmax(0,1fr))}
  .nav-button{min-height:54px}
  .nav-copy strong{font-size:12px}
}
@media (prefers-reduced-motion:reduce){
  .nav-button{transition:none}
}
</style>
