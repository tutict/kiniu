<script setup lang="ts">
import { useUiI18n } from '../i18n'
import type { SavedSandboxPlan, SessionExportResponse, SessionTurnView } from '../types/game'

type PositionedTurn = {
  id: string
  turn: SessionTurnView
  x: number
  y: number
}

type BranchEdge = {
  id: string
  path: string
}

const props = defineProps<{
  exportData: SessionExportResponse | null
  sandboxPlans: SavedSandboxPlan[]
  canImportSandbox: boolean
  backendUrl: string
  currentSessionId: string
  isLoadingSession: boolean
  sessionStatus: string
  sessionError: string
}>()

const emit = defineEmits<{
  loadSession: [sessionId: string]
  exportSession: []
  resetSession: []
  exportSandboxPlans: []
  resetSandboxPlans: []
  promoteSandbox: [planId: string]
}>()

const { t } = useUiI18n()
const requestedSessionId = ref('')
const selectedTurnId = ref('')
const selectedSandboxPlanId = ref('')

watch(
  () => props.currentSessionId,
  (sessionId) => {
    if (!requestedSessionId.value) requestedSessionId.value = sessionId
  },
  { immediate: true }
)

watch(
  () => props.exportData,
  (exportData) => {
    if (exportData?.turns.length && !selectedTurnId.value) {
      selectedTurnId.value = exportData.turns[exportData.turns.length - 1].id
    }
  },
  { immediate: true }
)

const visibleSandboxPlans = computed(() => {
  const currentSessionPlans = props.sandboxPlans.filter(plan => plan.sessionId === props.currentSessionId)
  return currentSessionPlans.length ? currentSessionPlans : props.sandboxPlans
})

watch(
  () => visibleSandboxPlans.value,
  (plans) => {
    if (!plans.length) {
      selectedSandboxPlanId.value = ''
      return
    }
    if (!plans.some(plan => plan.id === selectedSandboxPlanId.value)) {
      selectedSandboxPlanId.value = plans[0].id
    }
  },
  { immediate: true }
)

const selectedTurn = computed(() => {
  const turns = props.exportData?.turns ?? []
  return turns.find(turn => turn.id === selectedTurnId.value) ?? turns[turns.length - 1] ?? null
})

const selectedSandboxPlan = computed(() => {
  return visibleSandboxPlans.value.find(plan => plan.id === selectedSandboxPlanId.value) ?? visibleSandboxPlans.value[0] ?? null
})

const sessionMetrics = computed(() => {
  const turns = props.exportData?.turns ?? []
  const generatedTurns = turns.filter(turn => turn.storyEvent?.sourceType === 'generated').length
  const seedTurns = turns.filter(turn => turn.storyEvent?.sourceType === 'seed').length
  const uniqueScenes = new Set(turns.map(turn => turn.sceneId).filter(Boolean)).size
  return {
    totalTurns: turns.length,
    generatedTurns,
    seedTurns,
    uniqueScenes
  }
})

const sandboxMetrics = computed(() => {
  const totalPlans = props.sandboxPlans.length
  const sessionPlans = props.sandboxPlans.filter(plan => plan.sessionId === props.currentSessionId).length
  const totalSteps = visibleSandboxPlans.value.reduce((sum, plan) => sum + plan.steps.length, 0)
  return {
    totalPlans,
    sessionPlans,
    totalSteps
  }
})

