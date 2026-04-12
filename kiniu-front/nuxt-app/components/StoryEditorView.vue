<script setup lang="ts">
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
    step.consequenceSummary || `${step.label} reshapes the scene.`,
    `Intent: ${step.intent}`,
    `Risk: ${step.risk}`,
    `Mood: ${step.targetMood}`
  ]

  if (step.targetAgentId) {
    lines.push(`Focus agent: ${step.targetAgentId}`)
  }
  if (step.addedFlags.length) {
    lines.push(`Adds flags: ${step.addedFlags.join(', ')}`)
  }
  if (step.removedFlags.length) {
    lines.push(`Review removed flags manually: ${step.removedFlags.join(', ')}`)
  }

  return `${plan.title} / Step ${stepIndex + 1}\n${lines.join('\n')}`
}

function updateSelectedNode(mutator: (node: StoryNodeView) => void, status = 'Draft saved locally.') {
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
    sceneId: 'new-scene',
    title: 'New Node',
    speakerId: 'narrator',
    narrative: 'Describe the scene here.',
    tags: ['draft'],
    enterFlags: [],
    enterAffinityChanges: {},
    choices: []
  }
  props.draft.nodes.unshift(newNode)
  selectedNodeId.value = newNode.id
  emit('persistDraft', 'Created a new draft node.')
}

function duplicateSelectedNode() {
  if (!props.draft || !selectedNode.value) return
  const duplicate = JSON.parse(JSON.stringify(selectedNode.value)) as StoryNodeView
  duplicate.id = `${selectedNode.value.id}-copy-${Date.now()}`
  duplicate.title = `${selectedNode.value.title} Copy`
  duplicate.tags = Array.from(new Set([...duplicate.tags, 'draft']))
  props.draft.nodes.unshift(duplicate)
  selectedNodeId.value = duplicate.id
  emit('persistDraft', 'Duplicated the selected node.')
}

function addChoiceToSelectedNode() {
  updateSelectedNode((node) => {
    node.choices.push({
      id: `choice-${Date.now()}`,
      label: 'New Choice',
      description: 'Describe what this choice does.',
      targetNodeId: node.id,
      requiredFlags: [],
      blockedFlags: [],
      minimumAffinity: {},
      keywords: [],
      flagsToAdd: [],
      affinityChanges: {}
    })
  }, 'Added a new branch choice.')
}

function removeChoice(choiceId: string) {
  updateSelectedNode((node) => {
    node.choices = node.choices.filter(choice => choice.id !== choiceId)
  }, 'Removed a branch choice.')
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
        description: nextStep.consequenceSummary || `Continue into ${nextStep.label}.`,
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
    description: `Imported sandbox route: ${plan.summary || plan.title}`,
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
  emit('persistDraft', `Imported sandbox chain as ${importedNodes.length} candidate nodes.`)
}
</script>

