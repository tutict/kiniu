<script setup lang="ts">
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
</script>

<template>
  <section class="debug-stage">
    <div class="debug-head">
      <div>
        <p class="eyebrow">Session Debug</p>
        <h3>导出与回放</h3>
      </div>

      <div class="debug-toolbar">
        <input v-model="requestedSessionId" class="search-input" type="text" placeholder="session-123456">
        <button class="secondary-button" type="button" :disabled="isLoadingSession" @click="triggerLoad">
          {{ isLoadingSession ? '读取中...' : '读取导出' }}
        </button>
      </div>
    </div>

    <div class="debug-grid">
      <section class="debug-panel summary-panel">
        <div class="metric-strip">
          <div>
            <span>Session</span>
            <strong>{{ exportData?.sessionId || currentSessionId }}</strong>
          </div>
          <div>
            <span>Turns</span>
            <strong>{{ sessionMetrics.totalTurns }}</strong>
          </div>
          <div>
            <span>Generated</span>
            <strong>{{ sessionMetrics.generatedTurns }}</strong>
          </div>
          <div>
            <span>Seeded</span>
            <strong>{{ sessionMetrics.seedTurns }}</strong>
          </div>
          <div>
            <span>Scenes</span>
            <strong>{{ sessionMetrics.uniqueScenes }}</strong>
          </div>
          <div>
            <span>Sandbox</span>
            <strong>{{ sandboxMetrics.sessionPlans }}/{{ sandboxMetrics.totalPlans }}</strong>
          </div>
        </div>

        <div class="summary-copy">
          <p>当前导出用于调试动态编排结果、验证 seed 与 generated beat 的切换，并为前端演示或 Electron 嵌入提供直接 JSON。</p>
          <p v-if="exportData?.updatedAt">最近更新时间：{{ formatTime(exportData.updatedAt) }}</p>
        </div>

        <div class="footer-actions">
          <button class="primary-button" type="button" :disabled="!exportData" @click="emit('exportSession')">复制 JSON</button>
          <button class="secondary-button" type="button" @click="emit('resetSession')">清空缓存</button>
          <span class="backend-pill">{{ backendUrl || '未配置后端' }}</span>
        </div>
      </section>

      <section class="debug-panel sandbox-panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Sandbox Shelf</p>
            <h4>本地分支沙盘</h4>
          </div>

          <div class="footer-actions">
            <button class="primary-button" type="button" :disabled="!sandboxPlans.length" @click="emit('exportSandboxPlans')">复制 JSON</button>
            <button class="secondary-button" type="button" :disabled="!sandboxPlans.length" @click="emit('resetSandboxPlans')">清空沙盘</button>
            <button
              class="secondary-button"
              type="button"
              :disabled="!selectedSandboxPlan || !canImportSandbox"
              @click="selectedSandboxPlan && emit('promoteSandbox', selectedSandboxPlan.id)"
            >
              导入草稿
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
              <p>{{ plan.steps.length }} steps · rel {{ formatSignedValue(plan.totalRelationshipDelta) }}</p>
            </button>
          </div>

          <div v-if="selectedSandboxPlan" class="turn-detail">
            <div class="detail-grid">
              <div>
                <span>Session</span>
                <strong>{{ selectedSandboxPlan.sessionId || '-' }}</strong>
              </div>
              <div>
                <span>Created</span>
                <strong>{{ formatTime(selectedSandboxPlan.createdAt) }}</strong>
              </div>
              <div>
                <span>Scene</span>
                <strong>{{ selectedSandboxPlan.sceneId || '-' }}</strong>
              </div>
              <div>
                <span>Node</span>
                <strong>{{ selectedSandboxPlan.nodeId || '-' }}</strong>
              </div>
            </div>

            <article class="detail-block">
              <p class="block-label">Summary</p>
              <p>{{ selectedSandboxPlan.summary || 'No summary.' }}</p>
            </article>

            <article class="detail-block">
              <p class="block-label">Steps</p>
              <div class="reply-list">
                <div
                  v-for="(step, index) in selectedSandboxPlan.steps"
                  :key="`${selectedSandboxPlan.id}-${index}-${step.label}`"
                  class="reply-card"
                >
                  <strong>#{{ index + 1 }} {{ step.label }}</strong>
                  <span>{{ step.intent }} 路 {{ step.risk }} 路 {{ step.targetMood }}</span>
                  <p>{{ step.consequenceSummary }}</p>
                  <small>{{ step.targetAgentId || 'scene' }} 路 rel {{ formatSignedValue(step.relationshipDelta) }}</small>
                  <small v-if="step.addedFlags.length || step.removedFlags.length">
                    <template v-if="step.addedFlags.length">+{{ step.addedFlags.join(', ') }}</template>
                    <template v-if="step.addedFlags.length && step.removedFlags.length"> 路 </template>
                    <template v-if="step.removedFlags.length">-{{ step.removedFlags.join(', ') }}</template>
                  </small>
                </div>
              </div>
            </article>

            <article class="detail-block">
              <p class="block-label">Projected State</p>
              <div class="detail-grid">
                <div>
                  <span>Total Delta</span>
                  <strong>{{ formatSignedValue(selectedSandboxPlan.totalRelationshipDelta) }}</strong>
                </div>
                <div>
                  <span>Flags</span>
                  <strong>{{ selectedSandboxPlan.finalFlags.length }}</strong>
                </div>
                <div>
                  <span>Plans</span>
                  <strong>{{ sandboxMetrics.totalPlans }}</strong>
                </div>
                <div>
                  <span>Steps</span>
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
                  <span>final affinity</span>
                  <p>{{ value }}</p>
                </div>
              </div>
            </article>
          </div>
        </div>

        <div v-else class="turn-empty">
          <p>当前还没有保存的沙盘链。先回到游戏页，把分支预演保存到本地调试区。</p>
        </div>
        <p v-if="!canImportSandbox" class="status error">先加载或创建一个故事草稿，才能把沙盘链导入到故事图。</p>
      </section>

      <section class="debug-panel branch-panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Branch Tree</p>
            <h4>会话分支图</h4>
          </div>
        </div>

        <div class="branch-stage">
          <svg
            class="branch-svg"
            :viewBox="`0 0 ${branchGraph.width} ${branchGraph.height}`"
            :style="{ minWidth: `${branchGraph.width}px`, minHeight: `${branchGraph.height}px` }"
            role="img"
            aria-label="Session branch graph"
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
              <rect :x="node.x" :y="node.y" width="210" height="42" rx="14" />
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
            <p class="eyebrow">Replay</p>
            <h4>逐回合回放</h4>
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
              <p>{{ shortText(turn.playerChoice || turn.playerInput || 'No direct player move.', 52) }}</p>
            </button>
          </div>

          <div v-if="selectedTurn" class="turn-detail">
            <div class="detail-grid">
              <div>
                <span>Turn ID</span>
                <strong>{{ selectedTurn.id }}</strong>
              </div>
              <div>
                <span>Scene</span>
                <strong>{{ selectedTurn.sceneId }}</strong>
              </div>
              <div>
                <span>Node</span>
                <strong>{{ selectedTurn.nodeId }}</strong>
              </div>
              <div>
                <span>Source</span>
                <strong>{{ selectedTurn.storyEvent?.sourceType || '-' }}</strong>
              </div>
            </div>

            <article class="detail-block">
              <p class="block-label">Player Move</p>
              <p>{{ selectedTurn.playerChoice || selectedTurn.playerInput || 'The player remained silent.' }}</p>
            </article>

            <article class="detail-block">
              <p class="block-label">Director</p>
              <p>{{ selectedTurn.directorMessage || selectedTurn.storyEvent?.directorSummary || 'No director summary.' }}</p>
            </article>

            <article class="detail-block">
              <p class="block-label">Summary</p>
              <p>{{ selectedTurn.summary }}</p>
            </article>

            <article class="detail-block">
              <p class="block-label">Presented Choices</p>
              <div class="token-row">
                <span v-for="choice in selectedTurn.presentedChoices" :key="choice" class="token">{{ choice }}</span>
              </div>
            </article>

            <article class="detail-block" v-if="selectedTurn.presentedBranchOptions.length">
              <p class="block-label">Branch Options</p>
              <div class="reply-list">
                <div
                  v-for="option in selectedTurn.presentedBranchOptions"
                  :key="`${option.label}-${option.targetAgentId}`"
                  class="reply-card"
                >
                  <strong>{{ option.label }}</strong>
                  <span>{{ option.intent }} · {{ option.risk }} · {{ option.targetMood }}</span>
                  <p>{{ option.targetAgentId || 'scene' }} · {{ option.source }} · rel {{ option.relationshipDelta >= 0 ? '+' : '' }}{{ option.relationshipDelta }}</p>
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
              <p class="block-label">Agent Replies</p>
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
              <p class="block-label">Orchestration</p>
              <div class="detail-grid">
                <div>
                  <span>Verdict</span>
                  <strong>{{ selectedTurn.orchestration.critic.verdict }}</strong>
                </div>
                <div>
                  <span>Tension</span>
                  <strong>{{ selectedTurn.orchestration.planner.tensionLabel }}</strong>
                </div>
                <div>
                  <span>Pacing</span>
                  <strong>{{ selectedTurn.orchestration.planner.pacingLabel }}</strong>
                </div>
                <div>
                  <span>Focus Score</span>
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
                  <span>{{ option.intent }} · {{ option.risk }} · {{ option.targetMood }}</span>
                  <p>{{ option.targetAgentId || 'scene' }} · {{ option.source }} · rel {{ option.relationshipDelta >= 0 ? '+' : '' }}{{ option.relationshipDelta }}</p>
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
                  <strong>Critic</strong>
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
                    {{ invocation.providerSucceeded ? 'provider' : invocation.fallbackUsed ? 'fallback' : 'local' }}
                    路 {{ invocation.latencyMs }}ms
                  </span>
                  <p>{{ invocation.targetId || 'scene' }} 路 {{ invocation.model || 'no-model' }}</p>
                  <small>{{ invocation.errorMessage || invocation.providerUrl || 'No provider metadata.' }}</small>
                </div>
              </div>
              <div class="reply-list">
                <div v-for="plan in selectedTurn.orchestration.plans" :key="plan.agentId" class="reply-card">
                  <strong>{{ plan.agentName }}</strong>
                  <span>{{ plan.role }} 路 {{ plan.initiativeScore }} 路 {{ plan.shouldSpeak ? 'speak' : 'hold' }}</span>
                  <p>{{ plan.objective }}</p>
                  <small>{{ plan.scoreFactors.map((factor) => `${factor.delta >= 0 ? '+' : ''}${factor.delta} ${factor.code}`).join(' · ') }}</small>
                </div>
              </div>
            </article>

            <article class="detail-block">
              <p class="block-label">State Snapshot</p>
              <div class="token-row">
                <span v-for="flag in selectedTurn.stateSnapshot.flags" :key="flag" class="token muted">{{ flag }}</span>
              </div>
            </article>
          </div>

          <div v-else class="turn-empty">
            <p>还没有可回放的 session 导出。先读取一个 session。</p>
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
.debug-stage{display:grid;gap:18px;padding:28px;border:1px solid rgba(255,255,255,.08);border-radius:30px;background:linear-gradient(180deg,rgba(13,17,24,.84) 0%,rgba(10,14,20,.66) 100%)}
.debug-head,.debug-toolbar,.panel-head,.metric-strip,.footer-actions,.status-row{display:flex;justify-content:space-between;gap:12px;align-items:center;flex-wrap:wrap}
.debug-grid{display:grid;gap:18px}
.debug-panel{border:1px solid rgba(255,255,255,.08);background:rgba(255,255,255,.03);border-radius:24px;padding:20px}
.summary-panel{display:grid;gap:18px}
.metric-strip div,.detail-grid div{display:grid;gap:4px;min-width:92px}
.metric-strip span,.detail-grid span,.block-label{color:#9e9a93;font-size:12px;text-transform:uppercase;letter-spacing:.12em}
.metric-strip strong,.detail-grid strong{color:#f6efdf;font-size:15px}
.summary-copy{display:grid;gap:8px;color:#cfc7ba;line-height:1.7}
.backend-pill{display:inline-flex;padding:8px 12px;border-radius:999px;background:rgba(255,255,255,.05);color:#c8c0b2;font-size:12px}
.branch-stage{overflow:auto;padding:8px;border:1px solid rgba(255,255,255,.08);border-radius:22px;background:radial-gradient(circle at top left,rgba(229,199,138,.08),transparent 30%),rgba(255,255,255,.02)}
.branch-svg{display:block}
.branch-edge{fill:none;stroke:rgba(197,181,154,.36);stroke-width:2}
.branch-node{cursor:pointer}
.branch-node rect{fill:rgba(17,21,28,.92);stroke:rgba(255,255,255,.08);stroke-width:1.2;transition:fill 160ms ease,stroke 160ms ease}
.branch-node.selected rect{fill:rgba(229,199,138,.12);stroke:rgba(229,199,138,.72);stroke-width:1.8}
.branch-title,.branch-meta{font-family:"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;pointer-events:none}
.branch-title{fill:#f2eadd;font-size:13px;font-weight:700}
.branch-meta{fill:#9a978f;font-size:11px}
.replay-layout,.sandbox-layout{display:grid;grid-template-columns:280px minmax(0,1fr);gap:16px}
.turn-list,.sandbox-list,.reply-list,.detail-grid{display:grid;gap:10px}
.turn-list{max-height:520px;overflow:auto;padding-right:4px}
.sandbox-list{max-height:420px;overflow:auto;padding-right:4px}
.turn-card{appearance:none;border:1px solid transparent;border-radius:18px;padding:14px;background:rgba(255,255,255,.04);color:#f4ede0;text-align:left;cursor:pointer;display:grid;gap:6px;transition:transform 160ms ease,border-color 160ms ease,background 160ms ease}
.turn-card.active{border-color:rgba(229,199,138,.48);background:rgba(229,199,138,.08)}
.turn-card span{color:#9e9a93;font-size:12px}
.turn-card p{margin:0;color:#cfc7ba;line-height:1.5}
.turn-detail,.turn-empty{display:grid;gap:14px}
.detail-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
.detail-block{display:grid;gap:10px;padding:16px;border-radius:18px;background:rgba(255,255,255,.04)}
.detail-block p,.turn-empty p{margin:0;color:#ddd4c4;line-height:1.7}
.token-row{display:flex;gap:8px;flex-wrap:wrap}
.token{display:inline-flex;padding:6px 10px;border-radius:999px;background:rgba(229,199,138,.12);color:#efd5a1;font-size:12px}
.token.muted{background:rgba(255,255,255,.05);color:#b3aca0}
.reply-card{display:grid;gap:4px;padding:12px;border-radius:16px;background:rgba(255,255,255,.04)}
.reply-card span,.reply-card small{color:#9d9688}
.reply-card p{margin:0;color:#f0e7d7}
.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:.24em;text-transform:uppercase;color:#b9a988}
.search-input{width:min(320px,100%);padding:14px 16px;border:1px solid rgba(255,255,255,.1);border-radius:16px;outline:none;color:#f7f0e2;background:rgba(255,255,255,.04);font:inherit}
.search-input:focus{border-color:rgba(229,199,138,.54);box-shadow:0 0 0 4px rgba(229,199,138,.08)}
.primary-button,.secondary-button{appearance:none;border:0;cursor:pointer;padding:12px 18px;border-radius:999px;transition:transform 160ms ease,background 160ms ease}
.primary-button{background:#e5c78a;color:#11161d}
.secondary-button{background:rgba(255,255,255,.06);color:#f4ede0}
.primary-button:hover,.secondary-button:hover,.turn-card:hover{transform:translateY(-1px)}
.status{display:inline-flex;align-items:center;padding:12px 14px;border-radius:14px;line-height:1.5}
.status.success{color:#dff7d6;background:rgba(117,198,122,.14)}
.status.error{color:#ffd7d2;background:rgba(217,94,81,.14)}
h3,h4,p{margin:0}
h3{font-size:clamp(28px,4vw,38px);line-height:1}
h4{font-size:18px;line-height:1.1;color:#efe7d6}
@media (max-width:980px){.replay-layout,.sandbox-layout{grid-template-columns:1fr}.detail-grid{grid-template-columns:1fr}}
</style>
