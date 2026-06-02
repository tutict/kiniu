<script setup lang="ts">
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

function updateSelectedAgent(mutator: (agent: Agent) => void, status = 'Agent 编排草稿已保存到本地。') {
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
    name: 'New Agent',
    role: 'support',
    summary: 'Define what this Agent contributes to the container.',
    personality: 'measured, adaptive',
    systemPrompt: 'Respond as an independent Agent with goals, memory, and clear boundaries.',
    activeScenes: ['agent-hub'],
    personalityParameters: { tone: 'measured' },
    coreGoals: ['Help the user make progress in a specific mode.'],
    hiddenMotives: ['Identify whether this should become a reusable preset.'],
    initiative: 5,
    memoryStyle: 'episodic'
  }
  props.draft.agents.unshift(nextAgent)
  selectedAgentId.value = nextAgent.id
  emit('persistAgents', '已创建新的 Agent 草稿。')
}

function duplicateSelectedAgent() {
  if (!props.draft || !selectedAgent.value) return
  const duplicate = JSON.parse(JSON.stringify(selectedAgent.value)) as Agent
  duplicate.id = `${selectedAgent.value.id}-copy-${Date.now()}`
  duplicate.name = `${selectedAgent.value.name} Copy`
  props.draft.agents.unshift(duplicate)
  selectedAgentId.value = duplicate.id
  emit('persistAgents', '已复制当前 Agent。')
}

