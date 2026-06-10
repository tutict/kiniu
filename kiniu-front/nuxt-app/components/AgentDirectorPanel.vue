<script setup lang="ts">
import { useUiI18n } from '../i18n'
import type { Agent, AgentCatalogResponse } from '../types/game'

const props = defineProps<{
  draft: AgentCatalogResponse | null
  backendUrl: string
  isLoadingAgents: boolean
  isSavingAgents: boolean
  agentStatus: string
  agentError: string
}>()

const emit = defineEmits<{
  loadAgents: []
  persistAgents: [status?: string]
  publishAgents: []
  exportAgents: []
  resetAgents: []
}>()

const { t } = useUiI18n()
const selectedAgentId = ref('')
const agentSearch = ref('')

const filteredAgents = computed(() => {
  const agents = props.draft?.agents ?? []
  const keyword = agentSearch.value.trim().toLowerCase()
  if (!keyword) return agents
  return agents.filter((agent) => {
    const haystack = [
      agent.id,
      agent.name,
      agent.role,
      agent.summary,
      agent.personality,
      agent.activeScenes.join(' '),
      agent.coreGoals.join(' '),
      agent.hiddenMotives.join(' ')
    ]
      .join(' ')
      .toLowerCase()
    return haystack.includes(keyword)
  })
})

const selectedAgent = computed(() => {
  const agents = props.draft?.agents ?? []
  return agents.find(agent => agent.id === selectedAgentId.value) ?? agents[0] ?? null
})

watch(
  () => props.draft,
  (draft) => {
    if (draft?.agents.length && !selectedAgentId.value) {
      selectedAgentId.value = draft.agents[0].id
    }
  },
  { immediate: true }
)

function normalizeListInput(value: string) {
  return value.split(',').map(item => item.trim()).filter(Boolean)
}

function normalizeMapInput(value: string) {
  const result: Record<string, string> = {}
  normalizeListInput(value).forEach((pair) => {
    const [key, ...rest] = pair.split(':')
    const normalizedKey = key?.trim()
    const normalizedValue = rest.join(':').trim()
    if (normalizedKey && normalizedValue) result[normalizedKey] = normalizedValue
  })
  return result
}

function listToInput(value: string[]) {
  return value.join(', ')
}

function mapToInput(value: Record<string, string>) {
  return Object.entries(value).map(([key, item]) => `${key}:${item}`).join(', ')
}

function updateSelectedAgent(mutator: (agent: Agent) => void, status = t('agentDraftSaved')) {
  if (!props.draft || !selectedAgent.value) return
  const agent = props.draft.agents.find(item => item.id === selectedAgent.value?.id)
  if (!agent) return
  mutator(agent)
  emit('persistAgents', status)
}

function createAgentDraft() {
  if (!props.draft) return
  const nextAgent: Agent = {
    id: `agent-${props.draft.agents.length + 1}`,
    name: t('defaultAgentName'),
    role: 'support',
    summary: t('defaultAgentSummary'),
    personality: t('defaultAgentPersonality'),
    systemPrompt: t('defaultAgentPrompt'),
    activeScenes: ['agent-hub'],
    personalityParameters: { tone: 'measured' },
    coreGoals: [t('defaultAgentGoal')],
    hiddenMotives: [t('defaultAgentMotive')],
    initiative: 5,
    memoryStyle: 'episodic'
  }
  props.draft.agents.unshift(nextAgent)
  selectedAgentId.value = nextAgent.id
  emit('persistAgents', t('statusNewAgent'))
}

function duplicateSelectedAgent() {
  if (!props.draft || !selectedAgent.value) return
  const duplicate = JSON.parse(JSON.stringify(selectedAgent.value)) as Agent
  duplicate.id = `${selectedAgent.value.id}-copy-${Date.now()}`
  duplicate.name = `${selectedAgent.value.name} ${t('copiedSuffix')}`
  props.draft.agents.unshift(duplicate)
  selectedAgentId.value = duplicate.id
  emit('persistAgents', t('statusDuplicatedAgent'))
}

