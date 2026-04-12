export type ApiSettings = {
  backendUrl: string
  providerUrl: string
  apiKey: string
  model: string
}

export type Agent = {
  id: string
  name: string
  role: string
  summary: string
  personality: string
  systemPrompt: string
  activeScenes: string[]
  personalityParameters: Record<string, string>
  coreGoals: string[]
  hiddenMotives: string[]
  initiative: number
  memoryStyle: string
}

export type AgentCatalogResponse = {
  agents: Agent[]
}

export type WorldState = {
  currentScene: string
  currentNodeId?: string
  storySeedNodeId?: string
  flags: string[]
  affinityScores: Record<string, number>
  relationships?: Record<string, { trust: number; affection: number; curiosity: number }>
}

export type StoryEvent = {
  id: string
  sourceNodeId: string
  targetNodeId: string
  targetScene: string
  speakerId: string
  title: string
  narrative: string
  choices: string[]
  sourceType: string
  directorSummary: string
  spotlightAgentIds: string[]
}

export type AgentReplyView = {
  agentId: string
  agentName: string
  role: string
  objective: string
  memorySummary: string
  initiativeScore: number
  message: string
}

export type BranchOptionView = {
  label: string
  intent: string
  risk: string
  targetMood: string
  targetAgentId: string
  consequenceSummary: string
  relationshipDelta: number
  addedFlags: string[]
  removedFlags: string[]
  source: string
}

export type SandboxPlanDraft = {
  sceneId: string
  nodeId: string
  title: string
  summary: string
  steps: BranchOptionView[]
  totalRelationshipDelta: number
  finalFlags: string[]
  finalAffinityScores: Record<string, number>
}

export type SavedSandboxPlan = SandboxPlanDraft & {
  id: string
  sessionId: string
  createdAt: string
}

export type ScoreFactorView = {
  code: string
  delta: number
  reason: string
}

export type AIInvocationView = {
  operation: string
  targetId: string
  providerAttempted: boolean
  providerSucceeded: boolean
  fallbackUsed: boolean
  providerUrl: string
  model: string
  latencyMs: number
  errorMessage: string
}

export type OrchestrationAgentPlanView = {
  agentId: string
  agentName: string
  role: string
  shouldSpeak: boolean
  initiativeScore: number
  objective: string
  memorySummary: string
  scoreFactors: ScoreFactorView[]
}

export type OrchestrationPlannerView = {
  sceneGoal: string
  tensionLabel: string
  pacingLabel: string
  directorIntent: string
  risks: string[]
}

export type OrchestrationCriticView = {
  verdict: string
  focusScore: number
  castCoverageScore: number
  choicePressureScore: number
  notes: string[]
}

export type OrchestrationTraceView = {
  storyEventId: string
  storyTitle: string
  storySourceType: string
  sceneId: string
  focusAgentId: string
  planner: OrchestrationPlannerView
  critic: OrchestrationCriticView
  aiInvocations: AIInvocationView[]
  spotlightAgentIds: string[]
  activeAgentIds: string[]
  speakingAgentIds: string[]
  nextChoices: string[]
  nextBranchOptions: BranchOptionView[]
  plans: OrchestrationAgentPlanView[]
}

export type SessionTurnView = {
  id: string
  parentTurnId: string | null
  timestamp: string
  playerInput: string
  playerChoice: string
  sceneId: string
  nodeId: string
  storyEventId: string | null
  storyEvent?: StoryEvent
  directorMessage?: string
  summary: string
  presentedChoices: string[]
  presentedBranchOptions: BranchOptionView[]
  agentReplies: AgentReplyView[]
  stateSnapshot: WorldState
  orchestration?: OrchestrationTraceView | null
}

export type SessionExportResponse = {
  sessionId: string
  updatedAt: string
  currentState: WorldState
  agents: Agent[]
  turns: SessionTurnView[]
  sandboxPlans: SavedSandboxPlan[]
}

export type GameResponse = {
  message: string
  choices: string[]
  branchOptions: BranchOptionView[]
  state: WorldState
  sessionId?: string
  agentReplies?: AgentReplyView[]
  directorMessage?: string
  storyEvent?: StoryEvent
  orchestration?: OrchestrationTraceView | null
}

export type ChatMessage = {
  id: string
  role: 'system' | 'assistant' | 'player'
  speaker: string
  content: string
}

export type StoryChoiceView = {
  id: string
  label: string
  description: string
  targetNodeId: string
  requiredFlags: string[]
  blockedFlags: string[]
  minimumAffinity: Record<string, number>
  keywords: string[]
  flagsToAdd: string[]
  affinityChanges: Record<string, number>
}

export type StoryNodeView = {
  id: string
  sceneId: string
  title: string
  speakerId: string
  narrative: string
  tags: string[]
  enterFlags: string[]
  enterAffinityChanges: Record<string, number>
  choices: StoryChoiceView[]
}

export type StoryCatalogResponse = {
  entryNodeId: string
  nodes: StoryNodeView[]
}

export type StoryIssueView = {
  severity: string
  code: string
  message: string
  nodeId?: string | null
  choiceId?: string | null
}

export type StoryAnalysisResponse = {
  entryNodeId: string
  totalNodes: number
  totalChoices: number
  reachableNodeIds: string[]
  unreachableNodeIds: string[]
  endingNodeIds: string[]
  sceneIds: string[]
  errorCount: number
  warningCount: number
  issues: StoryIssueView[]
}

export type StoryAnalysisRequest = {
  story: StoryCatalogResponse
  agents?: AgentCatalogResponse | null
}

export type StoryGenerationRequest = {
  premise: string
  setting: string
  tone: string
  chapterGoal: string
  protagonistName: string
  companionName: string
  rivalName: string
}

export type StoryGenerationResponse = {
  story: StoryCatalogResponse
  agents: AgentCatalogResponse
  analysis: StoryAnalysisResponse
  summary: string
}
