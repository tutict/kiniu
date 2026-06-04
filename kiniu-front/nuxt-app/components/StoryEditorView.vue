<script setup lang="ts">
import AgentDirectorPanel from './AgentDirectorPanel.vue'
import SessionDebugPanel from './SessionDebugPanel.vue'
import StoryGeneratorPanel from './StoryGeneratorPanel.vue'
import StoryGraphCanvas from './StoryGraphCanvas.vue'
import { useUiI18n } from '../i18n'
import type {
  AgentCatalogResponse,
  SavedSandboxPlan,
  SessionExportResponse,
  StoryAnalysisResponse,
  StoryCatalogResponse,
  StoryChoiceView,
  StoryGenerationRequest,
  StoryNodeView
} from '../types/game'

const props = defineProps<{
  draft: StoryCatalogResponse | null
  agentDraft: AgentCatalogResponse | null
  sessionExport: SessionExportResponse | null
  sandboxPlans: SavedSandboxPlan[]
  storyAnalysis: StoryAnalysisResponse | null
  currentSessionId: string
  backendUrl: string
  isLoadingStory: boolean
  isSavingStory: boolean
  isLoadingAgents: boolean
  isSavingAgents: boolean
  isLoadingSession: boolean
  isGeneratingStory: boolean
  isValidatingStory: boolean
  storyStatus: string
  storyError: string
  agentStatus: string
  agentError: string
  sessionStatus: string
  sessionError: string
  generatorStatus: string
  generatorError: string
  validationStatus: string
  validationError: string
}>()

const emit = defineEmits<{
  loadStory: []
  persistDraft: [status?: string]
  publishDraft: []
  exportDraft: []
  resetDraft: []
  loadAgents: []
  persistAgents: [status?: string]
  publishAgents: []
  exportAgents: []
  resetAgents: []
  validateStory: []
  generateStory: [request: StoryGenerationRequest]
  loadSession: [sessionId: string]
  exportSession: []
  resetSession: []
  exportSandboxPlans: []
  resetSandboxPlans: []
}>()

const { t } = useUiI18n()
const storySearch = ref('')
const selectedNodeId = ref('')

const allTags = computed(() => {
  const tags = new Set<string>()
  props.draft?.nodes.forEach(node => node.tags.forEach(tag => tags.add(tag)))
  return Array.from(tags)
})

const filteredNodes = computed(() => {
  const nodes = props.draft?.nodes ?? []
  const keyword = storySearch.value.trim().toLowerCase()
  if (!keyword) return nodes
  return nodes.filter((node) => {
    const haystack = [node.id, node.sceneId, node.title, node.speakerId, node.narrative, node.tags.join(' ')]
      .join(' ')
      .toLowerCase()
    return haystack.includes(keyword)
  })
})

const selectedNode = computed(() => {
  const nodes = props.draft?.nodes ?? []
  return nodes.find(node => node.id === selectedNodeId.value) ?? nodes[0] ?? null
})

watch(
  () => props.draft,
  (draft) => {
    if (draft && !selectedNodeId.value) selectedNodeId.value = draft.entryNodeId
  },
  { immediate: true }
)

function normalizeListInput(value: string) {
  return value.split(',').map(item => item.trim()).filter(Boolean)
}

function normalizeAffinityInput(value: string) {
  const result: Record<string, number> = {}
  normalizeListInput(value).forEach((pair) => {
    const [key, rawValue] = pair.split(':').map(item => item.trim())
    const numericValue = Number(rawValue)
    if (key && Number.isFinite(numericValue)) result[key] = numericValue
  })
  return result
}

function listToInput(value: string[]) {
  return value.join(', ')
}