function removeSelectedAgent() {
  if (!props.draft || !selectedAgent.value) return
  if (selectedAgent.value.id === 'narrator') return

  props.draft.agents = props.draft.agents.filter(agent => agent.id !== selectedAgent.value?.id)
  selectedAgentId.value = props.draft.agents[0]?.id ?? ''
  emit('persistAgents', t('statusRemovedAgent'))
}

function updateAgentField(field: keyof Agent, value: string) {
  updateSelectedAgent((agent) => {
    if (field === 'activeScenes' || field === 'coreGoals' || field === 'hiddenMotives') {
      agent[field] = normalizeListInput(value)
      return
    }
    if (field === 'personalityParameters') {
      agent.personalityParameters = normalizeMapInput(value)
      return
    }
    if (field === 'initiative') {
      agent.initiative = Number(value) || 0
      return
    }
    ;(agent[field] as string) = value
  })
}
</script>

<template>
  <section class="agent-stage">
    <div class="stage-head">
      <div>
        <p class="eyebrow">{{ t('navStudio') }}</p>
        <h3>{{ t('agentStageTitle') }}</h3>
      </div>

      <div class="stage-actions">
        <button class="secondary-button" type="button" @click="createAgentDraft">{{ t('actionCreateAgent') }}</button>
        <button class="secondary-button" type="button" :disabled="!selectedAgent" @click="duplicateSelectedAgent">{{ t('actionDuplicateAgent') }}</button>
      </div>
    </div>

    <div class="stage-grid">
      <aside class="cast-column">
        <div class="cast-toolbar">
          <input v-model="agentSearch" class="search-input" type="text" :placeholder="t('labelAgentSearchPlaceholder')">
          <button class="secondary-button" type="button" :disabled="isLoadingAgents" @click="emit('loadAgents')">
            {{ isLoadingAgents ? t('actionLoading') : t('actionLoadBackend') }}
          </button>
        </div>

        <div class="cast-list">
          <button
            v-for="agent in filteredAgents"
            :key="agent.id"
            class="cast-item"
            :class="{ active: selectedAgentId === agent.id }"
            type="button"
            @click="selectedAgentId = agent.id"
          >
            <div class="cast-top">
              <strong>{{ agent.name }}</strong>
              <span>{{ agent.initiative }}</span>
            </div>
            <p>{{ agent.role }}</p>
            <div class="cast-scenes">
              <span v-for="scene in agent.activeScenes.slice(0, 3)" :key="scene">{{ scene }}</span>
            </div>
          </button>
        </div>
      </aside>

      <div v-if="selectedAgent" class="director-main">
        <section class="director-panel hero-panel">
          <div class="panel-head">
            <div>
              <p class="eyebrow">{{ t('labelSelectedAgent') }}</p>
              <h4>{{ selectedAgent.name }}</h4>
            </div>

            <div class="hero-metrics">
              <div>
                <span>{{ t('labelRole') }}</span>
                <strong>{{ selectedAgent.role }}</strong>
              </div>
              <div>
                <span>{{ t('labelInitiative') }}</span>
                <strong>{{ selectedAgent.initiative }}</strong>
              </div>
              <div>
                <span>{{ t('labelMemory') }}</span>
                <strong>{{ selectedAgent.memoryStyle }}</strong>
              </div>
            </div>
          </div>

          <div class="trajectory-strip">
            <div>
              <span>{{ t('labelCoreGoals') }}</span>
              <strong>{{ selectedAgent.coreGoals.length }}</strong>
            </div>
            <div>
              <span>{{ t('labelHiddenMotives') }}</span>
              <strong>{{ selectedAgent.hiddenMotives.length }}</strong>
            </div>
            <div>
              <span>{{ t('labelActiveWorkspaces') }}</span>
              <strong>{{ selectedAgent.activeScenes.length }}</strong>
            </div>
            <div>
              <span>{{ t('labelBackendShort') }}</span>
              <strong>{{ backendUrl || t('fieldNotConfigured') }}</strong>
            </div>
          </div>
        </section>

        <section class="director-panel">
          <div class="panel-head">
            <div>
              <p class="eyebrow">{{ t('labelIdentity') }}</p>
              <h4>{{ t('labelAgentIdentity') }}</h4>
            </div>

            <button
              class="text-button"
              type="button"
              :disabled="selectedAgent.id === 'narrator'"
              @click="removeSelectedAgent"
            >
              {{ t('actionDeleteAgent') }}
            </button>
          </div>

          <div class="editor-fields">
            <label class="field">
              <span>{{ t('labelAgentId') }}</span>
              <input :value="selectedAgent.id" type="text" @input="updateAgentField('id', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>{{ t('labelDisplayName') }}</span>
              <input :value="selectedAgent.name" type="text" @input="updateAgentField('name', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>{{ t('labelRole') }}</span>
              <input :value="selectedAgent.role" type="text" @input="updateAgentField('role', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>{{ t('labelMemoryStyle') }}</span>
              <input :value="selectedAgent.memoryStyle" type="text" @input="updateAgentField('memoryStyle', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field wide">
              <span>{{ t('labelSummary') }}</span>
              <textarea
                class="editor-textarea"
                rows="4"
                :value="selectedAgent.summary"
                @input="updateAgentField('summary', ($event.target as HTMLTextAreaElement).value)"
              />
            </label>
            <label class="field wide">
              <span>{{ t('labelPersonality') }}</span>
              <input
                :value="selectedAgent.personality"
                type="text"
                :placeholder="t('placeholderPersonality')"
                @input="updateAgentField('personality', ($event.target as HTMLInputElement).value)"
              >
            </label>
            <label class="field wide">
              <span>{{ t('labelSystemPrompt') }}</span>
              <textarea
                class="editor-textarea"
                rows="4"
                :value="selectedAgent.systemPrompt"
                @input="updateAgentField('systemPrompt', ($event.target as HTMLTextAreaElement).value)"
              />
            </label>
          </div>
        </section>

        <section class="director-panel dual-panel">
          <div>
            <div class="panel-head tight">
              <div>
                <p class="eyebrow">{{ t('labelGoals') }}</p>
                <h4>{{ t('labelExternalGoals') }}</h4>
              </div>
            </div>
            <label class="field">
              <span>{{ t('labelCoreGoals') }}</span>
              <textarea
                class="editor-textarea"
                rows="6"
                :value="listToInput(selectedAgent.coreGoals)"
                :placeholder="t('labelCommaSeparated')"
                @input="updateAgentField('coreGoals', ($event.target as HTMLTextAreaElement).value)"
              />
            </label>
          </div>

          <div>
            <div class="panel-head tight">
              <div>
                <p class="eyebrow">{{ t('labelMotives') }}</p>
                <h4>{{ t('labelHiddenMotives') }}</h4>
              </div>
            </div>
            <label class="field">
              <span>{{ t('labelHiddenMotives') }}</span>
              <textarea
                class="editor-textarea"
                rows="6"
                :value="listToInput(selectedAgent.hiddenMotives)"
                :placeholder="t('labelCommaSeparated')"
                @input="updateAgentField('hiddenMotives', ($event.target as HTMLTextAreaElement).value)"
              />
            </label>
          </div>
        </section>

        <section class="director-panel orchestration-panel">
          <div class="panel-head">
            <div>
              <p class="eyebrow">{{ t('labelOrchestration') }}</p>
              <h4>{{ t('labelParameters') }}</h4>
            </div>
          </div>

          <div class="editor-fields">
            <label class="field">
              <span>{{ t('labelInitiative') }}</span>
              <input
                :value="selectedAgent.initiative"
                type="number"
                min="0"
                max="20"
                @input="updateAgentField('initiative', ($event.target as HTMLInputElement).value)"
              >
            </label>
            <label class="field wide">
              <span>{{ t('labelActiveWorkspaces') }}</span>
              <input
                :value="listToInput(selectedAgent.activeScenes)"
                type="text"
                :placeholder="t('placeholderAgentScenes')"
                @input="updateAgentField('activeScenes', ($event.target as HTMLInputElement).value)"
              >
            </label>
            <label class="field wide">
              <span>{{ t('labelPersonalityParameters') }}</span>
              <input
                :value="mapToInput(selectedAgent.personalityParameters)"
                type="text"
                :placeholder="t('placeholderAgentParams')"
                @input="updateAgentField('personalityParameters', ($event.target as HTMLInputElement).value)"
              >
            </label>
          </div>

          <div class="scene-ribbon">
            <span v-for="scene in selectedAgent.activeScenes" :key="scene">{{ scene }}</span>
          </div>
        </section>
      </div>

      <div v-else class="director-empty">
        <p>{{ t('emptyNoAgentDraft') }}</p>
      </div>
      <aside class="footer-strip">
      <div class="footer-actions">
        <button class="primary-button" type="button" @click="emit('persistAgents', t('statusManualAgentSaved'))">{{ t('actionSaveAgentDraft') }}</button>
        <button class="primary-button" type="button" :disabled="isSavingAgents || !draft" @click="emit('publishAgents')">
          {{ isSavingAgents ? t('actionSaving') : t('actionSaveAgentsBackend') }}
        </button>
        <button class="secondary-button" type="button" :disabled="!draft" @click="emit('exportAgents')">{{ t('actionExportJson') }}</button>
        <button class="secondary-button" type="button" @click="emit('resetAgents')">{{ t('actionClearAgentDraft') }}</button>
      </div>

      <div class="footer-status">
        <p v-if="agentStatus" class="status success">{{ agentStatus }}</p>
        <p v-if="agentError" class="status error">{{ agentError }}</p>
      </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.agent-stage{display:grid;gap:12px;min-height:calc(100dvh - 154px);padding:14px;border:1px solid var(--color-border);border-radius:var(--radius);background:var(--color-bg-soft)}
