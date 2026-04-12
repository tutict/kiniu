<script setup lang="ts">
import type { ApiSettings, BranchOptionView, ChatMessage, OrchestrationTraceView, SandboxPlanDraft, WorldState } from '../types/game'

const props = defineProps<{
  settings: ApiSettings
  worldState: WorldState
  sceneLabel: string
  affinityEntries: [string, number][]
  currentBranchOptions: BranchOptionView[]
  orchestration: OrchestrationTraceView | null
  messages: ChatMessage[]
  playerInput: string
  isSending: boolean
}>()

const emit = defineEmits<{
  'update:playerInput': [value: string]
  'send-turn': [choice?: string]
  'save-sandbox': [plan: SandboxPlanDraft]
}>()

const selectedBranchLabel = ref('')
const sandboxQueue = ref<string[]>([])
const sandboxStatus = ref('')

watch(
  () => props.currentBranchOptions,
  (options) => {
    if (!options.length) {
      selectedBranchLabel.value = ''
      sandboxQueue.value = []
      sandboxStatus.value = ''
      return
    }
    if (!options.some(option => option.label === selectedBranchLabel.value)) {
      selectedBranchLabel.value = options[0].label
    }
    sandboxQueue.value = sandboxQueue.value.filter(label => options.some(option => option.label === label))
  },
  { immediate: true }
)

const previewBranch = computed(() => {
  return props.currentBranchOptions.find(option => option.label === selectedBranchLabel.value)
    ?? props.currentBranchOptions[0]
    ?? null
})

const previewAffinity = computed(() => {
  const option = previewBranch.value
  if (!option || !option.targetAgentId) return null
  const currentValue = props.worldState.affinityScores?.[option.targetAgentId] ?? 0
  return {
    agentId: option.targetAgentId,
    currentValue,
    nextValue: currentValue + option.relationshipDelta
  }
})

const previewFlags = computed(() => {
  const option = previewBranch.value
  const nextFlags = new Set(props.worldState.flags ?? [])
  if (!option) {
    return {
      added: [] as string[],
      removed: [] as string[],
      finalFlags: Array.from(nextFlags)
    }
  }

  option.removedFlags.forEach(flag => nextFlags.delete(flag))
  option.addedFlags.forEach(flag => nextFlags.add(flag))

  return {
    added: option.addedFlags,
    removed: option.removedFlags,
    finalFlags: Array.from(nextFlags)
  }
})

const sandboxOptions = computed(() => {
  return sandboxQueue.value
    .map(label => props.currentBranchOptions.find(option => option.label === label))
    .filter((option): option is BranchOptionView => Boolean(option))
})

const sandboxAffinityEntries = computed(() => {
  const affinityScores = new Map(Object.entries(props.worldState.affinityScores ?? {}))

  sandboxOptions.value.forEach((option) => {
    if (!option.targetAgentId) return
    const currentValue = affinityScores.get(option.targetAgentId) ?? 0
    affinityScores.set(option.targetAgentId, currentValue + option.relationshipDelta)
  })

  return Array.from(affinityScores.entries()).sort((left, right) => left[0].localeCompare(right[0]))
})

const sandboxFlags = computed(() => {
  const nextFlags = new Set(props.worldState.flags ?? [])

  sandboxOptions.value.forEach((option) => {
    option.removedFlags.forEach(flag => nextFlags.delete(flag))
    option.addedFlags.forEach(flag => nextFlags.add(flag))
  })

  return Array.from(nextFlags)
})

const sandboxRelationshipDelta = computed(() => {
  return sandboxOptions.value.reduce((total, option) => total + option.relationshipDelta, 0)
})

const sandboxDraft = computed<SandboxPlanDraft | null>(() => {
  if (!sandboxOptions.value.length) return null

  const summary = sandboxOptions.value
    .map((option, index) => `${index + 1}. ${option.consequenceSummary}`)
    .join(' ')

  return {
    sceneId: props.worldState.currentScene,
    nodeId: props.worldState.currentNodeId ?? '',
    title: `${props.sceneLabel} Sandbox x${sandboxOptions.value.length}`,
    summary,
    steps: sandboxOptions.value.map(option => ({ ...option })),
    totalRelationshipDelta: sandboxRelationshipDelta.value,
    finalFlags: [...sandboxFlags.value],
    finalAffinityScores: Object.fromEntries(sandboxAffinityEntries.value)
  }
})

