<script setup lang="ts">
import type { StoryAnalysisResponse, StoryGenerationRequest } from '../types/game'

const props = defineProps<{
  backendUrl: string
  hasDraft: boolean
  analysis: StoryAnalysisResponse | null
  isGenerating: boolean
  isValidating: boolean
  generatorStatus: string
  generatorError: string
  validationStatus: string
  validationError: string
}>()

const emit = defineEmits<{
  validateDraft: []
  generateDraft: [payload: StoryGenerationRequest]
}>()

const form = reactive<StoryGenerationRequest>({
  premise: 'A witness-bound relic rewrites the city around whoever frames its story first.',
  setting: 'storm-battered observatory district',
  tone: 'tense mystery',
  chapterGoal: 'secure control of the next lead before dawn',
  protagonistName: 'Mira',
  companionName: 'Lyra',
  rivalName: 'Rowan'
})

const issueGroups = computed(() => {
  const issues = props.analysis?.issues ?? []
  return {
    errors: issues.filter(issue => issue.severity === 'error'),
    warnings: issues.filter(issue => issue.severity === 'warning')
  }
})

function submitGeneration() {
  emit('generateDraft', { ...form })
}

function issueLocation(issue: { nodeId?: string | null; choiceId?: string | null }) {
  return [issue.nodeId, issue.choiceId].filter(Boolean).join(' / ') || 'catalog'
}
</script>

<template>
  <section class="generator-stage">
    <div class="panel-head">
      <div>
        <p class="eyebrow">Generator</p>
        <h3>Agent Story Generator</h3>
      </div>

      <div class="panel-actions">
        <button class="secondary-button" type="button" :disabled="!hasDraft || isValidating" @click="emit('validateDraft')">
          {{ isValidating ? 'Validating...' : 'Validate Draft' }}
        </button>
        <button class="primary-button" type="button" :disabled="isGenerating" @click="submitGeneration">
          {{ isGenerating ? 'Generating...' : 'Generate Starter Graph' }}
        </button>
      </div>
    </div>

    <div class="generator-grid">
      <div class="generator-panel">
        <div class="field-grid">
          <label class="field wide">
            <span>Premise</span>
            <textarea v-model="form.premise" class="editor-textarea" rows="4" />
          </label>
          <label class="field">
            <span>Setting</span>
            <input v-model="form.setting" type="text">
          </label>
          <label class="field">
            <span>Tone</span>
            <input v-model="form.tone" type="text">
          </label>
          <label class="field wide">
            <span>Chapter Goal</span>
            <input v-model="form.chapterGoal" type="text">
          </label>
          <label class="field">
            <span>Protagonist</span>
            <input v-model="form.protagonistName" type="text">
          </label>
          <label class="field">
            <span>Companion</span>
            <input v-model="form.companionName" type="text">
          </label>
          <label class="field">
            <span>Rival</span>
            <input v-model="form.rivalName" type="text">
          </label>
        </div>

        <div class="status-column">
          <p v-if="generatorStatus" class="status success">{{ generatorStatus }}</p>
          <p v-if="generatorError" class="status error">{{ generatorError }}</p>
          <p v-if="validationStatus" class="status success">{{ validationStatus }}</p>
          <p v-if="validationError" class="status error">{{ validationError }}</p>
        </div>
      </div>

      <div class="analysis-panel">
        <div class="metric-grid" v-if="analysis">
          <div><span>Nodes</span><strong>{{ analysis.totalNodes }}</strong></div>
          <div><span>Choices</span><strong>{{ analysis.totalChoices }}</strong></div>
          <div><span>Reachable</span><strong>{{ analysis.reachableNodeIds.length }}</strong></div>
          <div><span>Endings</span><strong>{{ analysis.endingNodeIds.length }}</strong></div>
          <div><span>Errors</span><strong>{{ analysis.errorCount }}</strong></div>
          <div><span>Warnings</span><strong>{{ analysis.warningCount }}</strong></div>
        </div>

        <div v-if="analysis" class="issue-columns">
          <div class="issue-group">
            <div class="group-head">
              <h4>Errors</h4>
              <span>{{ issueGroups.errors.length }}</span>
            </div>
            <div class="issue-list">
              <article v-for="issue in issueGroups.errors" :key="`${issue.code}-${issueLocation(issue)}`" class="issue-card error">
                <strong>{{ issue.code }}</strong>
                <p>{{ issue.message }}</p>
                <small>{{ issueLocation(issue) }}</small>
              </article>
              <p v-if="issueGroups.errors.length === 0" class="empty-copy">No blocking compile errors.</p>
            </div>
          </div>

          <div class="issue-group">
            <div class="group-head">
              <h4>Warnings</h4>
              <span>{{ issueGroups.warnings.length }}</span>
            </div>
            <div class="issue-list">
              <article v-for="issue in issueGroups.warnings" :key="`${issue.code}-${issueLocation(issue)}`" class="issue-card warning">
                <strong>{{ issue.code }}</strong>
                <p>{{ issue.message }}</p>
                <small>{{ issueLocation(issue) }}</small>
              </article>
              <p v-if="issueGroups.warnings.length === 0" class="empty-copy">No structural warnings.</p>
            </div>
          </div>
        </div>

        <p v-else class="empty-copy">
          Run validation on the current draft or generate a new starter chapter from a premise.
        </p>
      </div>
    </div>

    <p class="panel-note">
      Backend endpoint: <strong>{{ backendUrl || 'not configured' }}</strong>
    </p>
  </section>