.stage-head,.stage-actions,.panel-head,.hero-metrics,.trajectory-strip,.footer-actions{display:flex;gap:12px;align-items:center;justify-content:space-between;flex-wrap:wrap}
.stage-grid{display:grid;grid-template-columns:300px minmax(0,1fr) 280px;gap:10px;min-width:0}
.cast-column,.director-panel{border:1px solid var(--color-border-soft);background:var(--color-surface);border-radius:var(--radius)}
.cast-column{display:grid;align-content:start;gap:12px;max-height:calc(100dvh - 232px);overflow:auto;padding:14px;scrollbar-gutter:stable}
.cast-toolbar,.cast-list,.director-main,.editor-fields,.footer-status{display:grid;gap:12px}
.cast-list{max-height:none;overflow:auto;padding-right:4px}
.cast-item{appearance:none;border:1px solid var(--color-border-soft);border-radius:var(--radius);padding:14px;background:var(--color-surface);color:var(--color-text);text-align:left;cursor:pointer;transition:background 180ms var(--ease),border-color 180ms var(--ease),box-shadow 180ms var(--ease)}
.cast-item.active{border-color:var(--color-primary);background:var(--color-surface-muted);box-shadow:var(--shadow-active)}
.cast-item:hover{border-color:var(--color-primary);background:var(--color-hover)}
.cast-top,.cast-scenes{display:flex;gap:8px;justify-content:space-between;align-items:center;flex-wrap:wrap}
.cast-item p{margin:6px 0 0;color:var(--color-faint);font-size:13px}
.cast-scenes span,.scene-ribbon span{display:inline-flex;padding:5px 9px;border-radius:var(--radius);background:var(--color-token-muted-bg);color:var(--color-faint);font-size:12px;font-weight:700}
.director-main{grid-auto-rows:min-content;max-height:calc(100dvh - 232px);overflow:auto;scrollbar-gutter:stable}
.director-panel{padding:14px}
.hero-panel{background:var(--color-hero-surface)}
.hero-metrics div,.trajectory-strip div{display:grid;gap:4px;min-width:92px}
.hero-metrics span,.trajectory-strip span{color:var(--color-faint);font-size:12px;letter-spacing:0}
.hero-metrics strong,.trajectory-strip strong{font-size:15px;color:var(--color-text)}
.trajectory-strip{padding-top:18px;border-top:1px solid var(--color-border-soft)}
.editor-fields{grid-template-columns:repeat(2,minmax(0,1fr))}
.field{display:grid;gap:10px}
.field.wide{grid-column:1/-1}
.field span{font-size:13px;color:var(--color-text);letter-spacing:0;font-weight:800}
.search-input,.field input,.editor-textarea{width:100%;min-height:44px;padding:10px 14px;border:1px solid var(--color-border);border-radius:var(--radius);outline:none;color:var(--color-text);background:var(--color-input);font:inherit}
.editor-textarea{resize:vertical;min-height:124px}
.search-input:focus,.field input:focus,.editor-textarea:focus{border-color:var(--color-primary);box-shadow:0 0 0 4px var(--color-focus-ring)}
.dual-panel{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}
.orchestration-panel{display:grid;gap:16px}
.scene-ribbon{display:flex;gap:8px;flex-wrap:wrap}
.director-empty{display:grid;place-items:center;min-height:320px;border:1px dashed var(--color-border);border-radius:var(--radius);color:var(--color-faint);background:var(--color-surface)}
.footer-strip{display:grid;align-content:start;gap:12px;max-height:calc(100dvh - 232px);overflow:auto;padding:14px;border:1px solid var(--color-border-soft);border-radius:var(--radius);background:var(--color-surface);scrollbar-gutter:stable}
.footer-actions{display:grid;justify-content:stretch}
.primary-button,.secondary-button,.text-button{appearance:none;border:0;cursor:pointer;transition:background 180ms var(--ease),border-color 180ms var(--ease),box-shadow 180ms var(--ease),opacity 180ms var(--ease)}
.primary-button,.secondary-button{min-height:44px;padding:0 16px;border-radius:var(--radius);font-weight:800}
.primary-button{background:var(--color-accent);color:var(--color-on-accent)}
.secondary-button{border:1px solid var(--color-border);background:var(--color-input);color:var(--color-primary-strong)}
.primary-button:hover{background:var(--color-accent-hover);box-shadow:var(--shadow-accent)}
.secondary-button:hover{border-color:var(--color-primary);background:var(--color-hover)}
.text-button{min-height:44px;padding:0;background:transparent;color:var(--color-danger-action);font-weight:800}
.eyebrow{margin:0 0 6px;font-size:11px;letter-spacing:0;color:var(--color-primary-strong);font-weight:800;line-height:1.2}
h3,h4,p{margin:0}
h3{font-size:24px;line-height:1.15;color:var(--color-heading);overflow-wrap:anywhere}
h4{font-size:18px;line-height:1.2;color:var(--color-heading-soft);overflow-wrap:anywhere}
.status{display:inline-flex;align-items:center;min-height:44px;padding:10px 14px;border-radius:var(--radius);line-height:1.5}
.status.success{color:var(--color-success-text);background:var(--color-success-bg);border:1px solid var(--color-success-border)}
.status.error{color:var(--color-danger-text);background:var(--color-danger-bg);border:1px solid var(--color-danger-border)}
.tight{margin-bottom:12px}
@media (max-width:1280px){.stage-grid{grid-template-columns:280px minmax(0,1fr) 240px}}
@media (max-width:1100px){.agent-stage{min-height:auto}.stage-grid{grid-template-columns:1fr}.cast-column,.director-main,.footer-strip{max-height:none;overflow:visible}.cast-list{max-height:none}}
@media (max-width:860px){.editor-fields,.dual-panel{grid-template-columns:1fr}}
</style>