function affinityToInput(value: Record<string, number>) {
  return Object.entries(value).map(([key, amount]) => `${key}:${amount}`).join(', ')
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

function slugifySegment(value: string) {
  const normalized = value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return normalized || 'sandbox'
}

function buildSandboxNarrative(plan: SavedSandboxPlan, stepIndex: number) {
  const step = plan.steps[stepIndex]
  const lines = [
    step.consequenceSummary || t('branchFallbackSummary'),
    `${t('labelIntent')}: ${displayCode(step.intent)}`,
    `${t('labelRisk')}: ${displayCode(step.risk)}`,
    `${t('labelMood')}: ${displayCode(step.targetMood)}`
  ]

  if (step.targetAgentId) {
    lines.push(`${t('labelFocus')}: ${step.targetAgentId}`)
  }
  if (step.addedFlags.length) {
    lines.push(`${t('labelFlagsToAdd')}: ${step.addedFlags.join(', ')}`)
  }
  if (step.removedFlags.length) {
    lines.push(`${t('labelBlockedFlags')}: ${step.removedFlags.join(', ')}`)
  }

  return `${t('sandboxStepNarrative', { title: plan.title, step: stepIndex + 1 })}\n${lines.join('\n')}`
}

function updateSelectedNode(mutator: (node: StoryNodeView) => void, status = t('flowDraftSaved')) {
  if (!props.draft || !selectedNode.value) return
  const node = props.draft.nodes.find(item => item.id === selectedNode.value?.id)
  if (!node) return
  mutator(node)
  emit('persistDraft', status)
}

function createNodeDraft() {
  if (!props.draft) return
  const newNode: StoryNodeView = {
    id: `draft.node.${props.draft.nodes.length + 1}`,
    sceneId: 'agent-hub',
    title: t('defaultFlowNodeTitle'),
    speakerId: 'narrator',
    narrative: t('defaultFlowNodeNarrative'),
    tags: ['draft'],
    enterFlags: [],
    enterAffinityChanges: {},
    choices: []
  }
  props.draft.nodes.unshift(newNode)
  selectedNodeId.value = newNode.id
  emit('persistDraft', t('statusNewFlowNode'))
}

function duplicateSelectedNode() {
  if (!props.draft || !selectedNode.value) return
  const duplicate = JSON.parse(JSON.stringify(selectedNode.value)) as StoryNodeView
  duplicate.id = `${selectedNode.value.id}-copy-${Date.now()}`
  duplicate.title = `${selectedNode.value.title} ${t('copiedSuffix')}`
  duplicate.tags = Array.from(new Set([...duplicate.tags, 'draft']))
  props.draft.nodes.unshift(duplicate)
  selectedNodeId.value = duplicate.id
  emit('persistDraft', t('statusDuplicatedFlowNode'))
}

function addChoiceToSelectedNode() {
  updateSelectedNode((node) => {
    node.choices.push({
      id: `choice-${Date.now()}`,
      label: t('defaultActionLabel'),
      description: t('defaultActionDescription'),
      targetNodeId: node.id,
      requiredFlags: [],
      blockedFlags: [],
      minimumAffinity: {},
      keywords: [],
      flagsToAdd: [],
      affinityChanges: {}
    })
  }, t('statusNewAction'))
}

function removeChoice(choiceId: string) {
  updateSelectedNode((node) => {
    node.choices = node.choices.filter(choice => choice.id !== choiceId)
  }, t('statusRemovedAction'))
}

function updateNodeField(field: keyof StoryNodeView, value: string) {
  updateSelectedNode((node) => {
    if (field === 'tags') {
      node.tags = normalizeListInput(value)
      return
    }
    if (field === 'enterFlags') {
      node.enterFlags = normalizeListInput(value)
      return
    }
    if (field === 'enterAffinityChanges') {
      node.enterAffinityChanges = normalizeAffinityInput(value)
      return
    }
    ;(node[field] as string) = value
  })
}

function updateChoiceField(choiceId: string, field: keyof StoryChoiceView, value: string) {
  updateSelectedNode((node) => {
    const choice = node.choices.find(item => item.id === choiceId)
    if (!choice) return
    if (field === 'requiredFlags' || field === 'blockedFlags' || field === 'keywords') {
      choice[field] = normalizeListInput(value)
      return
    }
    if (field === 'minimumAffinity') {
      choice.minimumAffinity = normalizeAffinityInput(value)
      return
    }
    if (field === 'flagsToAdd') {
      choice.flagsToAdd = normalizeListInput(value)
      return
    }
    if (field === 'affinityChanges') {
      choice.affinityChanges = normalizeAffinityInput(value)
      return
    }
    ;(choice[field] as string) = value
  })
}

function importSandboxPlan(planId: string) {
  if (!props.draft || !selectedNode.value) return

  const plan = props.sandboxPlans.find(item => item.id === planId)
  if (!plan || !plan.steps.length) return

  const timestamp = Date.now()
  const prefix = `${slugifySegment(plan.sceneId || plan.title)}.sandbox.${timestamp}`
  const importedNodes: StoryNodeView[] = plan.steps.map((step, index) => {
    const nodeId = `${prefix}.${index + 1}`
    const nextStep = plan.steps[index + 1]
    const nextNodeId = nextStep ? `${prefix}.${index + 2}` : ''

    return {
      id: nodeId,
      sceneId: plan.sceneId || selectedNode.value?.sceneId || 'sandbox-import',
      title: `${plan.title} / ${index + 1}. ${step.label}`,
      speakerId: step.targetAgentId || 'narrator',
      narrative: buildSandboxNarrative(plan, index),
      tags: ['sandbox-import', slugifySegment(plan.id), slugifySegment(step.intent)],
      enterFlags: [],
      enterAffinityChanges: step.targetAgentId ? { [step.targetAgentId]: step.relationshipDelta } : {},
      choices: nextStep ? [{
        id: `${nodeId}.choice.next`,
        label: nextStep.label,
        description: nextStep.consequenceSummary || t('sandboxContinueDescription', { label: nextStep.label }),
        targetNodeId: nextNodeId,
        requiredFlags: [],
        blockedFlags: [],
        minimumAffinity: {},
        keywords: [nextStep.intent, nextStep.risk, nextStep.targetMood].filter(Boolean),
        flagsToAdd: nextStep.addedFlags,
        affinityChanges: nextStep.targetAgentId ? { [nextStep.targetAgentId]: nextStep.relationshipDelta } : {}
      }] : []
    }
  })

  selectedNode.value.choices.push({
    id: `choice-sandbox-import-${timestamp}`,
    label: plan.steps[0].label,
    description: t('sandboxImportedRoute', { summary: plan.summary || plan.title }),
    targetNodeId: importedNodes[0].id,
    requiredFlags: [],
    blockedFlags: [],
    minimumAffinity: {},
    keywords: ['sandbox-import', slugifySegment(plan.sceneId), slugifySegment(plan.title)],
    flagsToAdd: plan.steps[0].addedFlags,
    affinityChanges: plan.steps[0].targetAgentId ? { [plan.steps[0].targetAgentId]: plan.steps[0].relationshipDelta } : {}
  })

  props.draft.nodes.unshift(...importedNodes)
  selectedNodeId.value = importedNodes[0].id
  emit('persistDraft', t('statusSandboxImported', { count: importedNodes.length }))
}
</script>

<template>
  <section class="editor-view">
    <aside class="editor-sidebar">
      <div class="editor-sidebar-head">
        <div>
          <p class="eyebrow">{{ t('studioTaskFlow') }}</p>
          <h2>{{ t('studioTitle') }}</h2>
        </div>
        <button class="primary-button" type="button" @click="createNodeDraft">{{ t('actionNewFlowNode') }}</button>
      </div>

      <div class="editor-toolbar">
        <input v-model="storySearch" class="search-input" type="text" :placeholder="t('labelFlowSearchPlaceholder')">
        <button class="secondary-button" type="button" :disabled="isLoadingStory" @click="emit('loadStory')">
          {{ isLoadingStory ? t('actionLoading') : t('actionRefreshBackend') }}
        </button>
      </div>

      <div class="tag-cloud">
        <span v-for="tag in allTags" :key="tag" class="token muted" @click="storySearch = tag">{{ tag }}</span>
      </div>

      <div class="node-list">
        <button
          v-for="node in filteredNodes"
          :key="node.id"
          class="node-card"
          :class="{ active: selectedNodeId === node.id }"
          type="button"
          @click="selectedNodeId = node.id"
        >
          <div class="node-card-top">
            <strong>{{ node.title }}</strong>
            <span>{{ node.sceneId }}</span>
          </div>
          <p>{{ node.id }}</p>
          <div class="node-card-meta">
            <span>{{ node.speakerId }}</span>
            <span>{{ t('labelActionsCount', { count: node.choices.length }) }}</span>
          </div>
        </button>
      </div>
    </aside>

    <section class="editor-main">
      <StoryGeneratorPanel
        :backend-url="backendUrl"
        :has-draft="!!draft"
        :analysis="storyAnalysis"
        :is-generating="isGeneratingStory"
        :is-validating="isValidatingStory"
        :generator-status="generatorStatus"
        :generator-error="generatorError"
        :validation-status="validationStatus"
        :validation-error="validationError"
        @validate-draft="emit('validateStory')"
        @generate-draft="emit('generateStory', $event)"
      />

      <div v-if="selectedNode" class="editor-main-grid">
        <section class="editor-panel editor-panel-wide">
          <StoryGraphCanvas
            :nodes="filteredNodes"
            :entry-node-id="draft?.entryNodeId || ''"
            :selected-node-id="selectedNodeId"
            @select-node="selectedNodeId = $event"
          />
        </section>

        <section class="editor-panel">
          <div class="panel-head">
            <div>
              <p class="eyebrow">{{ t('labelFlowNodeInspector') }}</p>
              <h3>{{ selectedNode.title }}</h3>
            </div>
            <div class="inline-actions">
              <button class="secondary-button" type="button" @click="duplicateSelectedNode">{{ t('actionDuplicate') }}</button>
              <button class="secondary-button" type="button" @click="addChoiceToSelectedNode">{{ t('actionAddAction') }}</button>
            </div>
          </div>

          <div class="editor-fields">
            <label class="field wide">
              <span>{{ t('labelNodeTitle') }}</span>
              <input :value="selectedNode.title" type="text" @input="updateNodeField('title', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>{{ t('labelNodeId') }}</span>
              <input :value="selectedNode.id" type="text" @input="updateNodeField('id', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>{{ t('labelWorkspace') }}</span>
              <input :value="selectedNode.sceneId" type="text" @input="updateNodeField('sceneId', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>{{ t('labelSpeaker') }}</span>
              <input :value="selectedNode.speakerId" type="text" @input="updateNodeField('speakerId', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>{{ t('labelTags') }}</span>
              <input :value="listToInput(selectedNode.tags)" type="text" :placeholder="t('placeholderTags')" @input="updateNodeField('tags', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>{{ t('labelEnterFlags') }}</span>
              <input :value="listToInput(selectedNode.enterFlags)" type="text" :placeholder="t('placeholderFlags')" @input="updateNodeField('enterFlags', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>{{ t('labelEnterAffinity') }}</span>
              <input :value="affinityToInput(selectedNode.enterAffinityChanges)" type="text" placeholder="java-rag-interviewer:1" @input="updateNodeField('enterAffinityChanges', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field wide">
              <span>{{ t('labelNarrative') }}</span>
              <textarea class="editor-textarea" rows="6" :value="selectedNode.narrative" @input="updateNodeField('narrative', ($event.target as HTMLTextAreaElement).value)" />
            </label>
          </div>
        </section>

        <section class="editor-panel">
          <div class="panel-head">
            <div>
              <p class="eyebrow">{{ t('labelFlowPreview') }}</p>
              <h3>{{ t('labelOutgoingActions') }}</h3>
            </div>
            <span class="metric-pill">{{ t('labelActionsCount', { count: selectedNode.choices.length }) }}</span>
          </div>

          <div class="flow-list">
            <article v-for="choice in selectedNode.choices" :key="choice.id" class="flow-card">
              <div class="flow-card-top">
                <strong>{{ choice.label }}</strong>
                <button class="text-button" type="button" @click="removeChoice(choice.id)">{{ t('actionRemove') }}</button>
              </div>
              <p>{{ choice.description }}</p>
              <div class="flow-target">
                <span>{{ t('labelTargets') }}</span>
                <strong>{{ choice.targetNodeId }}</strong>
              </div>
            </article>
          </div>
        </section>

        <section class="editor-panel editor-panel-wide">
          <div class="panel-head">
            <div>
              <p class="eyebrow">{{ t('labelActionEditor') }}</p>
              <h3>{{ t('labelConditionsRouting') }}</h3>
            </div>
          </div>

          <div v-for="choice in selectedNode.choices" :key="choice.id" class="choice-editor">
            <div class="editor-fields">
              <label class="field">
                <span>{{ t('labelActionId') }}</span>
                <input :value="choice.id" type="text" @input="updateChoiceField(choice.id, 'id', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field">
                <span>{{ t('labelLabel') }}</span>
                <input :value="choice.label" type="text" @input="updateChoiceField(choice.id, 'label', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field wide">
                <span>{{ t('labelDescription') }}</span>
                <input :value="choice.description" type="text" @input="updateChoiceField(choice.id, 'description', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field">
                <span>{{ t('labelTargetNode') }}</span>
                <input :value="choice.targetNodeId" type="text" @input="updateChoiceField(choice.id, 'targetNodeId', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field">
                <span>{{ t('labelKeywords') }}</span>
                <input :value="listToInput(choice.keywords)" type="text" :placeholder="t('placeholderActionKeywords')" @input="updateChoiceField(choice.id, 'keywords', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field">
                <span>{{ t('labelRequiredFlags') }}</span>
                <input :value="listToInput(choice.requiredFlags)" type="text" :placeholder="t('placeholderFlags')" @input="updateChoiceField(choice.id, 'requiredFlags', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field">
                <span>{{ t('labelBlockedFlags') }}</span>
                <input :value="listToInput(choice.blockedFlags)" type="text" :placeholder="t('placeholderBlockedFlags')" @input="updateChoiceField(choice.id, 'blockedFlags', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field">
                <span>{{ t('labelMinimumAffinity') }}</span>
                <input :value="affinityToInput(choice.minimumAffinity)" type="text" placeholder="java-rag-interviewer:2, narrator:0" @input="updateChoiceField(choice.id, 'minimumAffinity', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field">
                <span>{{ t('labelFlagsToAdd') }}</span>
                <input :value="listToInput(choice.flagsToAdd)" type="text" :placeholder="t('placeholderFlagsToAdd')" @input="updateChoiceField(choice.id, 'flagsToAdd', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field">
                <span>{{ t('labelAffinityChanges') }}</span>
                <input :value="affinityToInput(choice.affinityChanges)" type="text" placeholder="knowledge-curator:1" @input="updateChoiceField(choice.id, 'affinityChanges', ($event.target as HTMLInputElement).value)">
              </label>
            </div>
          </div>
        </section>
      </div>

      <div v-else class="empty-state">
        <p>{{ t('emptyNoFlowNode') }}</p>
      </div>

      <AgentDirectorPanel
        :draft="agentDraft"
        :backend-url="backendUrl"
        :is-loading-agents="isLoadingAgents"
        :is-saving-agents="isSavingAgents"
        :agent-status="agentStatus"
        :agent-error="agentError"
        @load-agents="emit('loadAgents')"
        @persist-agents="emit('persistAgents', $event)"
        @publish-agents="emit('publishAgents')"
        @export-agents="emit('exportAgents')"
        @reset-agents="emit('resetAgents')"
      />

      <SessionDebugPanel
        :export-data="sessionExport"
        :sandbox-plans="sandboxPlans"
        :can-import-sandbox="!!draft"
        :backend-url="backendUrl"
        :current-session-id="currentSessionId"
        :is-loading-session="isLoadingSession"
        :session-status="sessionStatus"
        :session-error="sessionError"
        @load-session="emit('loadSession', $event)"
        @export-session="emit('exportSession')"
        @reset-session="emit('resetSession')"
        @export-sandbox-plans="emit('exportSandboxPlans')"
        @reset-sandbox-plans="emit('resetSandboxPlans')"
        @promote-sandbox="importSandboxPlan"
      />
    </section>

    <aside class="editor-inspector">
      <div class="editor-panel compact">
        <p class="eyebrow">{{ t('labelFlowDraftStatus') }}</p>
        <div class="metric-grid">
          <div><span>{{ t('labelNodes') }}</span><strong>{{ draft?.nodes.length || 0 }}</strong></div>
          <div><span>{{ t('labelEntry') }}</span><strong>{{ draft?.entryNodeId || '-' }}</strong></div>
          <div><span>{{ t('labelTags') }}</span><strong>{{ allTags.length }}</strong></div>
          <div><span>{{ t('labelBackendShort') }}</span><strong>{{ backendUrl || t('fieldNotConfigured') }}</strong></div>
        </div>
      </div>

      <div class="editor-panel compact">
        <p class="eyebrow">{{ t('labelDraftActions') }}</p>
        <div class="stack-actions">
          <button class="primary-button" type="button" @click="emit('persistDraft', t('statusManualFlowSaved'))">{{ t('actionSaveDraft') }}</button>
          <button class="primary-button" type="button" :disabled="isSavingStory || !draft" @click="emit('publishDraft')">
            {{ isSavingStory ? t('actionSaving') : t('actionPublishBackend') }}
          </button>
          <button class="secondary-button" type="button" @click="emit('exportDraft')">{{ t('actionCopyJson') }}</button>
          <button class="secondary-button" type="button" @click="emit('resetDraft')">{{ t('actionClearLocalDraft') }}</button>
        </div>
      </div>

      <div class="editor-panel compact">
        <p class="eyebrow">{{ t('labelContainerNotes') }}</p>
        <div class="notes-list">
          <p>{{ t('noteNodes') }}</p>
          <p>{{ t('noteDraftFirst') }}</p>
          <p>{{ t('noteSandboxImport') }}</p>
        </div>
      </div>

      <p v-if="storyStatus" class="status success">{{ storyStatus }}</p>
      <p v-if="storyError" class="status error">{{ storyError }}</p>
      <p v-if="agentStatus" class="status success">{{ agentStatus }}</p>
      <p v-if="agentError" class="status error">{{ agentError }}</p>
      <p v-if="sessionStatus" class="status success">{{ sessionStatus }}</p>
      <p v-if="sessionError" class="status error">{{ sessionError }}</p>
    </aside>
  </section>
</template>

<style scoped>
.editor-view{display:grid;grid-template-columns:320px minmax(0,1fr) 300px;gap:18px}
.editor-sidebar,.editor-inspector,.editor-panel{border:1px solid var(--color-border);background:var(--color-surface-panel);box-shadow:var(--shadow-card)}
.editor-sidebar,.editor-inspector{display:grid;align-content:start;gap:20px;padding:20px;border-radius:var(--radius)}
.editor-main,.editor-main-grid,.editor-toolbar,.node-list,.editor-fields,.flow-list,.notes-list,.stack-actions{display:grid;gap:12px}
.editor-panel{padding:20px;border-radius:var(--radius)}
.editor-panel.compact{background:var(--color-bg-soft);border-radius:var(--radius)}
.editor-panel-wide{grid-column:1/-1}
.editor-sidebar-head,.panel-head,.inline-actions,.node-card-top,.node-card-meta,.flow-card-top,.flow-target{display:flex;justify-content:space-between;gap:12px;align-items:center;flex-wrap:wrap}
.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:.18em;text-transform:uppercase;color:var(--color-primary-strong);font-weight:800}
h2,h3,p{margin:0}
h2{font-size:clamp(28px,4vw,40px);line-height:1.05;color:var(--color-heading)}
h3{color:var(--color-heading-soft)}
.search-input,.field input,.editor-textarea{width:100%;min-height:44px;padding:10px 14px;border:1px solid var(--color-border);border-radius:var(--radius);outline:none;color:var(--color-text);background:var(--color-input);font:inherit}
.editor-textarea{resize:vertical;min-height:124px}
.search-input:focus,.field input:focus,.editor-textarea:focus{border-color:var(--color-primary);box-shadow:0 0 0 4px var(--color-focus-ring)}
.field{display:grid;gap:10px}
.field.wide{grid-column:1/-1}
.field span{font-size:14px;color:var(--color-text);font-weight:700}
.tag-cloud,.token-row{display:flex;flex-wrap:wrap;gap:8px}
.token{padding:5px 10px;border-radius:var(--radius);background:var(--color-token-muted-bg);color:var(--color-faint);font-size:13px;cursor:pointer}
.node-list{max-height:calc(100vh - 340px);overflow:auto;padding-right:4px}
.node-card{appearance:none;border:1px solid var(--color-border-soft);border-radius:var(--radius);padding:14px;background:var(--color-surface);color:var(--color-text);text-align:left;cursor:pointer;transition:background 180ms var(--ease),border-color 180ms var(--ease),box-shadow 180ms var(--ease)}
.node-card.active{border-color:var(--color-primary);background:var(--color-surface-muted);box-shadow:var(--shadow-active)}
.node-card p,.node-card-meta span{color:var(--color-faint);font-size:13px}
.flow-card,.choice-editor{display:grid;gap:10px;padding:14px;border:1px solid var(--color-border-soft);border-radius:var(--radius);background:var(--color-surface)}
.flow-card p,.notes-list p{color:var(--color-muted);line-height:1.65}
.metric-grid{display:grid;gap:14px}
.metric-grid div{display:flex;justify-content:space-between;gap:12px;color:var(--color-text)}
.metric-grid span,.flow-target span{color:var(--color-faint)}
.metric-pill{padding:5px 10px;border-radius:var(--radius);background:var(--color-warning-bg);color:var(--color-warning-text);font-size:12px;font-weight:800}
.primary-button,.secondary-button,.text-button{appearance:none;border:0;cursor:pointer;transition:background 180ms var(--ease),border-color 180ms var(--ease),box-shadow 180ms var(--ease)}
.primary-button,.secondary-button{min-height:44px;padding:0 16px;border-radius:var(--radius);font-weight:800}
.primary-button{background:var(--color-accent);color:var(--color-on-accent)}
.secondary-button{border:1px solid var(--color-border);background:var(--color-input);color:var(--color-primary-strong)}
.text-button{min-height:44px;padding:0;background:transparent;color:var(--color-danger-action);font-weight:800}
.primary-button:hover{background:var(--color-accent-hover);box-shadow:var(--shadow-accent)}
.secondary-button:hover,.node-card:hover{border-color:var(--color-primary);background:var(--color-hover)}
.empty-state{display:grid;place-items:center;min-height:60vh;border:1px dashed var(--color-border);border-radius:var(--radius);color:var(--color-faint);background:var(--color-surface)}
.status{display:inline-flex;align-items:center;min-height:44px;padding:10px 14px;border-radius:var(--radius);line-height:1.5}
.status.success{color:var(--color-success-text);background:var(--color-success-bg);border:1px solid var(--color-success-border)}
.status.error{color:var(--color-danger-text);background:var(--color-danger-bg);border:1px solid var(--color-danger-border)}
@media (max-width:1200px){.editor-view{grid-template-columns:1fr}.node-list{max-height:none}}
@media (max-width:960px){.editor-fields{grid-template-columns:1fr}}
</style>
