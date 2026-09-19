<script setup lang="ts">
import AgentConsoleView from '../components/GameConsoleView.vue'
import AgentStudioView from '../components/StoryEditorView.vue'
import SettingsPanelView from '../components/SettingsPanelView.vue'
import LearningCenterView from '../components/LearningCenterView.vue'
import UiTabs from '../components/ui/UiTabs.vue'
import { normalizeLocale, provideUiI18n, type I18nKey } from '../i18n'
import { resolveStartupSettings } from '../utils/startupSettings'
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

const runtimeConfig = useRuntimeConfig()
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
  { id: 'learning' as const, label: t('navLearning'), key: '1' },
  { id: 'chat' as const, label: t('navChat'), key: '2' },
  { id: 'studio' as const, label: t('navStudio'), key: '3' },
  { id: 'settings' as const, label: t('navSettings'), key: '4' }
])

const studioItems = computed(() => [
  { id: 'flow' as const, label: t('studioTaskFlow'), meta: storyDraft.value?.entryNodeId || t('fieldNotEntered') },
  { id: 'agents' as const, label: t('agentStageTitle'), meta: `${agentDraft.value?.agents.length ?? 0}` },
  { id: 'debug' as const, label: t('sessionDebugTitle'), meta: `${sessionExport.value?.turns.length ?? 0} ${t('labelTurns')}` }
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
  const startupSettings = resolveStartupSettings({
    runtimeBackendUrl: runtimeConfig.public.kiniuBackendUrl,
    runtimeLocalToken: runtimeConfig.public.kiniuLocalToken,
    storedBackendUrl: settings.backendUrl,
    sessionLocalToken: sessionStorage.getItem(LOCAL_TOKEN_STORAGE_KEY) || ''
  })
  settings.backendUrl = startupSettings.backendUrl
  settings.localToken = startupSettings.localToken
  if (startupSettings.bootstrappedToken) {
    persistSessionSecret(LOCAL_TOKEN_STORAGE_KEY, startupSettings.localToken)
  }

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
  isHydrated.value = true
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
          <h1>{{ t('appTitle') }}</h1>
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
            </span>
          </button>
        </nav>
      </aside>

      <main class="workspace">
        <LearningCenterView
          :provider-url='settings.providerUrl'
          :api-key='settings.apiKey'
          :model='settings.model'
          v-if="activeView === 'learning'"
          :backend-url="settings.backendUrl"
          :local-token="settings.localToken"
          @use-agent="useLearningAgent"
          @open-settings="activeView = 'settings'"
        />

        <AgentConsoleView
          v-else-if="activeView === 'chat'"
          v-model:player-input="playerInput"
          :world-state="worldState"
          :scene-label="sceneLabel"
          :current-branch-options="currentBranchOptions"
          :orchestration="currentOrchestration"
          :messages="messages"
          :is-sending="isSending"
          @save-sandbox="saveSandboxPlan"
          @send-turn="sendTurn"
        />

        <section v-else-if="activeView === 'studio'" class="studio-frame">
          <UiTabs v-model="activeStudioView" :items="studioItems" variant="card" aria-label="Studio" />

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
.shell {
  min-height: 100dvh;
  background: var(--page-background);
  color: var(--color-text);
}
.workbench {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
  width: min(1480px, calc(100vw - 32px));
  min-height: 100dvh;
  margin: 0 auto;
  padding: 0 0 var(--space-8);
}
.rail {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-8);
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-border);
  background: color-mix(in srgb, var(--color-bg) 92%, transparent);
  backdrop-filter: blur(14px);
}
.brand-lockup {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.brand-mark {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: var(--text-md);
  font-weight: 900;
  box-shadow: var(--shadow-primary);
}
.eyebrow {
  margin: 0 0 var(--space-1);
  color: var(--color-primary-strong);
  font-size: var(--text-xs);
  font-weight: var(--font-bold);
  letter-spacing: 0.04em;
  line-height: 1.2;
  text-transform: uppercase;
}
h1 {
  margin: 0;
  color: var(--color-heading);
  font-size: 17px;
  font-weight: var(--font-bold);
  line-height: 1.1;
  overflow-wrap: anywhere;
}
p {
  margin: 0;
}
.nav {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  min-width: 0;
  overflow: auto;
}
.nav-button {
  appearance: none;
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
  min-height: 40px;
  padding: 7px 11px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-muted);
  text-align: left;
  cursor: pointer;
  transition: background var(--duration-base) var(--ease), color var(--duration-base) var(--ease), border-color var(--duration-base) var(--ease);
}
.nav-key {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-faint);
  font-size: 10px;
  font-weight: 900;
}
.nav-copy {
  min-width: 0;
}
.nav-copy strong {
  color: inherit;
  font-size: var(--text-sm);
  line-height: 1.2;
  white-space: nowrap;
}
.nav-copy small {
  display: none;
}
.nav-button:hover {
  border-color: var(--color-border);
  background: var(--color-hover);
  color: var(--color-primary-strong);
}
.nav-button.active {
  border-color: var(--color-border-strong);
  background: var(--color-token-bg);
  color: var(--color-primary-strong);
}
.nav-button.active .nav-key {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--color-on-primary);
}
.workspace {
  display: grid;
  grid-template-rows: minmax(0, 1fr);
  gap: var(--space-5);
  min-width: 0;
  min-height: calc(100dvh - 94px);
  padding-top: var(--space-5);
}
.studio-frame {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 10px;
  min-width: 0;
  min-height: 0;
}
.status-row {
  display: flex;
  justify-content: flex-start;
  gap: var(--space-3);
  flex-wrap: wrap;
}
.status {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 10px 14px;
  border-radius: var(--radius);
  line-height: 1.5;
}
.status.error {
  border: 1px solid var(--color-danger-border);
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
}
@media (max-width: 1100px) {
  .workbench {
    width: min(calc(100vw - 24px), 1480px);
  }
  .rail {
    position: static;
    grid-template-columns: 1fr;
    gap: var(--space-3);
    padding: var(--space-3) 0;
  }
  .nav {
    width: 100%;
    padding-bottom: 2px;
  }
  .workspace {
    min-height: auto;
    padding-top: 14px;
  }
}
@media (max-width: 720px) {
  .workbench {
    width: calc(100vw - 20px);
    padding-bottom: var(--space-5);
  }
  .brand-lockup {
    grid-template-columns: 32px minmax(0, 1fr);
  }
  .brand-mark {
    width: 32px;
    height: 32px;
  }
  h1 {
    font-size: 16px;
  }
  .nav-button {
    padding: 7px 9px;
  }
  .nav-copy small {
    display: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .nav-button {
    transition: none;
  }
}
</style>
