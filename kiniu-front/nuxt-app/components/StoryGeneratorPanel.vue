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
  premise: 'A desktop Agent container that can host companion, interview, knowledge, project, and writing Agents.',
  setting: 'local desktop workspace',
  tone: 'practical, focused',
  chapterGoal: 'route the user to the right Agent and preserve reusable task flows',
  protagonistName: 'User',
  companionName: 'Companion Agent',
  rivalName: 'Reviewer Agent'
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
        <h3>Agent Flow Generator</h3>
      </div>

      <div class="panel-actions">
        <button class="secondary-button" type="button" :disabled="!hasDraft || isValidating" @click="emit('validateDraft')">
          {{ isValidating ? 'Validating...' : 'Validate Draft' }}
        </button>
        <button class="primary-button" type="button" :disabled="isGenerating" @click="submitGeneration">
          {{ isGenerating ? 'Generating...' : 'Generate Starter Flow' }}
        </button>
      </div>
    </div>

    <div class="generator-grid">
      <div class="generator-panel">
        <div class="field-grid">
          <label class="field wide">
            <span>Container Premise</span>
            <textarea v-model="form.premise" class="editor-textarea" rows="4" />
          </label>
          <label class="field">
            <span>Workspace</span>
            <input v-model="form.setting" type="text">
          </label>
          <label class="field">
            <span>Tone</span>
            <input v-model="form.tone" type="text">
          </label>
          <label class="field wide">
            <span>Session Goal</span>
            <input v-model="form.chapterGoal" type="text">
          </label>
          <label class="field">
            <span>User Label</span>
            <input v-model="form.protagonistName" type="text">
          </label>
          <label class="field">
            <span>Primary Agent</span>
            <input v-model="form.companionName" type="text">
          </label>
          <label class="field">
            <span>Reviewer Agent</span>
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
          Run validation on the current draft or generate a new starter Agent flow from a premise.
        </p>
      </div>
    </div>

    <p class="panel-note">
      Backend endpoint: <strong>{{ backendUrl || 'not configured' }}</strong>
    </p>
  </section>
</template>

<style scoped>
.generator-stage{display:grid;gap:18px;padding:20px;border:1px solid var(--color-border);border-radius:var(--radius);background:#f7fffd}
.panel-head,.panel-actions,.group-head{display:flex;justify-content:space-between;gap:12px;align-items:center;flex-wrap:wrap}
.generator-grid{display:grid;grid-template-columns:minmax(0,1.1fr) minmax(0,.9fr);gap:18px}
.generator-panel,.analysis-panel{display:grid;gap:16px;padding:20px;border:1px solid #d7eeea;border-radius:var(--radius);background:#fff}
.field-grid,.status-column,.issue-columns,.issue-list{display:grid;gap:12px}
.field-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
.field{display:grid;gap:10px}
.field.wide{grid-column:1/-1}
.field span,.metric-grid span{font-size:12px;letter-spacing:.1em;text-transform:uppercase;color:var(--color-primary-strong);font-weight:800}
.field input,.editor-textarea{width:100%;min-height:44px;padding:10px 14px;border:1px solid var(--color-border);border-radius:var(--radius);outline:none;color:var(--color-text);background:#fff;font:inherit}
.editor-textarea{resize:vertical;min-height:112px}
.field input:focus,.editor-textarea:focus{border-color:var(--color-primary);box-shadow:0 0 0 4px rgba(13,148,136,.12)}
.metric-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
.metric-grid div{display:grid;gap:4px;padding:14px;border:1px solid #d7eeea;border-radius:var(--radius);background:#f7fffd}
.metric-grid strong{font-size:18px;color:var(--color-text)}
.issue-columns{grid-template-columns:repeat(2,minmax(0,1fr))}
.issue-group{display:grid;gap:10px}
.group-head h4,.eyebrow,.panel-note,p{margin:0}
.group-head span{display:inline-flex;padding:5px 9px;border-radius:var(--radius);background:#eef6f4;color:var(--color-faint);font-size:12px;font-weight:700}
.issue-card{display:grid;gap:6px;padding:14px;border-radius:var(--radius);background:#fff}
.issue-card p{color:var(--color-muted);line-height:1.6}
.issue-card small{color:var(--color-faint)}
.issue-card.error{border:1px solid #fecaca;background:#fff7f7}
.issue-card.warning{border:1px solid #fed7aa;background:#fff7ed}
.empty-copy{color:var(--color-faint);line-height:1.65}
.panel-note{color:var(--color-faint)}
.status{display:inline-flex;align-items:center;min-height:44px;padding:10px 14px;border-radius:var(--radius);line-height:1.5}
.status.success{color:#166534;background:#dcfce7;border:1px solid #bbf7d0}
.status.error{color:#991b1b;background:#fee2e2;border:1px solid #fecaca}
.primary-button,.secondary-button{appearance:none;border:0;cursor:pointer;min-height:44px;padding:0 16px;border-radius:var(--radius);font-weight:800;transition:background 180ms var(--ease),border-color 180ms var(--ease),box-shadow 180ms var(--ease),opacity 180ms var(--ease)}
.primary-button{background:var(--color-accent);color:#fff}
.secondary-button{border:1px solid var(--color-border);background:#fff;color:var(--color-primary-strong)}
.primary-button:hover{background:#c2410c;box-shadow:0 10px 22px rgba(234,88,12,.18)}
.secondary-button:hover{border-color:var(--color-primary);background:#ecfdf5}
.primary-button:disabled,.secondary-button:disabled{opacity:.5;cursor:not-allowed}
.eyebrow{font-size:12px;letter-spacing:.18em;text-transform:uppercase;color:var(--color-primary-strong);font-weight:800}
h3,h4{margin:0}
h3{font-size:clamp(28px,4vw,38px);line-height:1.05;color:#102f2d}
h4{color:#173f3b}
@media (max-width:1100px){.generator-grid,.issue-columns,.field-grid,.metric-grid{grid-template-columns:1fr}}
</style>