function addPreviewToSandbox() {
  if (!previewBranch.value) return
  sandboxQueue.value = [...sandboxQueue.value, previewBranch.value.label]
  sandboxStatus.value = ''
}

function removeLastSandboxStep() {
  if (!sandboxQueue.value.length) return
  sandboxQueue.value = sandboxQueue.value.slice(0, -1)
  sandboxStatus.value = ''
}

function clearSandbox() {
  sandboxQueue.value = []
  sandboxStatus.value = ''
}

function saveSandbox() {
  if (!sandboxDraft.value) return
  emit('save-sandbox', sandboxDraft.value)
  sandboxStatus.value = `Saved ${sandboxDraft.value.steps.length} sandbox steps to the debug shelf.`
}
</script>

<template>
  <section class="game-view">
    <aside class="scene-panel">
      <p class="eyebrow">当前场景</p>
      <h2>{{ sceneLabel }}</h2>
      <p class="scene-copy">
        世界状态与角色好感会随着每次输入实时更新，创作台里的节点结构决定分支如何展开。
      </p>

      <div class="scene-meta">
        <div>
          <span>Backend</span>
          <strong>{{ settings.backendUrl || '未配置' }}</strong>
        </div>
        <div>
          <span>Node</span>
          <strong>{{ worldState.currentNodeId || '未进入节点' }}</strong>
        </div>
      </div>

      <div class="state-block">
        <h3>剧情标记</h3>
        <div class="token-row">
          <span v-for="flag in worldState.flags" :key="flag" class="token">{{ flag }}</span>
          <span v-if="worldState.flags.length === 0" class="token muted">暂无</span>
        </div>
      </div>

      <div class="state-block">
        <h3>好感度</h3>
        <div class="affinity-list">
          <div v-for="[name, value] in affinityEntries" :key="name" class="affinity-item">
            <span>{{ name }}</span>
            <strong>{{ value }}</strong>
          </div>
        </div>
      </div>
      <div v-if="previewBranch" class="state-block">
        <div class="preview-head">
          <h3>Branch Preview</h3>
          <span class="preview-risk">{{ previewBranch.risk }}</span>
        </div>
        <p class="plan-copy">{{ previewBranch.label }}</p>
        <p class="scene-copy">{{ previewBranch.consequenceSummary }}</p>
        <div class="orchestration-meta">
          <div>
            <span>Intent</span>
            <strong>{{ previewBranch.intent }}</strong>
          </div>
          <div>
            <span>Mood</span>
            <strong>{{ previewBranch.targetMood }}</strong>
          </div>
          <div>
            <span>Target</span>
            <strong>{{ previewBranch.targetAgentId || 'scene' }}</strong>
          </div>
        </div>
        <div v-if="previewAffinity" class="orchestration-meta">
          <div>
            <span>Affinity</span>
            <strong>{{ previewAffinity.agentId }}</strong>
          </div>
          <div>
            <span>Current</span>
            <strong>{{ previewAffinity.currentValue }}</strong>
          </div>
          <div>
            <span>Predicted</span>
            <strong>{{ previewAffinity.nextValue }}</strong>
          </div>
        </div>
        <div class="token-row">
          <span v-for="flag in previewFlags.added" :key="`add-${flag}`" class="token added">+ {{ flag }}</span>
          <span v-for="flag in previewFlags.removed" :key="`remove-${flag}`" class="token removed">- {{ flag }}</span>
          <span v-if="!previewFlags.added.length && !previewFlags.removed.length" class="token muted">No flag changes</span>
        </div>
        <div class="token-row">
          <span v-for="flag in previewFlags.finalFlags.slice(0, 6)" :key="`final-${flag}`" class="token final">{{ flag }}</span>
        </div>
        <div class="sandbox-actions">
          <button class="sandbox-button primary" type="button" @click="addPreviewToSandbox">Add To Sandbox</button>
          <button class="sandbox-button" type="button" :disabled="!sandboxQueue.length" @click="removeLastSandboxStep">Undo Step</button>
          <button class="sandbox-button" type="button" :disabled="!sandboxQueue.length" @click="clearSandbox">Clear</button>
        </div>
      </div>
      <div v-if="sandboxOptions.length" class="state-block">
        <div class="preview-head">
          <h3>Sandbox</h3>
          <span class="preview-risk">{{ sandboxOptions.length }} steps</span>
        </div>
        <div class="plan-list">
          <article
            v-for="(option, index) in sandboxOptions"
            :key="`${option.label}-${index}`"
            class="plan-card"
          >
            <div class="plan-head">
              <strong>#{{ index + 1 }} {{ option.label }}</strong>
              <span>{{ option.relationshipDelta >= 0 ? '+' : '' }}{{ option.relationshipDelta }}</span>
            </div>
            <p>{{ option.consequenceSummary }}</p>
            <small>{{ option.targetAgentId || 'scene' }} · {{ option.intent }} · {{ option.risk }}</small>
          </article>
        </div>
        <div class="orchestration-meta">
          <div>
            <span>Total Delta</span>
            <strong>{{ sandboxRelationshipDelta >= 0 ? '+' : '' }}{{ sandboxRelationshipDelta }}</strong>
          </div>
          <div>
            <span>Flags</span>
            <strong>{{ sandboxFlags.length }}</strong>
          </div>
          <div>
            <span>Source</span>
            <strong>local sandbox</strong>
          </div>
        </div>
        <div class="plan-list">
          <article
            v-for="[name, value] in sandboxAffinityEntries"
            :key="`sandbox-${name}`"
            class="plan-card"
          >
            <div class="plan-head">
              <strong>{{ name }}</strong>
              <span>{{ value }}</span>
            </div>
          </article>
        </div>
        <div class="token-row">
          <span v-for="flag in sandboxFlags.slice(0, 8)" :key="`sandbox-flag-${flag}`" class="token final">{{ flag }}</span>
        </div>
        <div class="sandbox-actions">
          <button class="sandbox-button primary" type="button" @click="saveSandbox">Save Sandbox Run</button>
          <span class="scene-copy">Persist this branch chain into the session debug shelf for replay.</span>
        </div>
        <p v-if="sandboxStatus" class="hint">{{ sandboxStatus }}</p>
      </div>
      <div v-if="orchestration" class="state-block">
        <h3>Agent Orchestration</h3>
        <div class="orchestration-meta">
          <div>
            <span>Story</span>
            <strong>{{ orchestration.storyTitle }}</strong>
          </div>
          <div>
            <span>Source</span>
            <strong>{{ orchestration.storySourceType }}</strong>
          </div>
          <div>
            <span>Focus</span>
            <strong>{{ orchestration.focusAgentId }}</strong>
          </div>
        </div>
        <div class="orchestration-meta">
          <div>
            <span>Tension</span>
            <strong>{{ orchestration.planner.tensionLabel }}</strong>
          </div>
          <div>
            <span>Pacing</span>
            <strong>{{ orchestration.planner.pacingLabel }}</strong>
          </div>
          <div>
            <span>Verdict</span>
            <strong>{{ orchestration.critic.verdict }}</strong>
          </div>
        </div>
        <p class="plan-copy">{{ orchestration.planner.sceneGoal }}</p>
        <div class="token-row">
          <span v-for="agentId in orchestration.speakingAgentIds" :key="agentId" class="token">{{ agentId }}</span>
        </div>
        <div class="plan-list" v-if="orchestration.aiInvocations.length">
          <article
            v-for="invocation in orchestration.aiInvocations.slice(0, 3)"
            :key="`${invocation.operation}-${invocation.targetId}`"
            class="plan-card"
          >
            <div class="plan-head">
              <strong>{{ invocation.operation }}</strong>
              <span>{{ invocation.latencyMs }}ms</span>
            </div>
            <p>{{ invocation.providerSucceeded ? 'provider' : invocation.fallbackUsed ? 'fallback' : 'local' }} · {{ invocation.targetId || 'scene' }}</p>
            <small>{{ invocation.model || 'no-model' }}{{ invocation.errorMessage ? ` · ${invocation.errorMessage}` : '' }}</small>
          </article>
        </div>
        <div class="plan-list">
          <article v-for="risk in orchestration.planner.risks.slice(0, 2)" :key="risk" class="plan-card">
            <strong>Risk</strong>
            <p>{{ risk }}</p>
          </article>
        </div>
        <div class="plan-list" v-if="orchestration.nextBranchOptions.length">
          <article
            v-for="option in orchestration.nextBranchOptions.slice(0, 3)"
            :key="`${option.label}-${option.targetAgentId}`"
            class="plan-card"
          >
            <div class="plan-head">
              <strong>{{ option.label }}</strong>
              <span>{{ option.risk }}</span>
            </div>
            <p>{{ option.intent }} -> {{ option.targetMood }}</p>
            <small>{{ option.targetAgentId || 'scene' }} · {{ option.source }} · rel {{ option.relationshipDelta >= 0 ? '+' : '' }}{{ option.relationshipDelta }}</small>
            <small>{{ option.consequenceSummary }}</small>
          </article>
        </div>
        <div class="plan-list">
          <article
            v-for="plan in orchestration.plans.slice(0, 3)"
            :key="plan.agentId"
            class="plan-card"
            :class="{ active: plan.shouldSpeak }"
          >
            <div class="plan-head">
              <strong>{{ plan.agentName }}</strong>
              <span>{{ plan.initiativeScore }}</span>
            </div>
            <p>{{ plan.objective }}</p>
            <small>{{ plan.scoreFactors.map((factor) => `${factor.delta >= 0 ? '+' : ''}${factor.delta} ${factor.code}`).join(' · ') }}</small>
          </article>
        </div>
      </div>
    </aside>

    <section class="dialogue-panel">
      <div class="dialogue-feed">
        <article v-for="message in messages" :key="message.id" class="message" :class="message.role">
          <p class="speaker">{{ message.speaker }}</p>
          <p>{{ message.content }}</p>
        </article>
      </div>

      <div class="choice-row">
        <button
          v-for="option in currentBranchOptions"
          :key="option.label"
          class="choice-button"
          type="button"
          :disabled="isSending"
          @mouseenter="selectedBranchLabel = option.label"
          @focus="selectedBranchLabel = option.label"
          @click="emit('send-turn', option.label)"
        >
          <strong>{{ option.label }}</strong>
          <small>{{ option.intent }} · {{ option.risk }} · {{ option.targetMood }}</small>
          <small>{{ option.consequenceSummary }}</small>
          <small>
            {{ option.targetAgentId || 'scene' }} · rel {{ option.relationshipDelta >= 0 ? '+' : '' }}{{ option.relationshipDelta }}
            <template v-if="option.addedFlags.length"> · +{{ option.addedFlags.join(', ') }}</template>
            <template v-if="option.removedFlags.length"> · -{{ option.removedFlags.join(', ') }}</template>
          </small>
        </button>
      </div>

      <form class="composer" @submit.prevent="emit('send-turn')">
        <label class="composer-label" for="playerInput">推进剧情</label>
        <textarea
          id="playerInput"
          :value="playerInput"
          class="composer-input"
          rows="4"
          placeholder="例如：我相信 Lyra，但想先检查灯坛附近有没有异常。"
          @input="emit('update:playerInput', ($event.target as HTMLTextAreaElement).value)"
        />
        <div class="composer-footer">
          <p class="hint">会发送到 <strong>{{ settings.backendUrl }}/game/next</strong></p>
          <button class="primary-button" type="submit" :disabled="isSending">
            {{ isSending ? '发送中...' : '发送' }}
          </button>
        </div>
      </form>
    </section>
  </section>