function removeSelectedAgent() {
  if (!props.draft || !selectedAgent.value) return
  if (selectedAgent.value.id === 'narrator') return

  props.draft.agents = props.draft.agents.filter(agent => agent.id !== selectedAgent.value?.id)
  selectedAgentId.value = props.draft.agents[0]?.id ?? ''
  emit('persistAgents', '已移除当前 Agent。')
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
        <p class="eyebrow">Agent Studio</p>
        <h3>Agent 编排台</h3>
      </div>

      <div class="stage-actions">
        <button class="secondary-button" type="button" @click="createAgentDraft">新增 Agent</button>
        <button class="secondary-button" type="button" :disabled="!selectedAgent" @click="duplicateSelectedAgent">复制 Agent</button>
      </div>
    </div>

    <div class="stage-grid">
      <aside class="cast-column">
        <div class="cast-toolbar">
          <input v-model="agentSearch" class="search-input" type="text" placeholder="搜索 Agent / workspace / goal">
          <button class="secondary-button" type="button" :disabled="isLoadingAgents" @click="emit('loadAgents')">
            {{ isLoadingAgents ? '加载中...' : '读取后端' }}
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
              <p class="eyebrow">Selected Agent</p>
              <h4>{{ selectedAgent.name }}</h4>
            </div>

            <div class="hero-metrics">
              <div>
                <span>Role</span>
                <strong>{{ selectedAgent.role }}</strong>
              </div>
              <div>
                <span>Initiative</span>
                <strong>{{ selectedAgent.initiative }}</strong>
              </div>
              <div>
                <span>Memory</span>
                <strong>{{ selectedAgent.memoryStyle }}</strong>
              </div>
            </div>
          </div>

          <div class="trajectory-strip">
            <div>
              <span>Core Goals</span>
              <strong>{{ selectedAgent.coreGoals.length }}</strong>
            </div>
            <div>
              <span>Hidden Motives</span>
              <strong>{{ selectedAgent.hiddenMotives.length }}</strong>
            </div>
            <div>
              <span>Active Workspaces</span>
              <strong>{{ selectedAgent.activeScenes.length }}</strong>
            </div>
            <div>
              <span>Backend</span>
              <strong>{{ backendUrl || '未配置' }}</strong>
            </div>
          </div>
        </section>

        <section class="director-panel">
          <div class="panel-head">
            <div>
              <p class="eyebrow">Identity</p>
              <h4>Agent 定位</h4>
            </div>

            <button
              class="text-button"
              type="button"
              :disabled="selectedAgent.id === 'narrator'"
              @click="removeSelectedAgent"
            >
              删除 Agent
            </button>
          </div>

          <div class="editor-fields">
            <label class="field">
              <span>Agent ID</span>
              <input :value="selectedAgent.id" type="text" @input="updateAgentField('id', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>显示名</span>
              <input :value="selectedAgent.name" type="text" @input="updateAgentField('name', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>Role</span>
              <input :value="selectedAgent.role" type="text" @input="updateAgentField('role', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field">
              <span>Memory Style</span>
              <input :value="selectedAgent.memoryStyle" type="text" @input="updateAgentField('memoryStyle', ($event.target as HTMLInputElement).value)">
            </label>
            <label class="field wide">
              <span>Summary</span>
              <textarea
                class="editor-textarea"
                rows="4"
                :value="selectedAgent.summary"
                @input="updateAgentField('summary', ($event.target as HTMLTextAreaElement).value)"
              />
            </label>
            <label class="field wide">
              <span>Personality</span>
              <input
                :value="selectedAgent.personality"
                type="text"
                placeholder="playful, sharp, guarded"
                @input="updateAgentField('personality', ($event.target as HTMLInputElement).value)"
              >
            </label>
            <label class="field wide">
              <span>System Prompt</span>
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
                <p class="eyebrow">Goals</p>
                <h4>外显目标</h4>
              </div>
            </div>
            <label class="field">
              <span>Core Goals</span>
              <textarea
                class="editor-textarea"
                rows="6"
                :value="listToInput(selectedAgent.coreGoals)"
                placeholder="每项用逗号分隔"
                @input="updateAgentField('coreGoals', ($event.target as HTMLTextAreaElement).value)"
              />
            </label>
          </div>

          <div>
            <div class="panel-head tight">
              <div>
                <p class="eyebrow">Motives</p>
                <h4>隐藏动机</h4>
              </div>
            </div>
            <label class="field">
              <span>Hidden Motives</span>
              <textarea
                class="editor-textarea"
                rows="6"
                :value="listToInput(selectedAgent.hiddenMotives)"
                placeholder="每项用逗号分隔"
                @input="updateAgentField('hiddenMotives', ($event.target as HTMLTextAreaElement).value)"
              />
            </label>
          </div>
        </section>

        <section class="director-panel orchestration-panel">
          <div class="panel-head">
            <div>
              <p class="eyebrow">Orchestration</p>
              <h4>出场与参数</h4>
            </div>
          </div>

          <div class="editor-fields">
            <label class="field">
              <span>Initiative</span>
              <input
                :value="selectedAgent.initiative"
                type="number"
                min="0"
                max="20"
                @input="updateAgentField('initiative', ($event.target as HTMLInputElement).value)"
              >
            </label>
            <label class="field wide">
              <span>Active Workspaces</span>
              <input
                :value="listToInput(selectedAgent.activeScenes)"
                type="text"
                placeholder="agent-hub, interview-java-rag"
                @input="updateAgentField('activeScenes', ($event.target as HTMLInputElement).value)"
              >
            </label>
            <label class="field wide">
              <span>Personality Parameters</span>
              <input
                :value="mapToInput(selectedAgent.personalityParameters)"
                type="text"
                placeholder="tone:measured, trust-axis:slow-burn"
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
        <p>当前没有可编辑的 Agent 草稿。先从后端读取，或新建一个 Agent。</p>
      </div>
    </div>

    <div class="footer-strip">
      <div class="footer-actions">
        <button class="primary-button" type="button" @click="emit('persistAgents', 'Agent 草稿已手动保存。')">保存草稿</button>
        <button class="primary-button" type="button" :disabled="isSavingAgents || !draft" @click="emit('publishAgents')">
          {{ isSavingAgents ? '保存中...' : '保存到后端' }}
        </button>
        <button class="secondary-button" type="button" :disabled="!draft" @click="emit('exportAgents')">导出 JSON</button>
        <button class="secondary-button" type="button" @click="emit('resetAgents')">清空本地草稿</button>
      </div>

      <div class="footer-status">
        <p v-if="agentStatus" class="status success">{{ agentStatus }}</p>
        <p v-if="agentError" class="status error">{{ agentError }}</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.agent-stage{display:grid;gap:18px;padding:20px;border:1px solid var(--color-border);border-radius:var(--radius);background:#f7fffd}
.stage-head,.stage-actions,.panel-head,.hero-metrics,.trajectory-strip,.footer-actions{display:flex;gap:12px;align-items:center;justify-content:space-between;flex-wrap:wrap}
.stage-grid{display:grid;grid-template-columns:300px minmax(0,1fr);gap:18px}
.cast-column,.director-panel{border:1px solid #d7eeea;background:#fff;border-radius:var(--radius)}
.cast-column{display:grid;align-content:start;gap:14px;padding:18px}
.cast-toolbar,.cast-list,.director-main,.editor-fields,.footer-status{display:grid;gap:12px}
.cast-list{max-height:740px;overflow:auto;padding-right:4px}
.cast-item{appearance:none;border:1px solid #d7eeea;border-radius:var(--radius);padding:14px;background:#fff;color:var(--color-text);text-align:left;cursor:pointer;transition:background 180ms var(--ease),border-color 180ms var(--ease),box-shadow 180ms var(--ease)}
.cast-item.active{border-color:var(--color-primary);background:#ecfdf5;box-shadow:0 8px 18px rgba(13,148,136,.1)}
.cast-item:hover{border-color:var(--color-primary);background:#ecfdf5}
.cast-top,.cast-scenes{display:flex;gap:8px;justify-content:space-between;align-items:center;flex-wrap:wrap}
.cast-item p{margin:6px 0 0;color:var(--color-faint);font-size:13px}
.cast-scenes span,.scene-ribbon span{display:inline-flex;padding:5px 9px;border-radius:var(--radius);background:#eef6f4;color:var(--color-faint);font-size:12px;font-weight:700}
.director-main{grid-auto-rows:min-content}
.director-panel{padding:20px}
.hero-panel{background:linear-gradient(135deg,#ecfdf5,#fff)}
.hero-metrics div,.trajectory-strip div{display:grid;gap:4px;min-width:92px}
.hero-metrics span,.trajectory-strip span{color:var(--color-faint);font-size:12px;text-transform:uppercase;letter-spacing:.1em}
.hero-metrics strong,.trajectory-strip strong{font-size:15px;color:var(--color-text)}
.trajectory-strip{padding-top:18px;border-top:1px solid #d7eeea}
.editor-fields{grid-template-columns:repeat(2,minmax(0,1fr))}
.field{display:grid;gap:10px}
.field.wide{grid-column:1/-1}
.field span{font-size:13px;color:var(--color-text);letter-spacing:.08em;text-transform:uppercase;font-weight:800}
.search-input,.field input,.editor-textarea{width:100%;min-height:44px;padding:10px 14px;border:1px solid var(--color-border);border-radius:var(--radius);outline:none;color:var(--color-text);background:#fff;font:inherit}
.editor-textarea{resize:vertical;min-height:124px}
.search-input:focus,.field input:focus,.editor-textarea:focus{border-color:var(--color-primary);box-shadow:0 0 0 4px rgba(13,148,136,.12)}
.dual-panel{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}
.orchestration-panel{display:grid;gap:16px}
.scene-ribbon{display:flex;gap:8px;flex-wrap:wrap}
.director-empty{display:grid;place-items:center;min-height:320px;border:1px dashed var(--color-border);border-radius:var(--radius);color:var(--color-faint);background:#fff}
.footer-strip{display:grid;gap:12px;padding-top:6px}
.primary-button,.secondary-button,.text-button{appearance:none;border:0;cursor:pointer;transition:background 180ms var(--ease),border-color 180ms var(--ease),box-shadow 180ms var(--ease),opacity 180ms var(--ease)}
.primary-button,.secondary-button{min-height:44px;padding:0 16px;border-radius:var(--radius);font-weight:800}
.primary-button{background:var(--color-accent);color:#fff}
.secondary-button{border:1px solid var(--color-border);background:#fff;color:var(--color-primary-strong)}
.primary-button:hover{background:#c2410c;box-shadow:0 10px 22px rgba(234,88,12,.18)}
.secondary-button:hover{border-color:var(--color-primary);background:#ecfdf5}
.text-button{min-height:44px;padding:0;background:transparent;color:#b91c1c;font-weight:800}
.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:.18em;text-transform:uppercase;color:var(--color-primary-strong);font-weight:800}
h3,h4,p{margin:0}
h3{font-size:clamp(28px,4vw,38px);line-height:1.05;color:#102f2d}
h4{font-size:20px;line-height:1.1;color:#173f3b}
.status{display:inline-flex;align-items:center;min-height:44px;padding:10px 14px;border-radius:var(--radius);line-height:1.5}
.status.success{color:#166534;background:#dcfce7;border:1px solid #bbf7d0}
.status.error{color:#991b1b;background:#fee2e2;border:1px solid #fecaca}
.tight{margin-bottom:12px}
@media (max-width:1200px){.stage-grid{grid-template-columns:1fr}.cast-list{max-height:none}}
@media (max-width:860px){.editor-fields,.dual-panel{grid-template-columns:1fr}}
</style>
