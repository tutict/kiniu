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
const isContextOpen = ref(true)

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
  <section class="game-view" :class="{ 'context-collapsed': !isContextOpen }">
    <section class="dialogue-panel">
      <header class="session-bar">
        <div>
          <p class="eyebrow">{{ t('labelCurrentWorkspace') }}</p>
          <h2>{{ sceneLabel }}</h2>
        </div>
        <div class="session-meta">
          <span>{{ worldState.currentNodeId || t('fieldNotEntered') }}</span>
          <span>{{ settings.backendUrl || t('fieldNotConfigured') }}</span>
        </div>
      </header>

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
          <span>{{ option.consequenceSummary }}</span>
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

    <aside class="context-panel" :class="{ collapsed: !isContextOpen }">
      <header class="context-head">
        <div>
          <p class="eyebrow">{{ t('labelContextPanel') }}</p>
          <h3>{{ sceneLabel }}</h3>
        </div>
        <button
          class="context-toggle"
          type="button"
          :aria-expanded="isContextOpen"
          @click="isContextOpen = !isContextOpen"
        >
          {{ isContextOpen ? t('actionCollapse') : t('actionExpand') }}
        </button>
      </header>

      <div v-if="isContextOpen" class="context-body">
        <section class="context-card">
          <div class="meta-grid">
            <div>
              <span>{{ t('labelBackend') }}</span>
              <strong>{{ settings.backendUrl || t('fieldNotConfigured') }}</strong>
            </div>
            <div>
              <span>{{ t('labelFlowNode') }}</span>
              <strong>{{ worldState.currentNodeId || t('fieldNotEntered') }}</strong>
            </div>
          </div>
        </section>

        <section v-if="previewBranch" class="context-card featured">
          <div class="preview-head">
            <h3>{{ t('labelNextPreview') }}</h3>
            <span class="preview-risk">{{ displayCode(previewBranch.risk) }}</span>
          </div>
          <p class="plan-copy strong">{{ previewBranch.label }}</p>
          <p class="plan-copy">{{ previewBranch.consequenceSummary }}</p>
          <div class="meta-grid compact">
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
          <div class="token-row">
            <span v-for="flag in previewFlags.added" :key="`add-${flag}`" class="token added">+ {{ flag }}</span>
            <span v-for="flag in previewFlags.removed" :key="`remove-${flag}`" class="token removed">- {{ flag }}</span>
            <span v-if="!previewFlags.added.length && !previewFlags.removed.length" class="token muted">{{ t('labelNoFlagChanges') }}</span>
          </div>
          <div class="sandbox-actions">
            <button class="secondary-button primary" type="button" @click="addPreviewToSandbox">{{ t('actionAddSandbox') }}</button>
            <button class="secondary-button" type="button" :disabled="!sandboxQueue.length" @click="saveSandbox">{{ t('actionSaveSandbox') }}</button>
          </div>
          <p v-if="sandboxStatus" class="hint">{{ sandboxStatus }}</p>
        </section>

        <details class="context-section">
          <summary>{{ t('labelRuntime') }}</summary>
          <div class="section-body">
            <div class="token-row">
              <span v-for="flag in worldState.flags" :key="flag" class="token">{{ flag }}</span>
              <span v-if="worldState.flags.length === 0" class="token muted">{{ t('fieldNone') }}</span>
            </div>
            <div class="affinity-list">
              <div v-for="[name, value] in affinityEntries" :key="name" class="affinity-item">
                <span>{{ name }}</span>
                <strong>{{ value }}</strong>
              </div>
            </div>
          </div>
        </details>

        <details v-if="sandboxOptions.length" class="context-section" open>
          <summary>{{ t('labelSessionSandbox') }}</summary>
          <div class="section-body">
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
              </article>
            </div>
            <div class="meta-grid compact">
              <div>
                <span>{{ t('labelTotalDelta') }}</span>
                <strong>{{ sandboxRelationshipDelta >= 0 ? '+' : '' }}{{ sandboxRelationshipDelta }}</strong>
              </div>
              <div>
                <span>{{ t('labelFlags') }}</span>
                <strong>{{ sandboxFlags.length }}</strong>
              </div>
            </div>
            <div class="sandbox-actions">
              <button class="secondary-button primary" type="button" @click="saveSandbox">{{ t('actionSaveSandbox') }}</button>
              <button class="secondary-button" type="button" @click="removeLastSandboxStep">{{ t('actionUndoStep') }}</button>
              <button class="secondary-button" type="button" @click="clearSandbox">{{ t('actionClear') }}</button>
            </div>
          </div>
        </details>

        <details v-if="orchestration" class="context-section">
          <summary>{{ t('labelAgentOrchestration') }}</summary>
          <div class="section-body">
            <div class="meta-grid compact">
              <div>
                <span>{{ t('labelStory') }}</span>
                <strong>{{ orchestration.storyTitle }}</strong>
              </div>
              <div>
                <span>{{ t('labelFocus') }}</span>
                <strong>{{ orchestration.focusAgentId }}</strong>
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
                <p>{{ invocation.model || t('fieldNoModel') }}</p>
              </article>
            </div>
          </div>
        </details>
      </div>
    </aside>
  </section>