const branchGraph = computed(() => {
  const turns = props.exportData?.turns ?? []
  const turnMap = new Map(turns.map(turn => [turn.id, turn]))
  const childrenMap = new Map<string | null, SessionTurnView[]>()

  turns.forEach((turn) => {
    const parentKey = turn.parentTurnId ?? null
    const siblings = childrenMap.get(parentKey) ?? []
    siblings.push(turn)
    childrenMap.set(parentKey, siblings)
  })

  childrenMap.forEach((siblings) => {
    siblings.sort((left, right) => left.timestamp.localeCompare(right.timestamp))
  })

  const positioned = new Map<string, PositionedTurn>()
  let rowIndex = 0
  const cardWidth = 210
  const rowGap = 30
  const columnGap = 96
  const startX = 28
  const startY = 32

  const walk = (turn: SessionTurnView, depth: number) => {
    if (positioned.has(turn.id)) return
    const y = startY + rowIndex * rowGap
    rowIndex += 1
    positioned.set(turn.id, {
      id: turn.id,
      turn,
      x: startX + depth * columnGap,
      y
    })

    const children = childrenMap.get(turn.id) ?? []
    children.forEach(child => walk(child, depth + 1))
  }

  ;(childrenMap.get(null) ?? []).forEach(rootTurn => walk(rootTurn, 0))

  const edges: BranchEdge[] = turns
    .filter(turn => turn.parentTurnId && turnMap.has(turn.parentTurnId))
    .map((turn) => {
      const source = positioned.get(turn.parentTurnId!)
      const target = positioned.get(turn.id)
      if (!source || !target) {
        return null
      }
      const startXLine = source.x + cardWidth
      const startYLine = source.y + 18
      const endXLine = target.x
      const endYLine = target.y + 18
      const control = Math.max(36, (endXLine - startXLine) * 0.4)
      return {
        id: `${turn.parentTurnId}-${turn.id}`,
        path: `M ${startXLine} ${startYLine} C ${startXLine + control} ${startYLine}, ${endXLine - control} ${endYLine}, ${endXLine} ${endYLine}`
      }
    })
    .filter(Boolean) as BranchEdge[]

  const nodes = Array.from(positioned.values())
  return {
    nodes,
    edges,
    width: nodes.length ? Math.max(...nodes.map(node => node.x)) + cardWidth + 42 : 720,
    height: nodes.length ? Math.max(...nodes.map(node => node.y)) + 78 : 260
  }
})

function shortText(value: string | undefined | null, length = 30) {
  const text = value?.trim() ?? ''
  if (!text) return '...'
  return text.length <= length ? text : `${text.slice(0, length - 1)}…`
}

function formatTime(value: string) {
  if (!value) return '-'
  return new Date(value).toLocaleString()
}

function formatSignedValue(value: number) {
  return `${value >= 0 ? '+' : ''}${value}`
}

function triggerLoad() {
  emit('loadSession', requestedSessionId.value.trim() || props.currentSessionId)
}

function displayCode(value: string) {
  switch (value) {
    case 'pivot':
      return t('codePivot')
    case 'medium':
      return t('codeMedium')
    case 'low':
      return t('codeLow')
    case 'high':
      return t('codeHigh')
    case 'volatile':
      return t('codeVolatile')
    case 'stable':
      return t('codeStable')
    case 'calm':
      return t('codeCalm')
    default:
      return value
  }
}
</script>

