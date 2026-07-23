<script setup lang="ts">
import AgentConsoleView from '../components/GameConsoleView.vue'
import AgentStudioView from '../components/StoryEditorView.vue'
import SettingsPanelView from '../components/SettingsPanelView.vue'
import LearningCenterView from '../components/LearningCenterView.vue'
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

type ViewMode = 'learning' | 'chat' | 'studio' | 'settings'
type StudioMode = 'flow' | 'agents' | 'debug'
type ThemeMode = ApiSettings['theme']

const SETTINGS_STORAGE_KEY = 'kiniu.agent.settings'
const SESSION_STORAGE_KEY = 'kiniu.agent.session'
const STORY_DRAFT_STORAGE_KEY = 'kiniu.agent.flowDraft'
const AGENT_DRAFT_STORAGE_KEY = 'kiniu.agent.agentDraft'
const SESSION_EXPORT_STORAGE_KEY = 'kiniu.agent.sessionExport'
const SANDBOX_PLAN_STORAGE_KEY = 'kiniu.agent.sandboxPlans'
const API_KEY_STORAGE_KEY = 'kiniu.agent.apiKey'
const LOCAL_TOKEN_STORAGE_KEY = 'kiniu.agent.localToken'
const SESSION_PAGE_LIMIT = 50
const SESSION_PAGE_MAX = 200

const defaultSettings: ApiSettings = {
  backendUrl: 'http://localhost:8080',
  localToken: '',
  providerUrl: '',
  apiKey: '',
  model: 'gpt-4.1-mini',
  locale: 'zh-CN',
  theme: 'light'
}

const activeView = ref<ViewMode>('learning')
const activeStudioView = ref<StudioMode>('flow')
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
const preferredAgentId = ref('')
const sessionId = ref('')
const settings = reactive<ApiSettings>({ ...defaultSettings })
const currentLocale = computed(() => normalizeLocale(settings.locale))
const currentTheme = computed(() => normalizeTheme(settings.theme))
const { t } = provideUiI18n(currentLocale)
useHead(() => ({ title: `${t('appBrand')} · ${t('learningNav')}` }))
const storyDraft = ref<StoryCatalogResponse | null>(null)
const agentDraft = ref<AgentCatalogResponse | null>(null)
const sessionExport = ref<SessionExportResponse | null>(null)
const sessionExportOffset = ref(0)
const sessionExportLimit = ref(SESSION_PAGE_LIMIT)
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
  { id: 'learning' as const, label: t('navLearning'), meta: t('navLearningMeta'), key: '1' },
  { id: 'chat' as const, label: t('navChat'), meta: sceneLabel.value, key: '2' },
  { id: 'studio' as const, label: t('navStudio'), meta: `${storyDraft.value?.nodes.length ?? 0} ${t('labelNodes')}`, key: '3' },
  { id: 'settings' as const, label: t('navSettings'), meta: settings.backendUrl || t('fieldNotConfigured'), key: '4' }
])

const studioItems = computed(() => [
  { id: 'flow' as const, label: t('studioTaskFlow'), meta: storyDraft.value?.entryNodeId || t('fieldNotEntered') },
  { id: 'agents' as const, label: t('agentStageTitle'), meta: `${agentDraft.value?.agents.length ?? 0}` },
  { id: 'debug' as const, label: t('sessionDebugTitle'), meta: `${sessionExport.value?.turns.length ?? 0} ${t('labelTurns')}` }
])