</template>

<style scoped>
.generator-stage{display:grid;gap:18px;padding:28px;border:1px solid rgba(255,255,255,.08);border-radius:30px;background:linear-gradient(180deg,rgba(13,17,24,.84) 0%,rgba(10,14,20,.66) 100%)}
.panel-head,.panel-actions,.group-head{display:flex;justify-content:space-between;gap:12px;align-items:center;flex-wrap:wrap}
.generator-grid{display:grid;grid-template-columns:minmax(0,1.1fr) minmax(0,.9fr);gap:18px}
.generator-panel,.analysis-panel{display:grid;gap:16px;padding:20px;border:1px solid rgba(255,255,255,.08);border-radius:24px;background:rgba(255,255,255,.03)}
.field-grid,.status-column,.issue-columns,.issue-list{display:grid;gap:12px}
.field-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
.field{display:grid;gap:10px}
.field.wide{grid-column:1/-1}
.field span,.metric-grid span{font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:#b9a988}
.field input,.editor-textarea{width:100%;padding:14px 16px;border:1px solid rgba(255,255,255,.1);border-radius:16px;outline:none;color:#f7f0e2;background:rgba(255,255,255,.04);font:inherit}
.editor-textarea{resize:vertical;min-height:112px}
.field input:focus,.editor-textarea:focus{border-color:rgba(229,199,138,.54);box-shadow:0 0 0 4px rgba(229,199,138,.08)}
.metric-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
.metric-grid div{display:grid;gap:4px;padding:14px;border-radius:18px;background:rgba(255,255,255,.04)}
.metric-grid strong{font-size:18px;color:#f5efe3}
.issue-columns{grid-template-columns:repeat(2,minmax(0,1fr))}
.issue-group{display:grid;gap:10px}
.group-head h4,.eyebrow,.panel-note,p{margin:0}
.group-head span{display:inline-flex;padding:6px 10px;border-radius:999px;background:rgba(255,255,255,.05);color:#d6cfbf;font-size:12px}
.issue-card{display:grid;gap:6px;padding:14px;border-radius:18px;background:rgba(255,255,255,.04)}
.issue-card p{color:#ddd4c4;line-height:1.6}
.issue-card small{color:#9e9a93}
.issue-card.error{border:1px solid rgba(217,94,81,.35)}
.issue-card.warning{border:1px solid rgba(229,199,138,.28)}
.empty-copy{color:#a8a094;line-height:1.7}
.panel-note{color:#9e9a93}
.status{display:inline-flex;align-items:center;padding:12px 14px;border-radius:14px;line-height:1.5}
.status.success{color:#dff7d6;background:rgba(117,198,122,.14)}
.status.error{color:#ffd7d2;background:rgba(217,94,81,.14)}
.primary-button,.secondary-button{appearance:none;border:0;cursor:pointer;padding:12px 18px;border-radius:999px;transition:transform 160ms ease,background 160ms ease,opacity 160ms ease}
.primary-button{background:#e5c78a;color:#11161d}
.secondary-button{background:rgba(255,255,255,.06);color:#f4ede0}
.primary-button:hover,.secondary-button:hover{transform:translateY(-1px)}
.primary-button:disabled,.secondary-button:disabled{opacity:.5;cursor:not-allowed}
.eyebrow{font-size:12px;letter-spacing:.24em;text-transform:uppercase;color:#b9a988}
h3,h4{margin:0}
h3{font-size:clamp(28px,4vw,38px);line-height:1}
@media (max-width:1100px){.generator-grid,.issue-columns,.field-grid,.metric-grid{grid-template-columns:1fr}}
</style>