<template>
  <section class="debug-stage">
    <div class="debug-head">
      <div>
        <p class="eyebrow">{{ t('sessionDebugEyebrow') }}</p>
        <h3>{{ t('sessionDebugTitle') }}</h3>
      </div>

      <div class="debug-toolbar">
        <input v-model="requestedSessionId" class="search-input" type="text" placeholder="session-123456">
        <button class="secondary-button" type="button" :disabled="isLoadingSession" @click="triggerLoad">
          {{ isLoadingSession ? t('actionReading') : t('actionReadExport') }}
        </button>
      </div>
    </div>

    <div class="debug-grid">
      <section class="debug-panel summary-panel">
        <div class="metric-strip">
          <div>
            <span>{{ t('labelSession') }}</span>
            <strong>{{ exportData?.sessionId || currentSessionId }}</strong>
          </div>
          <div>
            <span>{{ t('labelTurns') }}</span>
            <strong>{{ sessionMetrics.totalTurns }}</strong>
          </div>
          <div>
            <span>{{ t('labelGenerated') }}</span>
            <strong>{{ sessionMetrics.generatedTurns }}</strong>
          </div>
          <div>
            <span>{{ t('labelSeeded') }}</span>
            <strong>{{ sessionMetrics.seedTurns }}</strong>
          </div>
          <div>
            <span>{{ t('labelWorkspaces') }}</span>
            <strong>{{ sessionMetrics.uniqueScenes }}</strong>
          </div>
          <div>
            <span>{{ t('labelSandbox') }}</span>
            <strong>{{ sandboxMetrics.sessionPlans }}/{{ sandboxMetrics.totalPlans }}</strong>
          </div>
        </div>

        <div class="summary-copy">
          <p>{{ t('sessionDebugCopy') }}</p>
          <p v-if="exportData?.updatedAt">{{ t('lastUpdated', { time: formatTime(exportData.updatedAt) }) }}</p>
        </div>

        <div class="footer-actions">
          <button class="primary-button" type="button" :disabled="!exportData" @click="emit('exportSession')">{{ t('actionCopyJson') }}</button>
          <button class="secondary-button" type="button" @click="emit('resetSession')">{{ t('actionClearCache') }}</button>
          <span class="backend-pill">{{ backendUrl || t('labelNoBackend') }}</span>
        </div>
      </section>

      <section class="debug-panel sandbox-panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">{{ t('labelSandboxShelf') }}</p>
            <h4>{{ t('labelLocalSandbox') }}</h4>
          </div>

          <div class="footer-actions">
            <button class="primary-button" type="button" :disabled="!sandboxPlans.length" @click="emit('exportSandboxPlans')">{{ t('actionCopyJson') }}</button>
            <button class="secondary-button" type="button" :disabled="!sandboxPlans.length" @click="emit('resetSandboxPlans')">{{ t('actionClearSandbox') }}</button>
            <button
              class="secondary-button"
              type="button"
              :disabled="!selectedSandboxPlan || !canImportSandbox"
              @click="selectedSandboxPlan && emit('promoteSandbox', selectedSandboxPlan.id)"
            >
              {{ t('actionImportFlow') }}
            </button>
          </div>
        </div>

        <div v-if="visibleSandboxPlans.length" class="sandbox-layout">
          <div class="sandbox-list">
            <button
              v-for="plan in visibleSandboxPlans"
              :key="plan.id"
              class="turn-card"
              :class="{ active: plan.id === selectedSandboxPlanId }"
              type="button"
              @click="selectedSandboxPlanId = plan.id"
            >
              <strong>{{ plan.title }}</strong>
              <span>{{ formatTime(plan.createdAt) }}</span>
              <p>{{ plan.sceneId }} / {{ plan.nodeId || '-' }}</p>
              <p>{{ plan.steps.length }} {{ t('labelSteps') }} · {{ t('labelRelation') }} {{ formatSignedValue(plan.totalRelationshipDelta) }}</p>
            </button>
          </div>

          <div v-if="selectedSandboxPlan" class="turn-detail">
            <div class="detail-grid">
              <div>
                <span>{{ t('labelSession') }}</span>
                <strong>{{ selectedSandboxPlan.sessionId || '-' }}</strong>
              </div>
              <div>
                <span>{{ t('labelCreated') }}</span>
                <strong>{{ formatTime(selectedSandboxPlan.createdAt) }}</strong>
              </div>
              <div>
                <span>{{ t('labelWorkspace') }}</span>
                <strong>{{ selectedSandboxPlan.sceneId || '-' }}</strong>
              </div>
              <div>
                <span>{{ t('labelNode') }}</span>
                <strong>{{ selectedSandboxPlan.nodeId || '-' }}</strong>
              </div>
            </div>

            <article class="detail-block">
              <p class="block-label">{{ t('labelSummaryBlock') }}</p>
              <p>{{ selectedSandboxPlan.summary || t('fieldNoSummary') }}</p>
            </article>

            <article class="detail-block">
              <p class="block-label">{{ t('labelSteps') }}</p>
              <div class="reply-list">
                <div
                  v-for="(step, index) in selectedSandboxPlan.steps"
                  :key="`${selectedSandboxPlan.id}-${index}-${step.label}`"
                  class="reply-card"
                >
                  <strong>#{{ index + 1 }} {{ step.label }}</strong>
                  <span>{{ displayCode(step.intent) }} · {{ displayCode(step.risk) }} · {{ displayCode(step.targetMood) }}</span>
                  <p>{{ step.consequenceSummary }}</p>
                  <small>{{ step.targetAgentId || t('fieldWorkspace') }} · {{ t('labelRelation') }} {{ formatSignedValue(step.relationshipDelta) }}</small>
                  <small v-if="step.addedFlags.length || step.removedFlags.length">
                    <template v-if="step.addedFlags.length">+{{ step.addedFlags.join(', ') }}</template>
                    <template v-if="step.addedFlags.length && step.removedFlags.length"> · </template>
                    <template v-if="step.removedFlags.length">-{{ step.removedFlags.join(', ') }}</template>
                  </small>
                </div>
              </div>
            </article>

            <article class="detail-block">
              <p class="block-label">{{ t('labelProjectedState') }}</p>
              <div class="detail-grid">
                <div>
                  <span>{{ t('labelTotalDelta') }}</span>
                  <strong>{{ formatSignedValue(selectedSandboxPlan.totalRelationshipDelta) }}</strong>
                </div>
                <div>
                  <span>{{ t('labelFlags') }}</span>
                  <strong>{{ selectedSandboxPlan.finalFlags.length }}</strong>
                </div>
                <div>
                  <span>{{ t('labelPlans') }}</span>
                  <strong>{{ sandboxMetrics.totalPlans }}</strong>
                </div>
                <div>
                  <span>{{ t('labelSteps') }}</span>
                  <strong>{{ sandboxMetrics.totalSteps }}</strong>
                </div>
              </div>
              <div class="token-row">
                <span v-for="flag in selectedSandboxPlan.finalFlags" :key="`${selectedSandboxPlan.id}-${flag}`" class="token muted">{{ flag }}</span>
              </div>
              <div class="reply-list">
                <div
                  v-for="(value, agentId) in selectedSandboxPlan.finalAffinityScores"
                  :key="`${selectedSandboxPlan.id}-${agentId}`"
                  class="reply-card"
                >
                  <strong>{{ agentId }}</strong>
                  <span>{{ t('labelFinalAffinity') }}</span>
                  <p>{{ value }}</p>
                </div>
              </div>
            </article>
          </div>
        </div>

        <div v-else class="turn-empty">
          <p>{{ t('emptyNoSandbox') }}</p>
        </div>
        <p v-if="!canImportSandbox" class="status error">{{ t('sandboxNeedsDraft') }}</p>
      </section>

      <section class="debug-panel branch-panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">{{ t('labelFlowTree') }}</p>
            <h4>{{ t('labelSessionActionGraph') }}</h4>
          </div>
        </div>

        <div class="branch-stage">
          <svg
            class="branch-svg"
            :viewBox="`0 0 ${branchGraph.width} ${branchGraph.height}`"
            :style="{ minWidth: `${branchGraph.width}px`, minHeight: `${branchGraph.height}px` }"
            role="img"
            :aria-label="t('sessionGraphAria')"
          >
            <path
              v-for="edge in branchGraph.edges"
              :key="edge.id"
              :d="edge.path"
              class="branch-edge"
            />

            <g
              v-for="node in branchGraph.nodes"
              :key="node.id"
              class="branch-node"
              :class="{ selected: node.id === selectedTurnId }"
              @click="selectedTurnId = node.id"
            >
              <rect :x="node.x" :y="node.y" width="210" height="42" rx="8" />
              <text :x="node.x + 14" :y="node.y + 17" class="branch-title">
                {{ shortText(node.turn.storyEvent?.title || node.turn.sceneId, 24) }}
              </text>
              <text :x="node.x + 14" :y="node.y + 31" class="branch-meta">
                {{ shortText(node.turn.id, 14) }} · {{ shortText(node.turn.storyEvent?.sourceType || 'turn', 10) }}
              </text>
            </g>
          </svg>
        </div>
      </section>

      <section class="debug-panel replay-panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">{{ t('labelReplay') }}</p>
            <h4>{{ t('labelTurnReplay') }}</h4>
          </div>
        </div>

        <div class="replay-layout">
          <div class="turn-list">
            <button
              v-for="turn in exportData?.turns ?? []"
              :key="turn.id"
              class="turn-card"
              :class="{ active: turn.id === selectedTurnId }"
              type="button"
              @click="selectedTurnId = turn.id"
            >
              <strong>{{ turn.storyEvent?.title || turn.sceneId }}</strong>
              <span>{{ formatTime(turn.timestamp) }}</span>
              <p>{{ shortText(turn.playerChoice || turn.playerInput || t('labelNoDirectMove'), 52) }}</p>
            </button>
          </div>

          <div v-if="selectedTurn" class="turn-detail">
            <div class="detail-grid">
              <div>
                <span>{{ t('labelTurnId') }}</span>
                <strong>{{ selectedTurn.id }}</strong>
              </div>
              <div>
                <span>{{ t('labelWorkspace') }}</span>
                <strong>{{ selectedTurn.sceneId }}</strong>
              </div>
              <div>
                <span>{{ t('labelNode') }}</span>
                <strong>{{ selectedTurn.nodeId }}</strong>
              </div>
              <div>
                <span>{{ t('labelSource') }}</span>
                <strong>{{ selectedTurn.storyEvent?.sourceType || '-' }}</strong>
              </div>
            </div>

            <article class="detail-block">
              <p class="block-label">{{ t('labelUserMove') }}</p>
              <p>{{ selectedTurn.playerChoice || selectedTurn.playerInput || t('fieldUserSilent') }}</p>
            </article>

            <article class="detail-block">
              <p class="block-label">{{ t('labelDirector') }}</p>
              <p>{{ selectedTurn.directorMessage || selectedTurn.storyEvent?.directorSummary || t('fieldNoDirectorSummary') }}</p>
            </article>

            <article class="detail-block">
              <p class="block-label">{{ t('labelSummaryBlock') }}</p>
              <p>{{ selectedTurn.summary }}</p>
            </article>

            <article class="detail-block">
              <p class="block-label">{{ t('labelPresentedActions') }}</p>
              <div class="token-row">
                <span v-for="choice in selectedTurn.presentedChoices" :key="choice" class="token">{{ choice }}</span>
              </div>
            </article>

            <article class="detail-block" v-if="selectedTurn.presentedBranchOptions.length">
              <p class="block-label">{{ t('labelActionOptions') }}</p>
              <div class="reply-list">
                <div
                  v-for="option in selectedTurn.presentedBranchOptions"
                  :key="`${option.label}-${option.targetAgentId}`"
                  class="reply-card"
                >
                  <strong>{{ option.label }}</strong>
                  <span>{{ displayCode(option.intent) }} · {{ displayCode(option.risk) }} · {{ displayCode(option.targetMood) }}</span>
                  <p>{{ option.targetAgentId || t('fieldWorkspace') }} · {{ option.source }} · {{ t('labelRelation') }} {{ option.relationshipDelta >= 0 ? '+' : '' }}{{ option.relationshipDelta }}</p>
                  <small>{{ option.consequenceSummary }}</small>
                  <small v-if="option.addedFlags.length || option.removedFlags.length">
                    <template v-if="option.addedFlags.length">+{{ option.addedFlags.join(', ') }}</template>
                    <template v-if="option.addedFlags.length && option.removedFlags.length"> · </template>
                    <template v-if="option.removedFlags.length">-{{ option.removedFlags.join(', ') }}</template>
                  </small>
                </div>
              </div>
            </article>

            <article class="detail-block">
              <p class="block-label">{{ t('labelAgentReplies') }}</p>
              <div class="reply-list">
                <div v-for="reply in selectedTurn.agentReplies" :key="reply.agentId + reply.message" class="reply-card">
                  <strong>{{ reply.agentName }}</strong>
                  <span>{{ reply.role }} · {{ reply.initiativeScore }}</span>
                  <p>{{ reply.objective }}</p>
                  <small>{{ reply.memorySummary }}</small>
                </div>
              </div>
            </article>

            <article v-if="selectedTurn.orchestration" class="detail-block">
              <p class="block-label">{{ t('labelOrchestration') }}</p>
              <div class="detail-grid">
                <div>
                  <span>{{ t('labelVerdict') }}</span>
                  <strong>{{ selectedTurn.orchestration.critic.verdict }}</strong>
                </div>
                <div>
                  <span>{{ t('labelTension') }}</span>
                  <strong>{{ selectedTurn.orchestration.planner.tensionLabel }}</strong>
                </div>
                <div>
                  <span>{{ t('labelPacing') }}</span>
                  <strong>{{ selectedTurn.orchestration.planner.pacingLabel }}</strong>
                </div>
                <div>
                  <span>{{ t('labelFocusScore') }}</span>
                  <strong>{{ selectedTurn.orchestration.critic.focusScore }}</strong>
                </div>
              </div>
              <p>{{ selectedTurn.orchestration.planner.sceneGoal }}</p>
              <div class="token-row">
                <span v-for="agentId in selectedTurn.orchestration.speakingAgentIds" :key="agentId" class="token">
                  {{ agentId }}
                </span>
              </div>
              <div class="reply-list">
                <div
                  v-for="option in selectedTurn.orchestration.nextBranchOptions"
                  :key="`${option.label}-${option.targetAgentId}-orch`"
                  class="reply-card"
                >
                  <strong>{{ option.label }}</strong>
                  <span>{{ displayCode(option.intent) }} · {{ displayCode(option.risk) }} · {{ displayCode(option.targetMood) }}</span>
                  <p>{{ option.targetAgentId || t('fieldWorkspace') }} · {{ option.source }} · {{ t('labelRelation') }} {{ option.relationshipDelta >= 0 ? '+' : '' }}{{ option.relationshipDelta }}</p>
                  <small>{{ option.consequenceSummary }}</small>
                  <small v-if="option.addedFlags.length || option.removedFlags.length">
                    <template v-if="option.addedFlags.length">+{{ option.addedFlags.join(', ') }}</template>
                    <template v-if="option.addedFlags.length && option.removedFlags.length"> · </template>
                    <template v-if="option.removedFlags.length">-{{ option.removedFlags.join(', ') }}</template>
                  </small>
                </div>
              </div>
              <div class="reply-list">
                <div v-for="note in selectedTurn.orchestration.critic.notes" :key="note" class="reply-card">
                  <strong>{{ t('labelCritic') }}</strong>
                  <p>{{ note }}</p>
                </div>
              </div>
              <div class="reply-list">
                <div
                  v-for="invocation in selectedTurn.orchestration.aiInvocations"
                  :key="`${invocation.operation}-${invocation.targetId}-${invocation.latencyMs}`"
                  class="reply-card"
                >
                  <strong>{{ invocation.operation }}</strong>
                  <span>
                    {{ invocation.providerSucceeded ? t('fieldProvider') : invocation.fallbackUsed ? t('fieldFallback') : t('fieldLocal') }}
                    · {{ invocation.latencyMs }}ms
                  </span>
                  <p>{{ invocation.targetId || t('fieldWorkspace') }} · {{ invocation.model || t('fieldNoModel') }}</p>
                  <small>{{ invocation.errorMessage || invocation.providerUrl || t('fieldNoProviderMeta') }}</small>
                </div>
              </div>
              <div class="reply-list">
                <div v-for="plan in selectedTurn.orchestration.plans" :key="plan.agentId" class="reply-card">
                  <strong>{{ plan.agentName }}</strong>
                  <span>{{ plan.role }} · {{ plan.initiativeScore }} · {{ plan.shouldSpeak ? t('fieldSpeak') : t('fieldHold') }}</span>
                  <p>{{ plan.objective }}</p>
                  <small>{{ plan.scoreFactors.map((factor) => `${factor.delta >= 0 ? '+' : ''}${factor.delta} ${factor.code}`).join(' · ') }}</small>
                </div>
              </div>
            </article>

            <article class="detail-block">
              <p class="block-label">{{ t('labelStateSnapshot') }}</p>
              <div class="token-row">
                <span v-for="flag in selectedTurn.stateSnapshot.flags" :key="flag" class="token muted">{{ flag }}</span>
              </div>
            </article>
          </div>

          <div v-else class="turn-empty">
            <p>{{ t('emptyNoSessionExport') }}</p>
          </div>
        </div>
      </section>
    </div>

    <div class="status-row">
      <p v-if="sessionStatus" class="status success">{{ sessionStatus }}</p>
      <p v-if="sessionError" class="status error">{{ sessionError }}</p>
    </div>
  </section>