const statusItems = computed(() => [
  { label: t('labelSession'), value: sessionId.value || '-' },
  { label: t('labelBackendShort'), value: settings.backendUrl || t('fieldNotConfigured') },
  { label: t('settingsModel'), value: settings.model || t('fieldNoModel') },
  { label: 'AI', value: `${currentOrchestration.value?.aiInvocations.length ?? 0}` }
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
function readStoredJson<T>(key: string, normalize: (value: T) => T): T | null {
  const storedValue = localStorage.getItem(key)
  if (!storedValue) return null

  try {
    return normalize(JSON.parse(storedValue) as T)
  } catch {
    localStorage.removeItem(key)
    errorMessage.value = t('cacheRestoreFailed')
    return null
  }
}

function createLocalSandboxPlan(plan: SandboxPlanDraft): SavedSandboxPlan {
  return normalizeSandboxPlan({
    ...plan,
    id: `sandbox-${Date.now()}`,
    sessionId: sessionId.value,
    createdAt: new Date().toISOString()
  })
}


onMounted(() => {
  isHydrated.value = true

  const savedSettings = readStoredJson<Partial<ApiSettings>>(SETTINGS_STORAGE_KEY, value => value)
  if (savedSettings) {
    Object.assign(settings, defaultSettings, savedSettings, {
      apiKey: '',
      localToken: '',
      locale: normalizeLocale(savedSettings.locale),
      theme: normalizeTheme(savedSettings.theme)
    })
  } else {
    settings.locale = normalizeLocale(settings.locale)
    settings.theme = normalizeTheme(settings.theme)
  }

  settings.apiKey = sessionStorage.getItem(API_KEY_STORAGE_KEY) || ''
  settings.localToken = sessionStorage.getItem(LOCAL_TOKEN_STORAGE_KEY) || ''

  const savedSessionId = localStorage.getItem(SESSION_STORAGE_KEY)
  sessionId.value = savedSessionId || `session-${Date.now()}`
  localStorage.setItem(SESSION_STORAGE_KEY, sessionId.value)

  storyDraft.value = readStoredJson<StoryCatalogResponse>(STORY_DRAFT_STORAGE_KEY, normalizeStoryCatalog)
  if (storyDraft.value) storyStatus.value = t('storyDraftRestored')

  agentDraft.value = readStoredJson<AgentCatalogResponse>(AGENT_DRAFT_STORAGE_KEY, normalizeAgentCatalog)
  if (agentDraft.value) agentStatus.value = t('agentDraftRestored')

  sessionExport.value = readStoredJson<SessionExportResponse>(SESSION_EXPORT_STORAGE_KEY, normalizeSessionExport)
  if (sessionExport.value) sessionStatus.value = t('sessionExportRestored')

  sandboxPlans.value = readStoredJson<SavedSandboxPlan[]>(SANDBOX_PLAN_STORAGE_KEY, normalizeSandboxPlans) ?? []
  if (sessionExport.value) {
    replaceSessionSandboxPlans(sessionExport.value.sessionId, sessionExport.value.sandboxPlans)
  }
})

watch([() => activeView.value, () => activeStudioView.value], async ([view, studioView]) => {
  saveStatus.value = ''
  errorMessage.value = ''
  if (view !== 'studio') return
  if (studioView === 'flow' && !storyDraft.value) {
    await loadStoryCatalog()
  }
  if (studioView === 'agents' && !agentDraft.value) {
    await loadAgentCatalog()
  }
  if (studioView === 'debug' && !sessionExport.value && settings.backendUrl.trim()) {
    await loadSessionExport()
  }
})

function persistedSettingsSnapshot(): ApiSettings {
  return {
    ...settings,
    apiKey: '',
    localToken: ''
  }
}

function persistSessionSecret(key: string, value: string) {
  const trimmed = value.trim()
  if (trimmed) {
    sessionStorage.setItem(key, trimmed)
  } else {
    sessionStorage.removeItem(key)
  }
}

function persistSettings() {
  persistSessionSecret(API_KEY_STORAGE_KEY, settings.apiKey)
  persistSessionSecret(LOCAL_TOKEN_STORAGE_KEY, settings.localToken)
  localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(persistedSettingsSnapshot()))
  saveStatus.value = t('settingsSaved')
}

function resetSettings() {
  Object.assign(settings, defaultSettings)
  sessionStorage.removeItem(API_KEY_STORAGE_KEY)
  sessionStorage.removeItem(LOCAL_TOKEN_STORAGE_KEY)
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

function normalizeSessionPageLimit(limit: number) {
  if (!Number.isFinite(limit) || limit <= 0) return SESSION_PAGE_LIMIT
  return Math.min(Math.max(1, Math.floor(limit)), SESSION_PAGE_MAX)
}

function clampSessionPageOffset(offset: number) {
  const safeOffset = Number.isFinite(offset) ? Math.max(0, Math.floor(offset)) : 0
  const totalTurns = sessionExport.value?.totalTurns ?? 0
  const limit = normalizeSessionPageLimit(sessionExportLimit.value)
  if (totalTurns <= 0) return safeOffset
  return Math.min(safeOffset, Math.max(0, totalTurns - limit))
}

function formatRequestError(error: unknown, messageKey: I18nKey, fallbackKey: I18nKey) {
  return error instanceof Error ? t(messageKey, { message: error.message }) : t(fallbackKey)
}

async function copyJsonPayload(
  payload: unknown,
  successKey: I18nKey,
  blockedKey: I18nKey,
  setStatus: (status: string) => void,
  clearError?: () => void
) {
  try {
    await navigator.clipboard.writeText(JSON.stringify(payload, null, 2))
    setStatus(t(successKey))
  } catch {
    setStatus(t(blockedKey))
  } finally {
    clearError?.()
  }
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
  const turns = (exportData.turns ?? []).map(turn => ({
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
    turns,
    totalTurns: Number.isFinite(exportData.totalTurns) ? exportData.totalTurns : turns.length,
    offset: Number.isFinite(exportData.offset) ? exportData.offset : 0,
    limit: normalizeSessionPageLimit(exportData.limit || turns.length || SESSION_PAGE_LIMIT)
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
    storyError.value = formatRequestError(error, 'loadFailed', 'loadFailedGeneric')
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
    agentError.value = formatRequestError(error, 'loadFailed', 'loadFailedGeneric')
  } finally {
    isLoadingAgents.value = false
  }
}

async function loadSessionExport(targetSessionId = sessionId.value, offset = sessionExportOffset.value, limit = sessionExportLimit.value) {
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

  const safeOffset = Number.isFinite(offset) ? Math.max(0, Math.floor(offset)) : 0
  const safeLimit = normalizeSessionPageLimit(limit)

  try {
    const response = await $fetch<SessionExportResponse>(`/agent/export/${encodeURIComponent(normalizedSessionId)}`, {
      baseURL: settings.backendUrl.trim(),
      headers: buildHeaders(),
      query: { offset: safeOffset, limit: safeLimit }
    })
    sessionExport.value = normalizeSessionExport(JSON.parse(JSON.stringify(response)) as SessionExportResponse)
    sessionExportOffset.value = sessionExport.value.offset
    sessionExportLimit.value = normalizeSessionPageLimit(sessionExport.value.limit || safeLimit)
    replaceSessionSandboxPlans(sessionExport.value.sessionId, sessionExport.value.sandboxPlans)
    localStorage.setItem(SANDBOX_PLAN_STORAGE_KEY, JSON.stringify(sandboxPlans.value))
    persistSessionExport(t('sessionExportLoaded'))
  } catch (error) {
    sessionError.value = formatRequestError(error, 'readFailed', 'readFailedGeneric')
  } finally {
    isLoadingSessionExport.value = false
  }
}

async function exportStoryDraft() {
  if (!storyDraft.value) return
  await copyJsonPayload(storyDraft.value, 'storyJsonCopied', 'clipboardBlockedStory', status => { storyStatus.value = status })
}

async function exportAgentDraft() {
  if (!agentDraft.value) return
  await copyJsonPayload(agentDraft.value, 'agentJsonCopied', 'clipboardBlockedAgent', status => { agentStatus.value = status })
}

async function exportSessionJson() {
  if (!sessionExport.value) return
  await copyJsonPayload(sessionExport.value, 'sessionJsonCopied', 'clipboardBlockedSession', status => { sessionStatus.value = status })
}

async function exportSandboxPlans() {
  await copyJsonPayload(
    sandboxPlans.value,
    'sandboxJsonCopied',
    'clipboardBlockedSandbox',
    status => { sessionStatus.value = status },
    () => { sessionError.value = '' }
  )
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
    storyError.value = formatRequestError(error, 'saveFailed', 'saveFailedGeneric')
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
    validationError.value = formatRequestError(error, 'validationFailed', 'validationFailedGeneric')
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
    generatorError.value = formatRequestError(error, 'generationFailed', 'generationFailedGeneric')
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
    agentError.value = formatRequestError(error, 'saveFailed', 'saveFailedGeneric')
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
    const savedPlan = createLocalSandboxPlan(plan)
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
    sessionExportOffset.value = sessionExport.value.offset
    sessionExportLimit.value = normalizeSessionPageLimit(sessionExport.value.limit || SESSION_PAGE_LIMIT)
    replaceSessionSandboxPlans(sessionExport.value.sessionId, sessionExport.value.sandboxPlans)
    localStorage.setItem(SANDBOX_PLAN_STORAGE_KEY, JSON.stringify(sandboxPlans.value))
    persistSessionExport(t('sandboxSavedBackend', { workspace: plan.sceneId || t('fieldWorkspace') }))
  } catch (error) {
    const fallbackPlan = createLocalSandboxPlan(plan)
    sandboxPlans.value = [fallbackPlan, ...sandboxPlans.value]
    persistSandboxPlans(t('sandboxSavedLocal', { workspace: fallbackPlan.sceneId || t('fieldWorkspace') }))
    sessionError.value = formatRequestError(error, 'sandboxSyncFailed', 'sandboxSyncFailedGeneric')
  }
}

function resetSandboxPlans() {
  localStorage.removeItem(SANDBOX_PLAN_STORAGE_KEY)
  sandboxPlans.value = []
  sessionStatus.value = t('sandboxPlansCleared')
  sessionError.value = ''
}

function buildHeaders(options: { includeProvider?: boolean } = {}) {
  assertSafeSecretTarget()
  const headers: Record<string, string> = {}
  if (settings.localToken.trim()) {
    headers['X-Local-Token'] = settings.localToken.trim()
  }
  if (options.includeProvider) {
    if (settings.apiKey.trim()) {
      headers.Authorization = `Bearer ${settings.apiKey.trim()}`
      headers['X-API-Key'] = settings.apiKey.trim()
    }
    if (settings.providerUrl.trim()) {
      headers['X-Provider-Url'] = settings.providerUrl.trim()
    }
    if (settings.model.trim()) {
      headers['X-Model'] = settings.model.trim()
    }
  }
  return headers
}

function assertSafeSecretTarget() {
  const hasSecrets = !!settings.localToken.trim() || !!settings.apiKey.trim()
  if (hasSecrets && !isLoopbackBackendUrl(settings.backendUrl.trim())) {
    throw new Error(t('backendMustBeLocal'))
  }
}

function isLoopbackBackendUrl(value: string) {
  try {
    const url = new URL(value)
    const hostname = url.hostname.toLowerCase()
    return hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '::1' || hostname === '[::1]'
  } catch {
    return false
  }
}

function loadSessionExportPage(offset: number) {
  return loadSessionExport(
    sessionExport.value?.sessionId || sessionId.value,
    clampSessionPageOffset(offset),
    normalizeSessionPageLimit(sessionExportLimit.value)
  )
}

function useLearningAgent(agentId: string) {
  preferredAgentId.value = agentId
  activeView.value = 'chat'
  playerInput.value = t('learningAgentStarter', { agentId })
  errorMessage.value = ''
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
      body: { sessionId: sessionId.value, input: trimmedInput, choice: trimmedChoice, preferredAgentId: preferredAgentId.value },
      headers: buildHeaders({ includeProvider: true })
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
    errorMessage.value = formatRequestError(error, 'requestFailed', 'requestFailedGeneric')
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
        <div class="top-strip">
          <div
            v-for="item in statusItems"
            :key="item.label"
            class="status-chip"
          >
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>

        <LearningCenterView
          :provider-url='settings.providerUrl'
          :api-key='settings.apiKey'
          :model='settings.model'
          v-if="activeView === 'learning'"
          :backend-url="settings.backendUrl"
          :local-token="settings.localToken"
          @use-agent="useLearningAgent"
        />

        <AgentConsoleView
          v-else-if="activeView === 'chat'"
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

        <section v-else-if="activeView === 'studio'" class="studio-frame">
          <div class="studio-switcher" role="tablist" aria-label="Studio">
            <button
              v-for="item in studioItems"
              :key="item.id"
              class="studio-tab"
              :class="{ active: activeStudioView === item.id }"
              type="button"
              role="tab"
              :aria-selected="activeStudioView === item.id"
              @click="activeStudioView = item.id"
            >
              <strong>{{ item.label }}</strong>
              <span>{{ item.meta }}</span>
            </button>
          </div>

          <AgentStudioView
            :mode="activeStudioView"
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
            :page-offset="sessionExportOffset"
            :page-limit="sessionExportLimit"
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
            @load-session-page="loadSessionExportPage"
            @export-session="exportSessionJson"
            @reset-session="resetSessionExport"
            @export-sandbox-plans="exportSandboxPlans"
            @reset-sandbox-plans="resetSandboxPlans"
          />
        </section>

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
  --color-primary:#2f5bd3;
  --color-primary-strong:#2346a8;
  --color-secondary:#4d5d73;
  --color-accent:#e76f51;
  --color-bg:#f3f6fb;
  --color-bg-soft:#f8faff;
  --color-surface:#ffffff;
  --color-surface-panel:rgba(255,255,255,.94);
  --color-surface-panel-strong:rgba(255,255,255,.96);
  --color-surface-muted:#edf2fb;
  --color-input:#ffffff;
  --color-hover:#edf3ff;
  --color-row:#f7f9fd;
  --color-on-primary:#ffffff;
  --color-on-accent:#ffffff;
  --color-heading:#13233a;
  --color-heading-soft:#2a3d58;
  --color-border:#d8e0ec;
  --color-border-soft:#e8edf5;
  --color-border-strong:#91a9dc;
  --color-text:#24344d;
  --color-muted:#60718b;
  --color-faint:#8a98ab;
  --color-danger:#dc2626;
  --color-success:#15803d;
  --color-token-bg:#e2eaff;
  --color-token-text:#2346a8;
  --color-token-muted-bg:#eef2f8;
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
  --color-focus-ring:rgba(47,91,211,.16);
  --color-focus-global:rgba(231,111,81,.34);
  --color-hero-surface:#f7f9fd;
  --color-graph-bg:#f7f9fd;
  --color-graph-edge:rgba(47,91,211,.34);
  --color-graph-arrow:#aab8d1;
  --color-graph-node:#ffffff;
  --color-graph-node-border:#d1d9d7;
  --color-graph-node-selected:#e2eaff;
  --color-graph-entry:#1d9a73;
  --page-background:#f3f6fb;
  --shadow-soft:0 12px 28px rgba(25,48,84,.06);
  --shadow-card:0 1px 2px rgba(25,48,84,.05),0 8px 18px rgba(25,48,84,.06);
  --shadow-active:0 6px 14px rgba(47,91,211,.12);
  --shadow-primary:0 8px 18px rgba(47,91,211,.16);
  --shadow-accent:0 8px 18px rgba(231,111,81,.16);
  --radius:6px;
  --ease:cubic-bezier(.2,.8,.2,1);
}
:global(:root[data-theme='dark']){
  --color-primary:#79a2ff;
  --color-primary-strong:#a8c0ff;
  --color-secondary:#a6b4cb;
  --color-accent:#ff997f;
  --color-bg:#101a2a;
  --color-bg-soft:#162238;
  --color-surface:#17243a;
  --color-surface-panel:rgba(23,36,58,.96);
  --color-surface-panel-strong:rgba(20,32,52,.98);
  --color-surface-muted:#1d3153;
  --color-input:#0d1727;
  --color-hover:#213b63;
  --color-row:#142138;
  --color-on-primary:#08152b;
  --color-on-accent:#2b1210;
  --color-heading:#f4f7ff;
  --color-heading-soft:#d8e1f3;
  --color-border:#324765;
  --color-border-soft:#263a56;
  --color-border-strong:#79a2ff;
  --color-text:#e5ebf7;
  --color-muted:#a8b7ce;
  --color-faint:#8092af;
  --color-danger:#f87171;
  --color-success:#4ade80;
  --color-token-bg:#203766;
  --color-token-text:#b7caff;
  --color-token-muted-bg:#1a2a45;
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
  --color-focus-ring:rgba(121,162,255,.2);
  --color-focus-global:rgba(255,153,127,.4);
  --color-hero-surface:#142138;
  --color-graph-bg:#0d1727;
  --color-graph-edge:rgba(121,162,255,.46);
  --color-graph-arrow:#a8c0ff;
  --color-graph-node:#14243d;
  --color-graph-node-border:#38557d;
  --color-graph-node-selected:#203766;
  --color-graph-entry:#6cdaa7;
  --page-background:#101a2a;
  --shadow-soft:0 12px 30px rgba(0,0,0,.24);
  --shadow-card:0 1px 2px rgba(0,0,0,.32),0 10px 24px rgba(0,0,0,.18);
  --shadow-active:0 6px 14px rgba(0,0,0,.24);
  --shadow-primary:0 8px 18px rgba(121,162,255,.16);
  --shadow-accent:0 8px 18px rgba(255,153,127,.14);
}
:global(body){
  margin:0;
  font-family:Inter,"Aptos","Noto Sans SC","PingFang SC","Microsoft YaHei",sans-serif;
  background:var(--page-background);
  color:var(--color-text);
  -webkit-font-smoothing:antialiased;
  text-rendering:optimizeLegibility;
}
:global(*){box-sizing:border-box}
:global(button),:global(input),:global(textarea){font:inherit}
:global(:focus-visible){outline:3px solid var(--color-focus-global);outline-offset:2px}
.shell{min-height:100dvh;background:var(--page-background);color:var(--color-text)}
.workbench{display:grid;grid-template-columns:220px minmax(0,1fr);gap:12px;width:min(1660px,calc(100vw - 24px));min-height:calc(100dvh - 24px);margin:0 auto;padding:12px 0}
.rail{position:sticky;top:12px;display:grid;grid-template-rows:auto 1fr auto;gap:16px;height:calc(100dvh - 24px);padding:14px;border:1px solid var(--color-border);border-radius:var(--radius);background:var(--color-surface-panel);box-shadow:var(--shadow-card)}
.brand-lockup{display:grid;grid-template-columns:38px minmax(0,1fr);gap:12px;align-items:center;min-width:0}
.brand-mark{display:grid;place-items:center;width:38px;height:38px;border-radius:var(--radius);background:var(--color-heading);color:var(--color-bg);font-size:16px;font-weight:900}
.eyebrow{margin:0 0 4px;font-size:11px;letter-spacing:0;color:var(--color-primary-strong);font-weight:800;line-height:1.2}
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
.rail-status span{font-size:11px;letter-spacing:0;color:var(--color-faint);font-weight:800}
.rail-status strong{font-size:12px;line-height:1.35;color:var(--color-muted);overflow-wrap:anywhere}
.workspace{display:grid;grid-template-rows:auto minmax(0,1fr);gap:10px;min-width:0;min-height:calc(100dvh - 24px)}
.top-strip{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px;min-width:0}
.status-chip{display:grid;gap:3px;min-width:0;min-height:48px;padding:8px 10px;border:1px solid var(--color-border-soft);border-radius:var(--radius);background:var(--color-surface-panel);color:var(--color-muted)}
.status-chip span{font-size:11px;line-height:1.2;color:var(--color-faint);font-weight:700;letter-spacing:0}
.status-chip strong{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px;line-height:1.2;color:var(--color-text)}
.studio-frame{display:grid;grid-template-rows:auto minmax(0,1fr);gap:10px;min-width:0;min-height:0}
.studio-switcher{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;min-width:0}
.studio-tab{appearance:none;border:1px solid var(--color-border-soft);cursor:pointer;display:grid;gap:3px;min-height:52px;padding:9px 12px;border-radius:var(--radius);background:var(--color-surface-panel);color:var(--color-muted);text-align:left;transition:background 180ms var(--ease),border-color 180ms var(--ease),box-shadow 180ms var(--ease),color 180ms var(--ease)}
.studio-tab strong{min-width:0;font-size:14px;line-height:1.25;color:inherit;overflow-wrap:anywhere}
.studio-tab span{min-width:0;font-size:12px;line-height:1.25;color:var(--color-faint);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.studio-tab.active{border-color:var(--color-border-strong);background:var(--color-surface-muted);color:var(--color-primary-strong);box-shadow:var(--shadow-active)}
.studio-tab:hover{border-color:var(--color-border-strong);background:var(--color-hover);color:var(--color-primary-strong)}
.status-row{grid-column:2;display:flex;justify-content:flex-start;gap:12px;flex-wrap:wrap}
.status{display:inline-flex;align-items:center;min-height:44px;padding:10px 14px;border-radius:var(--radius);line-height:1.5}
.status.error{color:var(--color-danger-text);background:var(--color-danger-bg);border:1px solid var(--color-danger-border)}
@media (max-width:1100px){
  .workbench{grid-template-columns:1fr;width:min(100vw - 20px,1640px)}
  .rail{position:static;height:auto;grid-template-rows:auto auto;gap:12px}
  .nav{grid-template-columns:repeat(4,minmax(0,1fr))}
  .nav-button{grid-template-columns:1fr;justify-items:center;min-height:62px;text-align:center}
  .nav-copy small{display:none}
  .rail-status{display:none}
  .workspace{min-height:auto}
  .top-strip{grid-template-columns:repeat(2,minmax(0,1fr))}
  .status-row{grid-column:1}
}
@media (max-width:720px){
  .workbench{width:min(100vw - 16px,1640px);padding:8px 0}
  .rail{padding:12px}
  .brand-lockup{grid-template-columns:32px minmax(0,1fr)}
  .brand-mark{width:32px;height:32px}
  h1{font-size:18px}
  .nav{grid-template-columns:repeat(4,minmax(0,1fr))}
  .nav-button{min-height:54px}
  .nav-copy strong{font-size:12px}
  .top-strip{grid-template-columns:repeat(2,minmax(0,1fr))}
  .studio-switcher{grid-template-columns:1fr}
  .status-chip{min-height:42px}
}
@media (prefers-reduced-motion:reduce){
  .nav-button,.studio-tab{transition:none}
}

/* The lab shell keeps navigation visible like a course platform while leaving the work surface spacious. */
.workbench{grid-template-columns:1fr;gap:0;width:min(1480px,calc(100vw - 32px));min-height:100dvh;padding:0 0 28px}
.rail{position:sticky;top:0;z-index:20;grid-template-columns:auto minmax(0,1fr) auto;gap:28px;align-items:center;height:auto;padding:14px 0;border:0;border-bottom:1px solid var(--color-border);border-radius:0;background:color-mix(in srgb,var(--color-bg) 92%,transparent);box-shadow:none;backdrop-filter:blur(14px)}
.brand-lockup{grid-template-columns:36px minmax(0,1fr);gap:10px}
.brand-mark{width:36px;height:36px;border-radius:5px;background:var(--color-primary);color:var(--color-on-primary);font-size:15px;box-shadow:var(--shadow-primary)}
.eyebrow{font-size:10px;letter-spacing:0;text-transform:uppercase}
h1{font-size:17px;letter-spacing:0;font-weight:800}
.nav{display:flex;align-items:center;gap:4px;min-width:0;overflow:auto}
.nav-button{display:flex;flex:0 0 auto;grid-template-columns:none;gap:8px;align-items:center;min-height:40px;padding:7px 11px;border-radius:5px;transition:background 160ms var(--ease),color 160ms var(--ease),border-color 160ms var(--ease)}
.nav-key{width:22px;height:22px;border:1px solid var(--color-border-soft);border-radius:4px;background:transparent;color:var(--color-faint);font-size:10px}
.nav-copy{display:flex;align-items:center;gap:7px}
.nav-copy strong{font-size:13px;white-space:nowrap}
.nav-copy small{max-width:140px;font-size:11px}
.nav-button.active{border-color:var(--color-border-strong);background:var(--color-token-bg);color:var(--color-primary-strong);box-shadow:none}
.nav-button.active .nav-key{border-color:var(--color-primary);background:var(--color-primary);color:var(--color-on-primary)}
.rail-status{display:flex;align-items:center;gap:8px;padding:0;border:0}
.rail-status span{font-size:10px;letter-spacing:0;text-transform:uppercase}
.rail-status strong{max-width:150px;font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.workspace{grid-template-rows:auto minmax(0,1fr);gap:18px;min-height:calc(100dvh - 94px);padding-top:18px}
.top-strip{grid-template-columns:repeat(4,minmax(0,1fr));gap:1px;border:1px solid var(--color-border);background:var(--color-border);border-radius:6px;overflow:hidden}
.status-chip{min-height:54px;padding:10px 13px;border:0;border-radius:0;background:var(--color-surface-panel)}
.status-chip span{font-size:10px;letter-spacing:0;text-transform:uppercase}
.status-chip strong{font-size:12px}
.status-row{grid-column:1}
@media (max-width:1100px){
  .workbench{width:min(calc(100vw - 24px),1480px)}
  .rail{position:static;grid-template-columns:1fr;gap:12px;padding:12px 0}
  .nav{width:100%;padding-bottom:2px}
  .nav-button{display:flex;min-height:40px;text-align:left}
  .nav-copy small{display:block}
  .rail-status{display:flex}
  .workspace{min-height:auto;padding-top:14px}
}
@media (max-width:720px){
  .workbench{width:calc(100vw - 20px);padding-bottom:18px}
  .brand-lockup{grid-template-columns:32px minmax(0,1fr)}
  .brand-mark{width:32px;height:32px}
  h1{font-size:16px}
  .nav-button{padding:7px 9px}
  .nav-copy small{display:none}
  .top-strip{grid-template-columns:repeat(2,minmax(0,1fr))}
}
</style>
