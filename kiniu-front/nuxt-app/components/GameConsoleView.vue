<script setup lang="ts">
import { useUiI18n } from '../i18n'
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

const { t } = useUiI18n()
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
    title: t('sandboxTitle', { scene: props.sceneLabel, count: sandboxOptions.value.length }),
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
  sandboxStatus.value = t('sandboxSavedToDebug', { count: sandboxDraft.value.steps.length })
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
  <section class="game-view">
    <aside class="scene-panel">
      <p class="eyebrow">{{ t('labelCurrentWorkspace') }}</p>
      <h2>{{ sceneLabel }}</h2>
      <p class="scene-copy">
        {{ t('chatSceneCopy') }}
      </p>

      <div class="scene-meta">
        <div>
          <span>{{ t('labelBackend') }}</span>
          <strong>{{ settings.backendUrl || t('fieldNotConfigured') }}</strong>
        </div>
        <div>
          <span>{{ t('labelFlowNode') }}</span>
          <strong>{{ worldState.currentNodeId || t('fieldNotEntered') }}</strong>
        </div>
      </div>

      <div class="state-block">
        <h3>{{ t('labelFlags') }}</h3>
        <div class="token-row">
          <span v-for="flag in worldState.flags" :key="flag" class="token">{{ flag }}</span>
          <span v-if="worldState.flags.length === 0" class="token muted">{{ t('fieldNone') }}</span>
        </div>
      </div>

      <div class="state-block">
        <h3>{{ t('labelAgentAffinity') }}</h3>
        <div class="affinity-list">
          <div v-for="[name, value] in affinityEntries" :key="name" class="affinity-item">
            <span>{{ name }}</span>
            <strong>{{ value }}</strong>
          </div>
        </div>
      </div>
      <div v-if="previewBranch" class="state-block">
        <div class="preview-head">
          <h3>{{ t('labelNextPreview') }}</h3>
          <span class="preview-risk">{{ displayCode(previewBranch.risk) }}</span>
        </div>
        <p class="plan-copy">{{ previewBranch.label }}</p>
        <p class="scene-copy">{{ previewBranch.consequenceSummary }}</p>
        <div class="orchestration-meta">
          <div>
            <span>{{ t('labelIntent') }}</span>
            <strong>{{ displayCode(previewBranch.intent) }}</strong>
          </div>
          <div>
            <span>{{ t('labelMood') }}</span>
            <strong>{{ displayCode(previewBranch.targetMood) }}</strong>
          </div>
          <div>
            <span>{{ t('labelTarget') }}</span>
            <strong>{{ previewBranch.targetAgentId || t('fieldWorkspace') }}</strong>
          </div>
        </div>
        <div v-if="previewAffinity" class="orchestration-meta">
          <div>
            <span>{{ t('labelAffinity') }}</span>
            <strong>{{ previewAffinity.agentId }}</strong>
          </div>
          <div>
            <span>{{ t('labelCurrent') }}</span>
            <strong>{{ previewAffinity.currentValue }}</strong>
          </div>
          <div>
            <span>{{ t('labelPredicted') }}</span>
            <strong>{{ previewAffinity.nextValue }}</strong>
          </div>
        </div>
        <div class="token-row">
          <span v-for="flag in previewFlags.added" :key="`add-${flag}`" class="token added">+ {{ flag }}</span>
          <span v-for="flag in previewFlags.removed" :key="`remove-${flag}`" class="token removed">- {{ flag }}</span>
          <span v-if="!previewFlags.added.length && !previewFlags.removed.length" class="token muted">{{ t('labelNoFlagChanges') }}</span>
        </div>
        <div class="token-row">
          <span v-for="flag in previewFlags.finalFlags.slice(0, 6)" :key="`final-${flag}`" class="token final">{{ flag }}</span>
        </div>
        <div class="sandbox-actions">
          <button class="sandbox-button primary" type="button" @click="addPreviewToSandbox">{{ t('actionAddSandbox') }}</button>
          <button class="sandbox-button" type="button" :disabled="!sandboxQueue.length" @click="removeLastSandboxStep">{{ t('actionUndoStep') }}</button>
          <button class="sandbox-button" type="button" :disabled="!sandboxQueue.length" @click="clearSandbox">{{ t('actionClear') }}</button>
        </div>
      </div>
      <div v-if="sandboxOptions.length" class="state-block">
        <div class="preview-head">
          <h3>{{ t('labelSessionSandbox') }}</h3>
          <span class="preview-risk">{{ t('labelActionsCount', { count: sandboxOptions.length }) }}</span>
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
            <small>{{ option.targetAgentId || t('fieldWorkspace') }} · {{ displayCode(option.intent) }} · {{ displayCode(option.risk) }}</small>
          </article>
        </div>
        <div class="orchestration-meta">
          <div>
            <span>{{ t('labelTotalDelta') }}</span>
            <strong>{{ sandboxRelationshipDelta >= 0 ? '+' : '' }}{{ sandboxRelationshipDelta }}</strong>
          </div>
          <div>
            <span>{{ t('labelFlags') }}</span>
            <strong>{{ sandboxFlags.length }}</strong>
          </div>
          <div>
            <span>{{ t('labelSource') }}</span>
            <strong>{{ t('fieldLocal') }}</strong>
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
          <button class="sandbox-button primary" type="button" @click="saveSandbox">{{ t('actionSaveSandbox') }}</button>
          <span class="scene-copy">{{ t('sandboxSaveHint') }}</span>
        </div>
        <p v-if="sandboxStatus" class="hint">{{ sandboxStatus }}</p>
      </div>
      <div v-if="orchestration" class="state-block">
        <h3>{{ t('labelAgentOrchestration') }}</h3>
        <div class="orchestration-meta">
          <div>
            <span>{{ t('labelStory') }}</span>
            <strong>{{ orchestration.storyTitle }}</strong>
          </div>
          <div>
            <span>{{ t('labelSource') }}</span>
            <strong>{{ orchestration.storySourceType }}</strong>
          </div>
          <div>
            <span>{{ t('labelFocus') }}</span>
            <strong>{{ orchestration.focusAgentId }}</strong>
          </div>
        </div>
        <div class="orchestration-meta">
          <div>
            <span>{{ t('labelTension') }}</span>
            <strong>{{ orchestration.planner.tensionLabel }}</strong>
          </div>
          <div>
            <span>{{ t('labelPacing') }}</span>
            <strong>{{ orchestration.planner.pacingLabel }}</strong>
          </div>
          <div>
            <span>{{ t('labelVerdict') }}</span>
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
            <p>{{ invocation.providerSucceeded ? t('fieldProvider') : invocation.fallbackUsed ? t('fieldFallback') : t('fieldLocal') }} · {{ invocation.targetId || t('fieldWorkspace') }}</p>
            <small>{{ invocation.model || t('fieldNoModel') }}{{ invocation.errorMessage ? ` · ${invocation.errorMessage}` : '' }}</small>
          </article>
        </div>
        <div class="plan-list">
          <article v-for="risk in orchestration.planner.risks.slice(0, 2)" :key="risk" class="plan-card">
            <strong>{{ t('labelRisk') }}</strong>
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
              <span>{{ displayCode(option.risk) }}</span>
            </div>
            <p>{{ displayCode(option.intent) }} -> {{ displayCode(option.targetMood) }}</p>
            <small>{{ option.targetAgentId || t('fieldWorkspace') }} · {{ option.source }} · {{ t('labelRelation') }} {{ option.relationshipDelta >= 0 ? '+' : '' }}{{ option.relationshipDelta }}</small>
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
          <small>{{ displayCode(option.intent) }} · {{ displayCode(option.risk) }} · {{ displayCode(option.targetMood) }}</small>
          <small>{{ option.consequenceSummary }}</small>
          <small>
            {{ option.targetAgentId || t('fieldWorkspace') }} · {{ t('labelRelation') }} {{ option.relationshipDelta >= 0 ? '+' : '' }}{{ option.relationshipDelta }}
            <template v-if="option.addedFlags.length"> · +{{ option.addedFlags.join(', ') }}</template>
            <template v-if="option.removedFlags.length"> · -{{ option.removedFlags.join(', ') }}</template>
          </small>
        </button>
      </div>

      <form class="composer" @submit.prevent="emit('send-turn')">
        <label class="composer-label" for="playerInput">{{ t('labelComposer') }}</label>
        <textarea
          id="playerInput"
          :value="playerInput"
          class="composer-input"
          rows="4"
          :placeholder="t('chatPlaceholder')"
          @input="emit('update:playerInput', ($event.target as HTMLTextAreaElement).value)"
        />
        <div class="composer-footer">
          <p class="hint">{{ t('labelSendEndpoint') }} <strong>{{ settings.backendUrl }}/agent/next</strong></p>
          <button class="primary-button" type="submit" :disabled="isSending">
            {{ isSending ? t('actionSending') : t('actionSend') }}
          </button>
        </div>
      </form>
    </section>
  </section>
