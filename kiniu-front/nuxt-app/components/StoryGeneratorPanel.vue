<script setup lang="ts">
import { useUiI18n } from '../i18n'
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

const { t } = useUiI18n()
const form = reactive<StoryGenerationRequest>({
  premise: t('promptPremise'),
  setting: t('promptWorkspace'),
  tone: t('promptTone'),
  chapterGoal: t('promptGoal'),
  protagonistName: t('promptUser'),
  companionName: t('promptPrimaryAgent'),
  rivalName: t('promptReviewerAgent')
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
  return [issue.nodeId, issue.choiceId].filter(Boolean).join(' / ') || t('labelCatalog')
}
</script>

<template>
  <section class="generator-stage">
    <div class="panel-head">
      <div>
        <p class="eyebrow">{{ t('generatorEyebrow') }}</p>
        <h3>{{ t('generatorTitle') }}</h3>
      </div>

      <div class="panel-actions">
        <button class="secondary-button" type="button" :disabled="!hasDraft || isValidating" @click="emit('validateDraft')">
          {{ isValidating ? t('actionValidating') : t('actionValidateDraft') }}
        </button>
        <button class="primary-button" type="button" :disabled="isGenerating" @click="submitGeneration">
          {{ isGenerating ? t('actionGenerating') : t('actionGenerateStarterFlow') }}
        </button>
      </div>
    </div>

    <div class="generator-grid">
      <div class="generator-panel">
        <div class="field-grid">
          <label class="field wide">
            <span>{{ t('fieldContainerPremise') }}</span>
            <textarea v-model="form.premise" class="editor-textarea" rows="4" />
          </label>
          <label class="field">
            <span>{{ t('labelWorkspace') }}</span>
            <input v-model="form.setting" type="text">
          </label>
          <label class="field">
            <span>{{ t('fieldTone') }}</span>
            <input v-model="form.tone" type="text">
          </label>
          <label class="field wide">
            <span>{{ t('fieldSessionGoal') }}</span>
            <input v-model="form.chapterGoal" type="text">
          </label>
          <label class="field">
            <span>{{ t('fieldUserLabel') }}</span>
            <input v-model="form.protagonistName" type="text">
          </label>
          <label class="field">
            <span>{{ t('fieldPrimaryAgent') }}</span>
            <input v-model="form.companionName" type="text">
          </label>
          <label class="field">
            <span>{{ t('fieldReviewerAgent') }}</span>
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
          <div><span>{{ t('labelNodes') }}</span><strong>{{ analysis.totalNodes }}</strong></div>
          <div><span>{{ t('labelChoices') }}</span><strong>{{ analysis.totalChoices }}</strong></div>
          <div><span>{{ t('labelReachable') }}</span><strong>{{ analysis.reachableNodeIds.length }}</strong></div>
          <div><span>{{ t('labelEndings') }}</span><strong>{{ analysis.endingNodeIds.length }}</strong></div>
          <div><span>{{ t('labelErrors') }}</span><strong>{{ analysis.errorCount }}</strong></div>
          <div><span>{{ t('labelWarnings') }}</span><strong>{{ analysis.warningCount }}</strong></div>
        </div>

        <div v-if="analysis" class="issue-columns">
          <div class="issue-group">
            <div class="group-head">
              <h4>{{ t('labelErrors') }}</h4>
              <span>{{ issueGroups.errors.length }}</span>
            </div>
            <div class="issue-list">
              <article v-for="issue in issueGroups.errors" :key="`${issue.code}-${issueLocation(issue)}`" class="issue-card error">
                <strong>{{ issue.code }}</strong>
                <p>{{ issue.message }}</p>
                <small>{{ issueLocation(issue) }}</small>
              </article>
              <p v-if="issueGroups.errors.length === 0" class="empty-copy">{{ t('noBlockingErrors') }}</p>
            </div>
          </div>

          <div class="issue-group">
            <div class="group-head">
              <h4>{{ t('labelWarnings') }}</h4>
              <span>{{ issueGroups.warnings.length }}</span>
            </div>
            <div class="issue-list">
              <article v-for="issue in issueGroups.warnings" :key="`${issue.code}-${issueLocation(issue)}`" class="issue-card warning">
                <strong>{{ issue.code }}</strong>
                <p>{{ issue.message }}</p>
                <small>{{ issueLocation(issue) }}</small>
              </article>
              <p v-if="issueGroups.warnings.length === 0" class="empty-copy">{{ t('noStructuralWarnings') }}</p>
            </div>
          </div>
        </div>

        <p v-else class="empty-copy">
          {{ t('generatorEmpty') }}
        </p>
      </div>
    </div>

    <p class="panel-note">
      {{ t('backendEndpoint') }}: <strong>{{ backendUrl || t('fieldNotConfigured') }}</strong>
    </p>
  </section>
</template>

<style scoped>
.generator-stage{display:grid;gap:18px;padding:20px;border:1px solid var(--color-border);border-radius:var(--radius);background:var(--color-bg-soft)}
.panel-head,.panel-actions,.group-head{display:flex;justify-content:space-between;gap:12px;align-items:center;flex-wrap:wrap}
.generator-grid{display:grid;grid-template-columns:minmax(0,1.1fr) minmax(0,.9fr);gap:18px}
.generator-panel,.analysis-panel{display:grid;gap:16px;padding:20px;border:1px solid var(--color-border-soft);border-radius:var(--radius);background:var(--color-surface)}
.field-grid,.status-column,.issue-columns,.issue-list{display:grid;gap:12px}
.field-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
.field{display:grid;gap:10px}
.field.wide{grid-column:1/-1}
.field span,.metric-grid span{font-size:12px;letter-spacing:.1em;text-transform:uppercase;color:var(--color-primary-strong);font-weight:800}
.field input,.editor-textarea{width:100%;min-height:44px;padding:10px 14px;border:1px solid var(--color-border);border-radius:var(--radius);outline:none;color:var(--color-text);background:var(--color-input);font:inherit}
.editor-textarea{resize:vertical;min-height:112px}
.field input:focus,.editor-textarea:focus{border-color:var(--color-primary);box-shadow:0 0 0 4px var(--color-focus-ring)}
.metric-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
.metric-grid div{display:grid;gap:4px;padding:14px;border:1px solid var(--color-border-soft);border-radius:var(--radius);background:var(--color-bg-soft)}
.metric-grid strong{font-size:18px;color:var(--color-text)}
.issue-columns{grid-template-columns:repeat(2,minmax(0,1fr))}
.issue-group{display:grid;gap:10px}
.group-head h4,.eyebrow,.panel-note,p{margin:0}
.group-head span{display:inline-flex;padding:5px 9px;border-radius:var(--radius);background:var(--color-token-muted-bg);color:var(--color-faint);font-size:12px;font-weight:700}
.issue-card{display:grid;gap:6px;padding:14px;border-radius:var(--radius);background:var(--color-surface)}
.issue-card p{color:var(--color-muted);line-height:1.6}
.issue-card small{color:var(--color-faint)}
.issue-card.error{border:1px solid var(--color-danger-border);background:var(--color-danger-bg)}
.issue-card.warning{border:1px solid var(--color-warning-border, var(--color-border-soft));background:var(--color-warning-bg)}
.empty-copy{color:var(--color-faint);line-height:1.65}
.panel-note{color:var(--color-faint)}
.status{display:inline-flex;align-items:center;min-height:44px;padding:10px 14px;border-radius:var(--radius);line-height:1.5}
.status.success{color:var(--color-success-text);background:var(--color-success-bg);border:1px solid var(--color-success-border)}
.status.error{color:var(--color-danger-text);background:var(--color-danger-bg);border:1px solid var(--color-danger-border)}
.primary-button,.secondary-button{appearance:none;border:0;cursor:pointer;min-height:44px;padding:0 16px;border-radius:var(--radius);font-weight:800;transition:background 180ms var(--ease),border-color 180ms var(--ease),box-shadow 180ms var(--ease),opacity 180ms var(--ease)}
.primary-button{background:var(--color-accent);color:var(--color-on-accent)}
.secondary-button{border:1px solid var(--color-border);background:var(--color-input);color:var(--color-primary-strong)}
.primary-button:hover{background:var(--color-accent-hover);box-shadow:var(--shadow-accent)}
.secondary-button:hover{border-color:var(--color-primary);background:var(--color-hover)}
.primary-button:disabled,.secondary-button:disabled{opacity:.5;cursor:not-allowed}
.eyebrow{font-size:12px;letter-spacing:.18em;text-transform:uppercase;color:var(--color-primary-strong);font-weight:800}
h3,h4{margin:0}
h3{font-size:clamp(28px,4vw,38px);line-height:1.05;color:var(--color-heading)}
h4{color:var(--color-heading-soft)}
@media (max-width:1100px){.generator-grid,.issue-columns,.field-grid,.metric-grid{grid-template-columns:1fr}}
</style>