</template>

<style scoped>
.game-view{display:grid;grid-template-columns:minmax(0,1fr) minmax(286px,340px);gap:10px;align-items:stretch;min-height:0}
.game-view.context-collapsed{grid-template-columns:minmax(0,1fr) 64px}
.dialogue-panel,.context-panel{border:1px solid var(--color-border);background:var(--color-surface-panel-strong);box-shadow:var(--shadow-card);border-radius:var(--radius)}
.dialogue-panel{display:grid;grid-template-rows:auto minmax(240px,1fr) auto auto;gap:10px;min-height:calc(100dvh - 82px);padding:14px}
.context-panel{display:grid;grid-template-rows:auto minmax(0,1fr);align-content:start;gap:12px;max-height:calc(100dvh - 82px);min-width:0;padding:14px;overflow:hidden}
.context-panel.collapsed{padding:10px}
.context-panel.collapsed .context-head{writing-mode:vertical-rl;align-items:center}
.context-panel.collapsed .context-head h3,.context-panel.collapsed .eyebrow{display:none}
.context-body{display:grid;gap:10px;min-height:0;overflow:auto;padding-right:4px;scrollbar-gutter:stable}
.eyebrow{margin:0 0 5px;font-size:11px;letter-spacing:0;color:var(--color-primary-strong);font-weight:800;line-height:1.2}
h2,h3,p{margin:0}
h2{font-size:23px;line-height:1.15;color:var(--color-heading);overflow-wrap:anywhere}
h3{font-size:16px;line-height:1.25;color:var(--color-heading-soft);overflow-wrap:anywhere}
.session-bar,.context-head,.preview-head,.plan-head,.composer-footer{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}
.session-meta{display:flex;justify-content:flex-end;gap:8px;flex-wrap:wrap;max-width:54%;min-width:0}
.session-meta span{padding:5px 8px;border-radius:var(--radius);background:var(--color-token-muted-bg);color:var(--color-muted);font-size:12px;font-weight:700;line-height:1.3;overflow-wrap:anywhere}
.dialogue-feed{display:grid;gap:10px;align-content:start;min-height:0;overflow:auto;padding-right:4px;scrollbar-gutter:stable}
.message{max-width:min(78%,840px);padding:12px 14px;border:1px solid var(--color-border-soft);border-radius:var(--radius);line-height:1.62;background:var(--color-surface);overflow-wrap:anywhere}
.message.assistant,.message.system{background:var(--color-row)}
.message.player{justify-self:end;background:var(--color-primary);border-color:var(--color-primary);color:var(--color-on-primary)}
.speaker{margin-bottom:5px;font-size:11px;letter-spacing:0;opacity:.75;font-weight:800;line-height:1.2}
.choice-row{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}
.choice-button,.primary-button,.secondary-button,.context-toggle{appearance:none;cursor:pointer;transition:background 180ms var(--ease),border-color 180ms var(--ease),opacity 180ms var(--ease),box-shadow 180ms var(--ease),transform 180ms var(--ease)}
.choice-button{display:grid;gap:4px;min-height:82px;padding:10px 12px;border:1px solid var(--color-border-soft);border-radius:var(--radius);background:var(--color-surface);color:var(--color-text);text-align:left;overflow-wrap:anywhere}
.choice-button:hover{border-color:var(--color-primary);background:var(--color-surface-muted);box-shadow:var(--shadow-active);transform:translateY(-1px)}
.choice-button strong{line-height:1.25}
.choice-button small,.choice-button span,.hint,.plan-copy{color:var(--color-muted);font-size:12px;line-height:1.45}
.composer{display:grid;gap:9px}
.composer-label{font-size:12px;letter-spacing:0;color:var(--color-primary-strong);font-weight:800}
.composer-input{width:100%;resize:vertical;min-height:104px;padding:12px 14px;border:1px solid var(--color-border);border-radius:var(--radius);outline:none;color:var(--color-text);background:var(--color-input);font:inherit;line-height:1.6}
.composer-input:focus{border-color:var(--color-primary);box-shadow:0 0 0 4px var(--color-focus-ring)}
.composer-footer{align-items:center;flex-wrap:wrap}
.composer-footer .hint{min-width:0;overflow-wrap:anywhere}
.primary-button,.secondary-button,.context-toggle{min-height:38px;padding:0 13px;border-radius:var(--radius);font-weight:800}
.primary-button{border:0;background:var(--color-accent);color:var(--color-on-accent)}
.secondary-button,.context-toggle{border:1px solid var(--color-border);background:var(--color-input);color:var(--color-primary-strong)}
.secondary-button.primary{background:var(--color-primary);border-color:var(--color-primary);color:var(--color-on-primary)}
.primary-button:hover{background:var(--color-accent-hover);box-shadow:var(--shadow-accent)}
.secondary-button:hover,.context-toggle:hover{border-color:var(--color-border-strong);background:var(--color-hover)}
.context-card,.context-section{border:1px solid var(--color-border-soft);border-radius:var(--radius);background:var(--color-surface);overflow:hidden}
.context-card{display:grid;gap:10px;padding:12px}
.context-card.featured{background:var(--color-bg-soft)}
.context-section summary{cursor:pointer;display:flex;align-items:center;justify-content:space-between;min-height:42px;padding:0 12px;color:var(--color-heading-soft);font-weight:800;list-style:none}
.context-section summary::-webkit-details-marker{display:none}
.context-section summary::after{content:'+';color:var(--color-faint);font-weight:900}
.context-section[open] summary{border-bottom:1px solid var(--color-border-soft)}
.context-section[open] summary::after{content:'-'}
.section-body{display:grid;gap:10px;padding:12px}
.meta-grid,.affinity-list,.plan-list{display:grid;gap:8px}
.meta-grid{grid-template-columns:1fr}
.meta-grid.compact{grid-template-columns:repeat(2,minmax(0,1fr))}
.meta-grid div,.affinity-item{display:flex;justify-content:space-between;gap:10px;align-items:center;min-width:0}
.meta-grid span,.affinity-item span,.plan-head span{color:var(--color-faint);font-size:12px;line-height:1.35}
.meta-grid strong,.affinity-item strong,.plan-head strong{min-width:0;color:var(--color-text);overflow-wrap:anywhere}
.preview-risk{display:inline-flex;padding:4px 8px;border-radius:var(--radius);background:var(--color-warning-bg);color:var(--color-warning-text);font-size:11px;font-weight:800;white-space:nowrap}
.plan-copy.strong{font-size:14px;font-weight:800;color:var(--color-heading-soft)}
.token-row{display:flex;flex-wrap:wrap;gap:7px}
.token{padding:5px 8px;border-radius:var(--radius);background:var(--color-token-bg);color:var(--color-token-text);font-size:12px;font-weight:800;line-height:1.25;overflow-wrap:anywhere}
.token.muted{background:var(--color-token-muted-bg);color:var(--color-faint)}
.token.added{background:var(--color-success-bg);color:var(--color-success-text)}
.token.removed{background:var(--color-danger-bg);color:var(--color-danger-text)}
.plan-card{display:grid;gap:5px;padding:10px;border:1px solid var(--color-border-soft);border-radius:var(--radius);background:var(--color-surface);overflow-wrap:anywhere}
.plan-card p{margin:0;color:var(--color-muted);font-size:12px;line-height:1.45}
.sandbox-actions{display:flex;flex-wrap:wrap;gap:8px}
.choice-button:disabled,.primary-button:disabled,.secondary-button:disabled{opacity:.46;cursor:not-allowed;box-shadow:none;transform:none}
@media (max-width:1180px){.game-view{grid-template-columns:minmax(0,1fr) 300px}}
@media (max-width:960px){
  .game-view,.game-view.context-collapsed{grid-template-columns:1fr;min-height:auto}
  .dialogue-panel,.context-panel{max-height:none;min-height:auto}
  .context-panel.collapsed .context-head{writing-mode:horizontal-tb}
  .context-panel.collapsed .eyebrow,.context-panel.collapsed .context-head h3{display:block}
  .session-bar{display:grid}
  .session-meta{max-width:none;justify-content:flex-start}
  .choice-row{grid-template-columns:1fr}
  .message{max-width:100%}
  .composer-footer .primary-button{width:100%}
}
@media (max-width:520px){.meta-grid.compact{grid-template-columns:1fr}.context-head{align-items:center}.context-toggle{min-width:72px}}
@media (prefers-reduced-motion:reduce){.choice-button,.primary-button,.secondary-button,.context-toggle{transition:none}.choice-button:hover{transform:none}}
</style>