</template>

<style scoped>
.game-view{display:grid;grid-template-columns:minmax(300px,360px) minmax(0,1fr);gap:20px;align-items:start}
.scene-panel,.dialogue-panel{border:1px solid var(--color-border);background:var(--color-surface-panel-strong);box-shadow:var(--shadow-card);backdrop-filter:blur(14px)}
.scene-panel{display:grid;align-content:start;gap:20px;max-height:calc(100dvh - 146px);overflow:auto;padding:22px;border-radius:var(--radius);scrollbar-gutter:stable}
.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:.18em;text-transform:uppercase;color:var(--color-primary-strong);font-weight:700}
h2,h3,p{margin:0}
h2{font-size:clamp(28px,4vw,40px);line-height:1.05;color:var(--color-heading)}
h3{font-size:18px;line-height:1.2;color:var(--color-heading-soft)}
.scene-copy,.hint{color:var(--color-muted);line-height:1.65}
.scene-meta,.affinity-list,.state-block,.composer,.orchestration-meta,.plan-list{display:grid;gap:12px}
.state-block{padding-top:18px;border-top:1px solid var(--color-border-soft)}
.preview-head{display:flex;justify-content:space-between;gap:12px;align-items:center}
.preview-risk{display:inline-flex;padding:5px 9px;border-radius:var(--radius);background:var(--color-warning-bg);color:var(--color-warning-text);font-size:12px;text-transform:uppercase;letter-spacing:.08em;font-weight:800}
.sandbox-actions{display:flex;flex-wrap:wrap;gap:8px}
.sandbox-button{appearance:none;border:1px solid var(--color-border);cursor:pointer;min-height:44px;padding:0 14px;border-radius:var(--radius);background:var(--color-input);color:var(--color-primary-strong);font-weight:700;transition:background 180ms var(--ease),border-color 180ms var(--ease),opacity 180ms var(--ease),box-shadow 180ms var(--ease)}
.sandbox-button.primary{background:var(--color-primary);border-color:var(--color-primary);color:var(--color-on-primary)}
.sandbox-button:hover{background:var(--color-hover);border-color:var(--color-border-strong)}
.sandbox-button.primary:hover{background:var(--color-primary-strong);box-shadow:var(--shadow-primary)}
.scene-meta div,.affinity-item,.orchestration-meta div,.plan-head{display:flex;justify-content:space-between;gap:12px;color:var(--color-text);align-items:center}
.scene-meta span,.affinity-item span,.orchestration-meta span,.plan-head span{color:var(--color-faint)}
.scene-meta strong,.affinity-item strong,.orchestration-meta strong,.plan-head strong{min-width:0;overflow-wrap:anywhere}
.token-row{display:flex;flex-wrap:wrap;gap:8px}
.choice-row{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
.token{padding:5px 10px;border-radius:var(--radius);background:var(--color-token-bg);color:var(--color-token-text);font-size:13px;font-weight:700;overflow-wrap:anywhere}
.token.muted{background:var(--color-token-muted-bg);color:var(--color-faint)}
.token.added{background:var(--color-success-bg);color:var(--color-success-text)}
.token.removed{background:var(--color-danger-bg);color:var(--color-danger-text)}
.token.final{background:var(--color-surface-muted);color:var(--color-primary-strong)}
.plan-card{display:grid;gap:6px;padding:12px;border:1px solid var(--color-border-soft);border-radius:var(--radius);background:var(--color-surface);overflow-wrap:anywhere}
.plan-card.active{border-color:var(--color-primary);background:var(--color-surface-muted)}
.plan-card p,.plan-card small,.plan-copy{margin:0;color:var(--color-muted);line-height:1.5}
.plan-card small{color:var(--color-faint)}
.dialogue-panel{display:grid;grid-template-rows:minmax(360px,1fr) auto auto;gap:16px;min-height:calc(100dvh - 146px);padding:20px;border-radius:var(--radius)}
.dialogue-feed{display:grid;gap:12px;align-content:start;overflow:auto;padding-right:4px;scrollbar-gutter:stable}
.message{max-width:min(82%,760px);padding:14px 16px;border:1px solid var(--color-border-soft);border-radius:var(--radius);line-height:1.65;background:var(--color-surface);overflow-wrap:anywhere}
.message.assistant,.message.system{background:var(--color-row)}
.message.player{justify-self:end;background:var(--color-primary);border-color:var(--color-primary);color:var(--color-on-primary)}
.speaker{margin-bottom:6px;font-size:12px;letter-spacing:.12em;text-transform:uppercase;opacity:.75;font-weight:800}
.choice-button,.primary-button{appearance:none;border:0;cursor:pointer;transition:background 180ms var(--ease),border-color 180ms var(--ease),opacity 180ms var(--ease),box-shadow 180ms var(--ease),transform 180ms var(--ease)}
.choice-button{display:grid;gap:4px;min-height:98px;padding:12px 14px;border:1px solid var(--color-border-soft);border-radius:var(--radius);background:var(--color-surface);color:var(--color-text);text-align:left;overflow-wrap:anywhere}
.choice-button:hover{border-color:var(--color-primary);background:var(--color-surface-muted);box-shadow:var(--shadow-active);transform:translateY(-1px)}
.choice-button small{color:var(--color-muted);line-height:1.45}
.composer-label{font-size:13px;letter-spacing:.12em;text-transform:uppercase;color:var(--color-primary-strong);font-weight:800}
.composer-input{width:100%;resize:vertical;min-height:118px;padding:14px 16px;border:1px solid var(--color-border);border-radius:var(--radius);outline:none;color:var(--color-text);background:var(--color-input);font:inherit;line-height:1.6}
.composer-input:focus{border-color:var(--color-primary);box-shadow:0 0 0 4px var(--color-focus-ring)}
.composer-footer{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
.composer-footer .hint{min-width:0;overflow-wrap:anywhere}
.primary-button{min-height:44px;padding:0 18px;border-radius:var(--radius);background:var(--color-accent);color:var(--color-on-accent);font-weight:800}
.primary-button:hover{background:var(--color-accent-hover);box-shadow:var(--shadow-accent)}
.choice-button:disabled,.primary-button:disabled,.sandbox-button:disabled{opacity:.46;cursor:not-allowed;box-shadow:none;transform:none}
@media (max-width:1100px){.game-view{grid-template-columns:300px minmax(0,1fr)}}
@media (max-width:960px){.game-view{grid-template-columns:1fr}.scene-panel,.dialogue-panel{max-height:none;min-height:auto}.choice-row{grid-template-columns:1fr}.message{max-width:100%}.composer-footer .primary-button{width:100%}}
@media (prefers-reduced-motion:reduce){.choice-button,.primary-button,.sandbox-button{transition:none}.choice-button:hover{transform:none}}
</style>