</template>

<style scoped>
.debug-stage{display:grid;gap:12px;min-width:0;min-height:calc(100dvh - 154px);padding:14px;border:1px solid var(--color-border);border-radius:var(--radius);background:var(--color-bg-soft);overflow:hidden}
.debug-head,.debug-toolbar,.panel-head,.footer-actions,.status-row{display:flex;justify-content:space-between;gap:12px;align-items:center;flex-wrap:wrap}
.debug-grid{display:grid;grid-template-columns:300px minmax(0,1fr);grid-template-areas:"summary summary" "sandbox branch" "sandbox replay";gap:10px;min-width:0}
.debug-panel{min-width:0;border:1px solid var(--color-border-soft);background:var(--color-surface);border-radius:var(--radius);padding:14px;box-shadow:var(--shadow-card)}
.summary-panel{grid-area:summary}
.sandbox-panel{grid-area:sandbox}
.branch-panel{grid-area:branch}
.replay-panel{grid-area:replay}
.summary-panel{display:grid;gap:12px}
.metric-strip{display:grid;grid-template-columns:repeat(auto-fit,minmax(92px,1fr));gap:10px;min-width:0}
.metric-strip div,.detail-grid div{display:grid;gap:4px;min-width:92px}
.metric-strip span,.detail-grid span,.block-label{color:var(--color-faint);font-size:12px;letter-spacing:0}
.metric-strip strong,.detail-grid strong{color:var(--color-text);font-size:15px}
.summary-copy{display:grid;gap:8px;color:var(--color-muted);line-height:1.65}
.backend-pill{display:inline-flex;padding:6px 10px;border-radius:var(--radius);background:var(--color-token-muted-bg);color:var(--color-faint);font-size:12px;font-weight:700}
.branch-stage{overflow:auto;padding:8px;border:1px solid var(--color-border-soft);border-radius:var(--radius);background:var(--color-graph-bg);scrollbar-gutter:stable}
.branch-svg{display:block}
.branch-edge{fill:none;stroke:var(--color-graph-edge);stroke-width:2}
.branch-node{cursor:pointer}
.branch-node rect{fill:var(--color-graph-node);stroke:var(--color-graph-node-border);stroke-width:1.2;transition:fill 180ms var(--ease),stroke 180ms var(--ease)}
.branch-node.selected rect{fill:var(--color-graph-node-selected);stroke:var(--color-primary);stroke-width:1.8}
.branch-title,.branch-meta{font-family:"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;pointer-events:none}
.branch-title{fill:var(--color-heading-soft);font-size:13px;font-weight:700}
.branch-meta{fill:var(--color-muted);font-size:11px}
.replay-layout,.sandbox-layout{display:grid;grid-template-columns:260px minmax(0,1fr);gap:12px}
.turn-list,.sandbox-list,.reply-list,.detail-grid{display:grid;gap:10px}
.turn-list{max-height:520px;overflow:auto;padding-right:4px;scrollbar-gutter:stable}
.sandbox-list{max-height:420px;overflow:auto;padding-right:4px;scrollbar-gutter:stable}
.turn-card{appearance:none;border:1px solid var(--color-border-soft);border-radius:var(--radius);padding:14px;background:var(--color-surface);color:var(--color-text);text-align:left;cursor:pointer;display:grid;gap:6px;transition:background 180ms var(--ease),border-color 180ms var(--ease),box-shadow 180ms var(--ease),transform 180ms var(--ease)}
.turn-card.active{border-color:var(--color-primary);background:var(--color-surface-muted);box-shadow:var(--shadow-active)}
.turn-card:hover{border-color:var(--color-primary);background:var(--color-hover);transform:translateY(-1px)}
.turn-card span{color:var(--color-faint);font-size:12px}
.turn-card p{margin:0;color:var(--color-muted);line-height:1.5}
.turn-detail,.turn-empty{display:grid;gap:14px}
.detail-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
.detail-block{display:grid;gap:10px;padding:14px;border:1px solid var(--color-border-soft);border-radius:var(--radius);background:var(--color-surface)}
.detail-block p,.turn-empty p{margin:0;color:var(--color-muted);line-height:1.65}
.token-row{display:flex;gap:8px;flex-wrap:wrap}
.token{display:inline-flex;padding:5px 9px;border-radius:var(--radius);background:var(--color-token-bg);color:var(--color-token-text);font-size:12px;font-weight:700}
.token.muted{background:var(--color-token-muted-bg);color:var(--color-faint)}
.reply-card{display:grid;gap:4px;padding:12px;border:1px solid var(--color-border-soft);border-radius:var(--radius);background:var(--color-surface)}
.reply-card span,.reply-card small{color:var(--color-faint)}
.reply-card p{margin:0;color:var(--color-text)}
.eyebrow{margin:0 0 6px;font-size:11px;letter-spacing:0;color:var(--color-primary-strong);font-weight:800;line-height:1.2}
.search-input{width:min(320px,100%);min-height:44px;padding:10px 14px;border:1px solid var(--color-border);border-radius:var(--radius);outline:none;color:var(--color-text);background:var(--color-input);font:inherit}
.search-input:focus{border-color:var(--color-primary);box-shadow:0 0 0 4px var(--color-focus-ring)}
.primary-button,.secondary-button{appearance:none;border:0;cursor:pointer;min-height:44px;padding:0 16px;border-radius:var(--radius);font-weight:800;transition:background 180ms var(--ease),border-color 180ms var(--ease),box-shadow 180ms var(--ease)}
.primary-button{background:var(--color-accent);color:var(--color-on-accent)}
.secondary-button{border:1px solid var(--color-border);background:var(--color-input);color:var(--color-primary-strong)}
.primary-button:hover{background:var(--color-accent-hover);box-shadow:var(--shadow-accent)}
.secondary-button:hover{border-color:var(--color-primary);background:var(--color-hover)}
.status{display:inline-flex;align-items:center;min-height:44px;padding:10px 14px;border-radius:var(--radius);line-height:1.5}
.status.success{color:var(--color-success-text);background:var(--color-success-bg);border:1px solid var(--color-success-border)}
.status.error{color:var(--color-danger-text);background:var(--color-danger-bg);border:1px solid var(--color-danger-border)}
h3,h4,p{margin:0}
h3{font-size:24px;line-height:1.15;color:var(--color-heading);overflow-wrap:anywhere}
h4{font-size:17px;line-height:1.2;color:var(--color-heading-soft);overflow-wrap:anywhere}
.debug-panel,.turn-card,.detail-block,.reply-card,.backend-pill{overflow-wrap:anywhere}
@media (max-width:1180px){.debug-grid{grid-template-columns:1fr;grid-template-areas:"summary" "branch" "sandbox" "replay"}}
@media (max-width:980px){.debug-stage{min-height:auto}.replay-layout,.sandbox-layout{grid-template-columns:1fr}.detail-grid{grid-template-columns:1fr}.debug-toolbar,.search-input{width:100%}}
@media (max-width:520px){.debug-stage{padding:14px}.debug-panel{padding:14px}.metric-strip{grid-template-columns:repeat(2,minmax(0,1fr))}.metric-strip div,.detail-grid div{min-width:0}.footer-actions .primary-button,.footer-actions .secondary-button{flex:1 1 140px}}
@media (prefers-reduced-motion:reduce){.turn-card,.primary-button,.secondary-button{transition:none}.turn-card:hover{transform:none}}
</style>
