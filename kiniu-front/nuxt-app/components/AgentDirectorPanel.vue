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
    summary: 'Define what this character contributes to the branch structure.',
    personality: 'measured, adaptive',
    systemPrompt: 'Respond as an independent AVG character with goals and memory.',
    activeScenes: ['opening'],
    personalityParameters: { tone: 'measured' },
    coreGoals: ['Open a new branch for the player.'],
    hiddenMotives: ['Keep one private agenda concealed.'],
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
        <p class="eyebrow">Agent Stage</p>
        <h3>角色编排台</h3>
      </div>

      <div class="stage-actions">
        <button class="secondary-button" type="button" @click="createAgentDraft">新增 Agent</button>
        <button class="secondary-button" type="button" :disabled="!selectedAgent" @click="duplicateSelectedAgent">复制 Agent</button>
      </div>
    </div>

    <div class="stage-grid">
      <aside class="cast-column">
        <div class="cast-toolbar">
          <input v-model="agentSearch" class="search-input" type="text" placeholder="搜索角色 / scene / goal">
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
              <span>Active Scenes</span>
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
              <h4>角色定位</h4>
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
              <span>Active Scenes</span>
              <input
                :value="listToInput(selectedAgent.activeScenes)"
                type="text"
                placeholder="opening, whispering-grove"
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
        <p>当前没有可编辑的 Agent 草稿。先从后端读取，或新建一个角色。</p>
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
.agent-stage{display:grid;gap:18px;padding:28px;border:1px solid rgba(255,255,255,.08);border-radius:30px;background:linear-gradient(180deg,rgba(9,13,19,.82) 0%,rgba(15,20,27,.68) 100%)}
.stage-head,.stage-actions,.panel-head,.hero-metrics,.trajectory-strip,.footer-actions{display:flex;gap:12px;align-items:center;justify-content:space-between;flex-wrap:wrap}
.stage-grid{display:grid;grid-template-columns:300px minmax(0,1fr);gap:18px}
.cast-column,.director-panel{border:1px solid rgba(255,255,255,.08);background:rgba(255,255,255,.03);border-radius:24px}
.cast-column{display:grid;align-content:start;gap:14px;padding:18px}
.cast-toolbar,.cast-list,.director-main,.editor-fields,.footer-status{display:grid;gap:12px}
.cast-list{max-height:740px;overflow:auto;padding-right:4px}
.cast-item{appearance:none;border:1px solid transparent;border-radius:18px;padding:14px;background:rgba(255,255,255,.04);color:#f4ede0;text-align:left;cursor:pointer;transition:transform 160ms ease,border-color 160ms ease,background 160ms ease}
.cast-item.active{border-color:rgba(229,199,138,.48);background:rgba(229,199,138,.08)}
.cast-item:hover,.primary-button:hover,.secondary-button:hover{transform:translateY(-1px)}
.cast-top,.cast-scenes{display:flex;gap:8px;justify-content:space-between;align-items:center;flex-wrap:wrap}
.cast-item p{margin:6px 0 0;color:#a7a093;font-size:13px}
.cast-scenes span,.scene-ribbon span{display:inline-flex;padding:6px 10px;border-radius:999px;background:rgba(255,255,255,.05);color:#d6cfbf;font-size:12px}
.director-main{grid-auto-rows:min-content}
.director-panel{padding:20px}
.hero-panel{background:
  radial-gradient(circle at top right,rgba(229,199,138,.16),transparent 34%),
  linear-gradient(135deg,rgba(255,255,255,.05),rgba(255,255,255,.02))}
.hero-metrics div,.trajectory-strip div{display:grid;gap:4px;min-width:92px}
.hero-metrics span,.trajectory-strip span{color:#9e9a93;font-size:12px;text-transform:uppercase;letter-spacing:.12em}
.hero-metrics strong,.trajectory-strip strong{font-size:15px;color:#f6efdf}
.trajectory-strip{padding-top:18px;border-top:1px solid rgba(255,255,255,.08)}
.editor-fields{grid-template-columns:repeat(2,minmax(0,1fr))}
.field{display:grid;gap:10px}
.field.wide{grid-column:1/-1}
.field span{font-size:13px;color:#f7efde;letter-spacing:.08em;text-transform:uppercase}
.search-input,.field input,.editor-textarea{width:100%;padding:14px 16px;border:1px solid rgba(255,255,255,.1);border-radius:16px;outline:none;color:#f7f0e2;background:rgba(255,255,255,.04);font:inherit}
.editor-textarea{resize:vertical;min-height:124px}
.search-input:focus,.field input:focus,.editor-textarea:focus{border-color:rgba(229,199,138,.54);box-shadow:0 0 0 4px rgba(229,199,138,.08)}
.dual-panel{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}
.orchestration-panel{display:grid;gap:16px}
.scene-ribbon{display:flex;gap:8px;flex-wrap:wrap}
.director-empty{display:grid;place-items:center;min-height:320px;border:1px dashed rgba(255,255,255,.12);border-radius:24px;color:#a8a094}
.footer-strip{display:grid;gap:12px;padding-top:6px}
.primary-button,.secondary-button,.text-button{appearance:none;border:0;cursor:pointer;transition:transform 160ms ease,background 160ms ease,opacity 160ms ease}
.primary-button,.secondary-button{padding:12px 18px;border-radius:999px}
.primary-button{background:#e5c78a;color:#11161d}
.secondary-button{background:rgba(255,255,255,.06);color:#f4ede0}
.text-button{padding:0;background:transparent;color:#f1b6ae}
.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:.24em;text-transform:uppercase;color:#b9a988}
h3,h4,p{margin:0}
h3{font-size:clamp(28px,4vw,38px);line-height:1}
h4{font-size:20px;line-height:1.1}
.status{display:inline-flex;align-items:center;padding:12px 14px;border-radius:14px;line-height:1.5}
.status.success{color:#dff7d6;background:rgba(117,198,122,.14)}
.status.error{color:#ffd7d2;background:rgba(217,94,81,.14)}
.tight{margin-bottom:12px}
@media (max-width:1200px){.stage-grid{grid-template-columns:1fr}.cast-list{max-height:none}}
@media (max-width:860px){.editor-fields,.dual-panel{grid-template-columns:1fr}}
</style>