</template>

<style scoped>
.game-view{display:grid;grid-template-columns:320px minmax(0,1fr);gap:20px}
.scene-panel,.dialogue-panel{border:1px solid rgba(255,255,255,.08);background:rgba(10,14,19,.64);backdrop-filter:blur(18px)}
.scene-panel{display:grid;align-content:start;gap:22px;padding:24px;border-radius:28px}
.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:.24em;text-transform:uppercase;color:#b9a988}
h2,h3,p{margin:0}
h2{font-size:clamp(28px,4vw,40px);line-height:1}
.scene-copy,.hint{color:#cfc7ba;line-height:1.7}
.scene-meta,.affinity-list,.state-block,.composer,.orchestration-meta,.plan-list{display:grid;gap:12px}
.preview-head{display:flex;justify-content:space-between;gap:12px;align-items:center}
.preview-risk{display:inline-flex;padding:6px 10px;border-radius:999px;background:rgba(229,199,138,.12);color:#efd5a1;font-size:12px;text-transform:uppercase;letter-spacing:.12em}
.sandbox-actions{display:flex;flex-wrap:wrap;gap:8px}
.sandbox-button{appearance:none;border:1px solid rgba(255,255,255,.08);cursor:pointer;padding:10px 14px;border-radius:999px;background:rgba(255,255,255,.04);color:#f4ede0;transition:transform 160ms ease,background 160ms ease,opacity 160ms ease}
.sandbox-button.primary{background:rgba(229,199,138,.16);border-color:rgba(229,199,138,.28);color:#f5deb0}
.scene-meta div,.affinity-item,.orchestration-meta div,.plan-head{display:flex;justify-content:space-between;gap:12px;color:#d6cfbf;align-items:center}
.scene-meta span,.affinity-item span,.orchestration-meta span,.plan-head span{color:#9e9a93}
.token-row,.choice-row{display:flex;flex-wrap:wrap;gap:8px}
.token{padding:6px 12px;border-radius:999px;background:rgba(229,199,138,.12);color:#efd5a1;font-size:13px}
.token.muted{background:rgba(255,255,255,.05);color:#8f918e}
.token.added{background:rgba(117,198,122,.14);color:#d7f5d0}
.token.removed{background:rgba(217,94,81,.16);color:#ffd7d2}
.token.final{background:rgba(255,255,255,.05);color:#d8d2c5}
.plan-card{display:grid;gap:6px;padding:12px;border-radius:16px;background:rgba(255,255,255,.04)}
.plan-card.active{border:1px solid rgba(229,199,138,.38);background:rgba(229,199,138,.08)}
.plan-card p,.plan-card small,.plan-copy{margin:0;color:#cfc7ba;line-height:1.5}
.plan-card small{color:#9e9a93}
.dialogue-panel{display:grid;grid-template-rows:1fr auto auto;gap:18px;min-height:70vh;padding:22px;border-radius:32px}
.dialogue-feed{display:grid;gap:14px;overflow:auto;padding-right:4px}
.message{max-width:min(80%,720px);padding:16px 18px;border-radius:20px;line-height:1.7}
.message.assistant,.message.system{background:rgba(255,255,255,.05)}
.message.player{justify-self:end;background:linear-gradient(135deg,#d5b06a 0%,#8f6934 100%);color:#17120d}
.speaker{margin-bottom:6px;font-size:12px;letter-spacing:.14em;text-transform:uppercase;opacity:.72}
.choice-button,.primary-button{appearance:none;border:0;cursor:pointer;transition:transform 160ms ease,background 160ms ease,opacity 160ms ease}
.choice-button{display:grid;gap:4px;padding:12px 16px;border-radius:18px;background:rgba(255,255,255,.05);color:#f4ede0;text-align:left}
.choice-button small{color:#bdb4a4;line-height:1.45}
.composer-label{font-size:13px;letter-spacing:.12em;text-transform:uppercase;color:#bcb09a}
.composer-input{width:100%;resize:vertical;min-height:124px;padding:16px;border:1px solid rgba(255,255,255,.1);border-radius:20px;outline:none;color:#f7f0e2;background:rgba(255,255,255,.04);font:inherit}
.composer-input:focus{border-color:rgba(229,199,138,.54);box-shadow:0 0 0 4px rgba(229,199,138,.08)}
.composer-footer{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
.primary-button{padding:12px 18px;border-radius:999px;background:#e5c78a;color:#11161d}
.choice-button:hover,.primary-button:hover,.sandbox-button:hover{transform:translateY(-1px)}
.choice-button:disabled,.primary-button:disabled,.sandbox-button:disabled{opacity:.5;cursor:not-allowed}
@media (max-width:960px){.game-view{grid-template-columns:1fr}.dialogue-panel{min-height:auto}}
</style>