<template>
  <section class="editor-view">
    <aside class="editor-sidebar">
      <div class="editor-sidebar-head">
        <div>
          <p class="eyebrow">Story Graph</p>
          <h2>Authoring Desk</h2>
        </div>
        <button class="primary-button" type="button" @click="createNodeDraft">New Node</button>
      </div>

      <div class="editor-toolbar">
        <input v-model="storySearch" class="search-input" type="text" placeholder="Search node / scene / tag">
        <button class="secondary-button" type="button" :disabled="isLoadingStory" @click="emit('loadStory')">
          {{ isLoadingStory ? 'Loading...' : 'Refresh From Backend' }}
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
            <span>{{ node.choices.length }} exits</span>
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
              <p class="eyebrow">Node Inspector</p>
              <h3>{{ selectedNode.title }}</h3>
            </div>
            <div class="inline-actions">
              <button class="secondary-button" type="button" @click="duplicateSelectedNode">Duplicate</button>
              <button class="secondary-button" type="button" @click="addChoiceToSelectedNode">Add Branch</button>
            </div>
          </div>

          <div class="editor-fields">
            <label class="field wide">
              <span>Node Title</span>
              <input :value="selectedNode.title" type="text" @input="updateNodeField('title', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>Node ID</span>
              <input :value="selectedNode.id" type="text" @input="updateNodeField('id', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>Scene</span>
              <input :value="selectedNode.sceneId" type="text" @input="updateNodeField('sceneId', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>Speaker</span>
              <input :value="selectedNode.speakerId" type="text" @input="updateNodeField('speakerId', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>Tags</span>
              <input :value="listToInput(selectedNode.tags)" type="text" placeholder="hub, lore, tactics" @input="updateNodeField('tags', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>Enter Flags</span>
              <input :value="listToInput(selectedNode.enterFlags)" type="text" placeholder="met-lyra, lantern-attuned" @input="updateNodeField('enterFlags', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>Enter Affinity</span>
              <input :value="affinityToInput(selectedNode.enterAffinityChanges)" type="text" placeholder="lyra:1" @input="updateNodeField('enterAffinityChanges', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field wide">
              <span>Narrative</span>
              <textarea class="editor-textarea" rows="6" :value="selectedNode.narrative" @input="updateNodeField('narrative', ($event.target as HTMLTextAreaElement).value)" />
            </label>
          </div>
        </section>

        <section class="editor-panel">
          <div class="panel-head">
            <div>
              <p class="eyebrow">Flow Preview</p>
              <h3>Outgoing Choices</h3>
            </div>
            <span class="metric-pill">{{ selectedNode.choices.length }} exits</span>
          </div>

          <div class="flow-list">
            <article v-for="choice in selectedNode.choices" :key="choice.id" class="flow-card">
              <div class="flow-card-top">
                <strong>{{ choice.label }}</strong>
                <button class="text-button" type="button" @click="removeChoice(choice.id)">Remove</button>
              </div>
              <p>{{ choice.description }}</p>
              <div class="flow-target">
                <span>Targets</span>
                <strong>{{ choice.targetNodeId }}</strong>
              </div>
            </article>
          </div>
        </section>

        <section class="editor-panel editor-panel-wide">
          <div class="panel-head">
            <div>
              <p class="eyebrow">Choice Editor</p>
              <h3>Conditions And Routing</h3>
            </div>
          </div>

          <div v-for="choice in selectedNode.choices" :key="choice.id" class="choice-editor">
            <div class="editor-fields">
              <label class="field">
                <span>Choice ID</span>
                <input :value="choice.id" type="text" @input="updateChoiceField(choice.id, 'id', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field">
                <span>Label</span>
                <input :value="choice.label" type="text" @input="updateChoiceField(choice.id, 'label', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field wide">
                <span>Description</span>
                <input :value="choice.description" type="text" @input="updateChoiceField(choice.id, 'description', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field">
                <span>Target Node</span>
                <input :value="choice.targetNodeId" type="text" @input="updateChoiceField(choice.id, 'targetNodeId', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field">
                <span>Keywords</span>
                <input :value="listToInput(choice.keywords)" type="text" placeholder="trust, help, relic" @input="updateChoiceField(choice.id, 'keywords', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field">
                <span>Required Flags</span>
                <input :value="listToInput(choice.requiredFlags)" type="text" placeholder="lantern-attuned, allied-lyra" @input="updateChoiceField(choice.id, 'requiredFlags', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field">
                <span>Blocked Flags</span>
                <input :value="listToInput(choice.blockedFlags)" type="text" placeholder="failed-ambush" @input="updateChoiceField(choice.id, 'blockedFlags', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field">
                <span>Minimum Affinity</span>
                <input :value="affinityToInput(choice.minimumAffinity)" type="text" placeholder="lyra:2, narrator:0" @input="updateChoiceField(choice.id, 'minimumAffinity', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field">
                <span>Flags To Add</span>
                <input :value="listToInput(choice.flagsToAdd)" type="text" placeholder="trust-offered, hidden-route-known" @input="updateChoiceField(choice.id, 'flagsToAdd', ($event.target as HTMLInputElement).value)">
              </label>
              <label class="field">
                <span>Affinity Changes</span>
                <input :value="affinityToInput(choice.affinityChanges)" type="text" placeholder="lyra:1" @input="updateChoiceField(choice.id, 'affinityChanges', ($event.target as HTMLInputElement).value)">
              </label>
            </div>
          </div>
        </section>
      </div>

      <div v-else class="empty-state">
        <p>No editable story node is loaded yet. Load from backend or create a draft node first.</p>
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
        <p class="eyebrow">Draft Status</p>
        <div class="metric-grid">
          <div><span>Nodes</span><strong>{{ draft?.nodes.length || 0 }}</strong></div>
          <div><span>Entry</span><strong>{{ draft?.entryNodeId || '-' }}</strong></div>
          <div><span>Tags</span><strong>{{ allTags.length }}</strong></div>
          <div><span>Backend</span><strong>{{ backendUrl || 'Not set' }}</strong></div>
        </div>
      </div>

      <div class="editor-panel compact">
        <p class="eyebrow">Draft Actions</p>
        <div class="stack-actions">
          <button class="primary-button" type="button" @click="emit('persistDraft', 'Draft saved manually.')">Save Draft</button>
          <button class="primary-button" type="button" :disabled="isSavingStory || !draft" @click="emit('publishDraft')">
            {{ isSavingStory ? 'Saving...' : 'Publish To Backend' }}
          </button>
          <button class="secondary-button" type="button" @click="emit('exportDraft')">Copy JSON</button>
          <button class="secondary-button" type="button" @click="emit('resetDraft')">Clear Local Draft</button>
        </div>
      </div>

      <div class="editor-panel compact">
        <p class="eyebrow">Authoring Notes</p>
        <div class="notes-list">
          <p>Nodes carry scene text and context. Choices carry routing, flag conditions, and affinity changes.</p>
          <p>The editor is still draft-first. Use it to reshape structure before deciding what should become authored canon.</p>
          <p>Sandbox imports become candidate node chains. Review removed flags and side effects manually before publishing.</p>
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
.editor-sidebar,.editor-inspector,.editor-panel{border:1px solid rgba(255,255,255,.08);background:rgba(10,14,19,.64);backdrop-filter:blur(18px)}
.editor-sidebar,.editor-inspector{display:grid;align-content:start;gap:22px;padding:24px;border-radius:28px}
.editor-main,.editor-main-grid,.editor-toolbar,.node-list,.editor-fields,.flow-list,.notes-list,.stack-actions{display:grid;gap:12px}
.editor-panel{padding:22px;border-radius:28px}
.editor-panel.compact{background:rgba(255,255,255,.04);border-radius:24px}
.editor-panel-wide{grid-column:1/-1}
.editor-sidebar-head,.panel-head,.inline-actions,.node-card-top,.node-card-meta,.flow-card-top,.flow-target{display:flex;justify-content:space-between;gap:12px;align-items:center;flex-wrap:wrap}
.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:.24em;text-transform:uppercase;color:#b9a988}
h2,h3,p{margin:0}
h2{font-size:clamp(28px,4vw,40px);line-height:1}
.search-input,.field input,.editor-textarea{width:100%;padding:14px 16px;border:1px solid rgba(255,255,255,.1);border-radius:16px;outline:none;color:#f7f0e2;background:rgba(255,255,255,.04);font:inherit}
.editor-textarea{resize:vertical;min-height:124px;border-radius:20px}
.search-input:focus,.field input:focus,.editor-textarea:focus{border-color:rgba(229,199,138,.54);box-shadow:0 0 0 4px rgba(229,199,138,.08)}
.field{display:grid;gap:10px}
.field.wide{grid-column:1/-1}
.field span{font-size:14px;color:#f7efde}
.tag-cloud,.token-row{display:flex;flex-wrap:wrap;gap:8px}
.token{padding:6px 12px;border-radius:999px;background:rgba(255,255,255,.05);color:#8f918e;font-size:13px;cursor:pointer}
.node-list{max-height:calc(100vh - 340px);overflow:auto;padding-right:4px}
.node-card{appearance:none;border:1px solid transparent;border-radius:18px;padding:14px;background:rgba(255,255,255,.04);color:#f4ede0;text-align:left;cursor:pointer}
.node-card.active{border-color:rgba(229,199,138,.48);background:rgba(229,199,138,.08)}
.node-card p,.node-card-meta span{color:#9f9d95;font-size:13px}
.flow-card,.choice-editor{display:grid;gap:10px;padding:16px;border-radius:20px;background:rgba(255,255,255,.04)}
.flow-card p,.notes-list p{color:#cfc7ba;line-height:1.7}
.metric-grid{display:grid;gap:14px}
.metric-grid div{display:flex;justify-content:space-between;gap:12px;color:#d6cfbf}
.metric-grid span,.flow-target span{color:#9e9a93}
.metric-pill{padding:6px 12px;border-radius:999px;background:rgba(229,199,138,.12);color:#efd5a1;font-size:12px}
.primary-button,.secondary-button,.text-button{appearance:none;border:0;cursor:pointer;transition:transform 160ms ease,background 160ms ease}
.primary-button,.secondary-button{padding:12px 18px;border-radius:999px}
.primary-button{background:#e5c78a;color:#11161d}
.secondary-button{background:rgba(255,255,255,.06);color:#f4ede0}
.text-button{padding:0;background:transparent;color:#f2b2a9}
.primary-button:hover,.secondary-button:hover,.node-card:hover{transform:translateY(-1px)}
.empty-state{display:grid;place-items:center;min-height:60vh;border:1px dashed rgba(255,255,255,.12);border-radius:28px;color:#a8a094}
.status{display:inline-flex;align-items:center;padding:12px 14px;border-radius:14px;line-height:1.5}
.status.success{color:#dff7d6;background:rgba(117,198,122,.14)}
.status.error{color:#ffd7d2;background:rgba(217,94,81,.14)}
@media (max-width:1200px){.editor-view{grid-template-columns:1fr}.node-list{max-height:none}}
@media (max-width:960px){.editor-fields{grid-template-columns:1fr}}
</style>
