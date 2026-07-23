<script setup lang="ts">
import type {
  LearningCatalog,
  LearningProgress,
  LearningTask,
  LearningCheckResponse,
  TaskCheckResult,
  LearningPublishResponse
} from '../types/learning'
import { useUiI18n } from '../i18n'

const props = defineProps<{
  backendUrl: string
  localToken: string
  providerUrl: string
  apiKey: string
  model: string
}>()
const emit = defineEmits<{
  (event: 'use-agent', agentId: string): void
}>()
const { t } = useUiI18n()

const DRAFT_STORAGE_KEY = 'kiniu.learn.taskDrafts'
const catalog = ref<LearningCatalog | null>(null)
const progress = ref<LearningProgress | null>(null)
const selectedTaskId = ref('')
const files = ref<Record<string, string>>({})
const activeFile = ref('')
const drafts = ref<Record<string, Record<string, string>>>({})
const results = ref<TaskCheckResult[]>([])
const attemptId = ref('')
const loading = ref(false)
const checking = ref(false)
const mentorChecking = ref(false)
const publishing = ref(false)
const error = ref('')
const notice = ref('')
const mentorQuestion = ref('')
const mentorFeedback = ref('')
const publishedAgentName = ref('')
const importError = ref('')
const importFileInfo = ref('')
const pendingImport = ref<{ path: string; name: string; size: number; content: string } | null>(null)
let requestGeneration = 0
let requestSequence = 0

const tasks = computed(() => catalog.value?.modules.flatMap(module => module.tasks) ?? [])
const selectedTask = computed<LearningTask | null>(() =>
  tasks.value.find(task => task.id === selectedTaskId.value) ?? tasks.value[0] ?? null)
const completed = computed(() => new Set(progress.value?.completedTaskIds ?? []))
const score = computed(() => progress.value?.bestScores[selectedTaskId.value] ?? 0)
const requestHeaders = computed<Record<string, string>>(() =>
  props.localToken.trim() ? { 'X-Local-Token': props.localToken.trim() } : {})
const feedbackHeaders = computed<Record<string, string>>(() => {
  const headers = { ...requestHeaders.value }
  if (props.apiKey.trim()) {
    headers.Authorization = 'Bearer ' + props.apiKey.trim()
    headers['X-API-Key'] = props.apiKey.trim()
  }
  if (props.providerUrl.trim()) headers['X-Provider-Url'] = props.providerUrl.trim()
  if (props.model.trim()) headers['X-Model'] = props.model.trim()
  return headers
})

function isLoopbackBackendUrl(value: string) {
  try {
    const hostname = new URL(value).hostname.toLowerCase()
    return ['localhost', '127.0.0.1', '::1', '[::1]'].includes(hostname)
  } catch {
    return false
  }
}

function assertSafeSecretTarget(includeProvider = false) {
  const hasSecret = props.localToken.trim() || (includeProvider && props.apiKey.trim())
  if (hasSecret && !isLoopbackBackendUrl(props.backendUrl.trim())) {
    throw new Error(t('backendMustBeLocal'))
  }
}

function taskUnlocked(task: LearningTask) {
  if (completed.value.has(task.id)) return true
  if (task.prerequisiteTaskIds?.length) {
    return task.prerequisiteTaskIds.every(taskId => completed.value.has(taskId))
  }
  const index = tasks.value.findIndex(candidate => candidate.id === task.id)
  return index === 0 || completed.value.has(tasks.value[index - 1]?.id ?? '')
}

function prerequisiteTitles(task: LearningTask) {
  return (task.prerequisiteTaskIds ?? [])
    .map(taskId => tasks.value.find(candidate => candidate.id === taskId)?.title ?? taskId)
}

function firstAvailableTask(nextCatalog: LearningCatalog, nextProgress: LearningProgress) {
  const allTasks = nextCatalog.modules.flatMap(module => module.tasks)
  const done = new Set(nextProgress.completedTaskIds)
  return allTasks.find(task => !done.has(task.id)
    && (!task.prerequisiteTaskIds?.length || task.prerequisiteTaskIds.every(id => done.has(id))))
    ?.id ?? nextProgress.currentTaskId ?? allTasks[0]?.id ?? ''
}

function selectTask(task: LearningTask) {
  if (!taskUnlocked(task) || checking.value || publishing.value) return
  selectedTaskId.value = task.id
}

watch(selectedTask, task => {
  if (!task) return
  const restored = drafts.value[task.id]
  files.value = restored ? { ...restored } : Object.fromEntries(task.starterFiles.map(file => [file.path, file.content]))
  activeFile.value = task.starterFiles[0]?.path ?? ''
  results.value = []
  attemptId.value = ''
  mentorFeedback.value = ''
  publishedAgentName.value = ''
  importError.value = ''
  importFileInfo.value = ''
  pendingImport.value = null
}, { immediate: true })

onMounted(() => {
  try {
    drafts.value = JSON.parse(localStorage.getItem(DRAFT_STORAGE_KEY) || '{}')
  } catch {
    drafts.value = {}
  }
  loadLearning()
})

watch(() => props.backendUrl, () => {
  if (!props.backendUrl.trim()) return
  catalog.value = null
  progress.value = null
  loadLearning()
})
watch(() => props.localToken, () => {
  if (catalog.value) loadLearning()
})

async function loadLearning() {
  if (!props.backendUrl.trim()) {
    error.value = t('learningNeedBackend')
    return
  }
  const generation = ++requestGeneration
  loading.value = true
  error.value = ''
  try {
    assertSafeSecretTarget()
    const [nextCatalog, nextProgress] = await Promise.all([
      $fetch<LearningCatalog>('/learn/catalog', { baseURL: props.backendUrl.trim(), headers: requestHeaders.value }),
      $fetch<LearningProgress>('/learn/progress', { baseURL: props.backendUrl.trim(), headers: requestHeaders.value })
    ])
    if (generation !== requestGeneration) return
    catalog.value = nextCatalog
    progress.value = nextProgress
    selectedTaskId.value = firstAvailableTask(nextCatalog, nextProgress)
    notice.value = t('learningCatalogLoaded')
  } catch (requestError) {
    if (generation === requestGeneration) {
      error.value = requestError instanceof Error ? requestError.message : t('learningCatalogLoadFailed')
    }
  } finally {
    if (generation === requestGeneration) loading.value = false
  }
}

function saveDraft() {
  if (!selectedTask.value) return
  drafts.value[selectedTask.value.id] = { ...files.value }
  localStorage.setItem(DRAFT_STORAGE_KEY, JSON.stringify(drafts.value))
}

async function checkTask() {
  const task = selectedTask.value
  if (!task || !props.backendUrl.trim() || !taskUnlocked(task)) return
  saveDraft()
  const taskId = task.id
  const sequence = ++requestSequence
  const generation = requestGeneration
  checking.value = true
  error.value = ''
  notice.value = ''
  try {
    assertSafeSecretTarget()
    const response = await $fetch<LearningCheckResponse>(
      `/learn/tasks/${encodeURIComponent(taskId)}/check`,
      {
        baseURL: props.backendUrl.trim(),
        method: 'POST',
        headers: requestHeaders.value,
        body: { files: files.value }
      })
    if (generation !== requestGeneration) return
    progress.value = response.progress
    if (selectedTaskId.value !== taskId || sequence !== requestSequence) return
    results.value = response.results
    attemptId.value = response.attemptId
    notice.value = response.passed
      ? t('learningCheckPassed', { score: response.score })
      : t('learningCheckFailed', { score: response.score })
  } catch (requestError) {
    if (generation === requestGeneration && selectedTaskId.value === taskId) {
      error.value = requestError instanceof Error ? requestError.message : t('learningCheckRequestFailed')
    }
  } finally {
    if (sequence === requestSequence) checking.value = false
  }
}

async function askMentor() {
  const task = selectedTask.value
  const currentAttemptId = attemptId.value
  if (!task || !currentAttemptId || !props.backendUrl.trim()) return
  const taskId = task.id
  const generation = requestGeneration
  mentorChecking.value = true
  error.value = ''
  try {
    assertSafeSecretTarget(true)
    const response = await $fetch<{ feedback: string }>(
      `/learn/tasks/${encodeURIComponent(taskId)}/feedback`,
      {
        baseURL: props.backendUrl.trim(),
        method: 'POST',
        headers: feedbackHeaders.value,
        body: { attemptId: currentAttemptId, question: mentorQuestion.value }
      })
    if (generation === requestGeneration && selectedTaskId.value === taskId && attemptId.value === currentAttemptId) {
      mentorFeedback.value = response.feedback
    }
  } catch (requestError) {
    if (generation === requestGeneration && selectedTaskId.value === taskId) {
      error.value = requestError instanceof Error ? requestError.message : t('learningMentorRequestFailed')
    }
  } finally {
    mentorChecking.value = false
  }
}

async function publishAgent() {
  const task = selectedTask.value
  const currentAttemptId = attemptId.value
  if (!task || task.kind !== 'agent-project' || !currentAttemptId || !props.backendUrl.trim()) return
  const taskId = task.id
  const generation = requestGeneration
  publishing.value = true
  error.value = ''
  try {
    assertSafeSecretTarget()
    const response = await $fetch<LearningPublishResponse>(
      `/learn/tasks/${encodeURIComponent(taskId)}/publish-agent`,
      {
        baseURL: props.backendUrl.trim(),
        method: 'POST',
        headers: requestHeaders.value,
        body: { attemptId: currentAttemptId }
      })
    if (generation === requestGeneration && selectedTaskId.value === taskId) {
      publishedAgentName.value = response.agent.name
      notice.value = t('learningPublished', { name: response.agent.name })
      emit('use-agent', response.agent.id)
    }
  } catch (requestError) {
    if (generation === requestGeneration && selectedTaskId.value === taskId) {
      error.value = requestError instanceof Error ? requestError.message : t('learningPublishFailed')
    }
  } finally {
    publishing.value = false
  }
}

function updateActiveFile(event: Event) {
  if (!activeFile.value) return
  files.value[activeFile.value] = (event.target as HTMLTextAreaElement).value
  saveDraft()
}

function selectActiveFile(path: string) {
  activeFile.value = path
  importError.value = ''
  importFileInfo.value = ''
  pendingImport.value = null
}

async function importActiveFile(event: Event) {
  const task = selectedTask.value
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  importError.value = ''
  importFileInfo.value = ''
  pendingImport.value = null
  if (!task || !activeFile.value || !file) return
  if (file.name !== activeFile.value) {
    importError.value = t('learningImportWrongFile', { file: activeFile.value })
    return
  }
  if (file.size > 100_000) {
    importError.value = t('learningImportFileTooLarge')
    return
  }
  const currentTotal = Object.entries(files.value)
    .filter(([path]) => path !== activeFile.value)
    .reduce((total, [, content]) => total + new TextEncoder().encode(content).length, 0)
  if (currentTotal + file.size > 500_000) {
    importError.value = t('learningImportTotalTooLarge')
    return
  }
  const content = await file.text()
  if (file.name.toLowerCase().endsWith('.json')) {
    try {
      JSON.parse(content)
    } catch {
      importError.value = t('learningImportInvalidJson')
      return
    }
  }
  pendingImport.value = { path: activeFile.value, name: file.name, size: file.size, content }
  importFileInfo.value = t('learningImportSelected', { name: file.name, size: file.size })
}

function confirmImport() {
  const pending = pendingImport.value
  if (!pending) return
  files.value[pending.path] = pending.content
  importFileInfo.value = t('learningImportLoaded', { name: pending.name, size: pending.size })
  pendingImport.value = null
  saveDraft()
}
</script>

<template>
  <section class="learning-view">
    <header class="learning-hero">
      <div>
        <p class="eyebrow">{{ t('learningEyebrow') }}</p>
        <h2>{{ t('learningTitle') }}</h2>
        <p class="hero-copy">{{ t('learningHeroCopy') }}</p>
      </div>
      <div class="hero-metrics">
        <div><strong>{{ progress?.completedTaskIds.length ?? 0 }}</strong><span>{{ t('learningCompleted') }}</span></div>
        <div><strong>{{ progress?.weakSkills.length ?? 0 }}</strong><span>{{ t('learningWeakSkills') }}</span></div>
        <div><strong>{{ catalog?.version ?? '-' }}</strong><span>{{ t('learningCatalogVersion') }}</span></div>
      </div>
    </header>

    <p v-if="loading" class="status">{{ t('learningLoading') }}</p>
    <p v-if="error" class="status error">{{ error }}</p>
    <p v-if="notice" class="status success">{{ notice }}</p>

    <div v-if="catalog" class="learning-grid">
      <aside class="course-rail">
        <div v-for="module in catalog.modules" :key="module.id" class="module-block">
          <div class="module-heading"><span>{{ module.level }}</span><strong>{{ module.title }}</strong></div>
          <p>{{ module.summary }}</p>
          <button
            v-for="task in module.tasks"
            :key="task.id"
            class="task-button"
            :class="{ active: selectedTask?.id === task.id, locked: !taskUnlocked(task) }"
            type="button"
            :disabled="!taskUnlocked(task) || checking || publishing"
            :title="taskUnlocked(task) ? task.title : t('learningLockedPrerequisites', { prerequisites: prerequisiteTitles(task).join('、') || t('learningPreviousTask') })"
            @click="selectTask(task)"
          >
            <span class="task-state" :class="{ done: completed.has(task.id), locked: !taskUnlocked(task) }">
              {{ completed.has(task.id) ? '✓' : taskUnlocked(task) ? '→' : '🔒' }}
            </span>
            <span>
              <strong>{{ task.title }}</strong>
              <small>{{ t('learningMinutes', { minutes: task.estimatedMinutes, level: task.level }) }}</small>
              <small v-if="!taskUnlocked(task)" class="lock-reason">{{ t('learningLockedPrerequisites', { prerequisites: prerequisiteTitles(task).join('、') || t('learningPreviousTask') }) }}</small>
            </span>
          </button>
        </div>
      </aside>

      <main v-if="selectedTask" class="task-workspace">
        <section class="task-brief panel">
          <div class="section-heading">
            <div><p class="eyebrow">{{ t('learningCurrentTask', { kind: selectedTask.kind }) }}</p><h3>{{ selectedTask.title }}</h3></div>
            <span class="score-chip">{{ t('learningBestScore', { score: score || '—' }) }}</span>
          </div>
          <p class="summary">{{ selectedTask.summary }}</p>
          <div class="objective"><strong>{{ t('learningObjective') }}</strong><span>{{ selectedTask.objective }}</span></div>
          <div class="scenario"><strong>{{ t('learningScenario') }}</strong><span>{{ selectedTask.scenario }}</span></div>
          <div class="skill-list"><span v-for="skill in selectedTask.skills" :key="skill">{{ skill }}</span></div>
          <div class="lesson-block">
            <p class="eyebrow">{{ t('learningLesson') }}</p>
            <div class="lesson-copy">{{ selectedTask.lesson }}</div>
          </div>
          <div class="task-meta-grid">
            <div><strong>{{ t('learningDeliverables') }}</strong><ul><li v-for="item in selectedTask.deliverables" :key="item">{{ item }}</li></ul></div>
            <div><strong>{{ t('learningPrerequisites') }}</strong><span v-if="!selectedTask.prerequisiteTaskIds?.length">{{ t('learningNoPrerequisites') }}</span><ul v-else><li v-for="item in prerequisiteTitles(selectedTask)" :key="item">{{ item }}</li></ul></div>
          </div>
          <div class="reference-list">
            <strong>{{ t('learningReferences') }}</strong>
            <a v-for="reference in selectedTask.references" :key="reference.url" :href="reference.url" target="_blank" rel="noopener noreferrer">
              {{ reference.title }} · {{ reference.publisher }} · {{ reference.version }}
            </a>
          </div>
        </section>

        <section class="workbench-grid">
          <div class="panel file-panel">
            <div class="section-heading">
              <div><p class="eyebrow">{{ t('learningArtifact') }}</p><h3>{{ t('learningWorkspace') }}</h3></div>
              <button class="primary-button" type="button" :disabled="checking || publishing" @click="checkTask">
                {{ checking ? t('learningChecking') : t('learningRunChecks') }}
              </button>
            </div>
            <div class="file-tabs">
              <button v-for="path in Object.keys(files)" :key="path" type="button" :class="{ active: activeFile === path }" @click="selectActiveFile(path)">{{ path }}</button>
            </div>
            <div class="import-row">
              <input id="learning-file-import" type="file" accept=".json,.md,.markdown,.txt,application/json,text/markdown,text/plain" @change="importActiveFile" />
              <label for="learning-file-import" class="secondary-button">{{ t('learningImportFile') }}</label>
              <span>{{ selectedTask.evidenceMode === 'import' ? t('learningImportMode') : t('learningDocumentMode') }}</span>
            </div>
            <p v-if="importFileInfo" class="status success">{{ importFileInfo }}</p>
            <button v-if="pendingImport" class="secondary-button import-confirm" type="button" @click="confirmImport">{{ t('learningImportConfirm') }}</button>
            <p v-if="importError" class="status error">{{ importError }}</p>
            <textarea :value="files[activeFile]" spellcheck="false" @input="updateActiveFile" />
            <p class="editor-hint">{{ t('learningEditorHint') }}</p>
          </div>

          <div class="panel evidence-panel">
            <div class="section-heading"><div><p class="eyebrow">{{ t('learningEvidence') }}</p><h3>{{ t('learningChecklist') }}</h3></div><span>{{ results.length }}/{{ selectedTask.checks.length }}</span></div>
            <div v-if="results.length" class="check-list">
              <article v-for="result in results" :key="result.checkId" :class="['check-row', result.passed ? 'passed' : 'failed']">
                <strong>{{ result.passed ? t('learningPassed') : t('learningNeedsFix') }} · {{ result.checkId }}</strong>
                <span>{{ result.message }}</span>
                <small>{{ result.evidence }}</small>
              </article>
            </div>
            <div v-else class="empty-evidence"><strong>{{ t('learningNoEvidence') }}</strong><span>{{ t('learningNoEvidenceCopy') }}</span></div>
            <div class="mentor-box">
              <p class="eyebrow">{{ t('learningMentor') }}</p>
              <textarea v-model="mentorQuestion" :placeholder="t('learningMentorPlaceholder')" rows="3" />
              <button class="secondary-button" type="button" :disabled="!attemptId || mentorChecking || checking" @click="askMentor">
                {{ mentorChecking ? t('learningRequesting') : t('learningRequestExplanation') }}
              </button>
              <p v-if="mentorFeedback" class="mentor-feedback">{{ mentorFeedback }}</p>
              <button v-if="selectedTask.kind === 'agent-project' && results.length && attemptId && progress?.completedTaskIds.includes(selectedTask.id)" class="publish-button" type="button" :disabled="publishing" @click="publishAgent">
                {{ publishing ? t('learningPublishing') : t('learningPublishUse') }}
              </button>
              <small v-if="publishedAgentName" class="publish-note">{{ publishedAgentName }}</small>
            </div>
          </div>
        </section>
      </main>
    </div>
  </section>
</template>

<style scoped>
.learning-view{display:grid;gap:12px;min-width:0;min-height:0}.learning-hero,.panel,.course-rail{border:1px solid var(--color-border);border-radius:var(--radius);background:var(--color-surface-panel);box-shadow:var(--shadow-card)}.learning-hero{display:flex;justify-content:space-between;gap:24px;padding:22px}.learning-hero h2{margin:4px 0 8px;color:var(--color-heading);font-size:27px;letter-spacing:0}.hero-copy,.summary,.module-block p,.editor-hint,.empty-evidence span{color:var(--color-muted);line-height:1.6}.hero-copy{max-width:680px}.hero-metrics{display:flex;align-items:center;gap:22px}.hero-metrics div{display:grid;gap:4px;min-width:78px}.hero-metrics strong{font-size:25px;color:var(--color-primary-strong)}.hero-metrics span{font-size:11px;color:var(--color-faint)}.learning-grid{display:grid;grid-template-columns:280px minmax(0,1fr);gap:12px;min-height:0}.course-rail{padding:12px}.module-block{display:grid;gap:7px;padding:8px 0 14px;border-bottom:1px solid var(--color-border-soft)}.module-block:last-child{border-bottom:0}.module-heading{display:grid;gap:3px}.module-heading span,.eyebrow{font-size:10px;letter-spacing:0;color:var(--color-primary-strong);font-weight:900}.module-heading strong{color:var(--color-heading)}.module-block p{margin:0;font-size:12px}.task-button{display:grid;grid-template-columns:24px minmax(0,1fr);gap:7px;align-items:start;width:100%;padding:9px;border:1px solid transparent;border-radius:var(--radius);background:transparent;color:var(--color-text);text-align:left;cursor:pointer}.task-button:hover,.task-button.active{border-color:var(--color-border-strong);background:var(--color-surface-muted)}.task-button:disabled{cursor:not-allowed;opacity:.55}.task-button strong,.task-button small{display:block}.task-button strong{font-size:12px}.task-button small{margin-top:3px;color:var(--color-faint);font-size:11px}.task-button .lock-reason{color:var(--color-warning-text);white-space:normal;line-height:1.4}.task-state{display:grid;place-items:center;width:21px;height:21px;border-radius:50%;background:var(--color-token-muted-bg);color:var(--color-faint);font-weight:900}.task-state.done{background:var(--color-success-bg);color:var(--color-success)}.task-state.locked{background:var(--color-surface-muted)}.task-workspace{display:grid;gap:12px;min-width:0}.panel{padding:16px}.section-heading{display:flex;justify-content:space-between;align-items:start;gap:12px}.section-heading h3{margin:3px 0 0;color:var(--color-heading);font-size:18px}.score-chip{padding:5px 9px;border-radius:999px;background:var(--color-token-bg);color:var(--color-token-text);font-size:12px;font-weight:800}.objective,.scenario{display:grid;gap:4px;padding:10px 12px;margin-top:10px;border-left:3px solid var(--color-primary);background:var(--color-surface-muted);font-size:13px;line-height:1.5}.scenario{border-left-color:var(--color-accent)}.objective strong,.scenario strong{font-size:11px;color:var(--color-primary-strong)}.skill-list{display:flex;gap:6px;flex-wrap:wrap;margin-top:12px}.skill-list span{padding:5px 8px;border:1px solid var(--color-border-soft);border-radius:999px;color:var(--color-muted);font-size:11px}.lesson-block{display:grid;gap:6px;margin-top:16px;padding-top:14px;border-top:1px solid var(--color-border-soft)}.lesson-copy{white-space:pre-line;color:var(--color-muted);font-size:13px;line-height:1.65}.task-meta-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-top:14px;color:var(--color-muted);font-size:12px;line-height:1.5}.task-meta-grid strong,.reference-list strong{display:block;color:var(--color-heading);font-size:11px}.task-meta-grid ul{margin:6px 0 0;padding-left:18px}.reference-list{display:grid;gap:5px;margin-top:14px;padding-top:12px;border-top:1px solid var(--color-border-soft);font-size:11px}.reference-list a{color:var(--color-primary-strong);text-decoration:none}.reference-list a:hover{text-decoration:underline}.import-row{display:flex;align-items:center;gap:10px;flex-wrap:wrap;color:var(--color-muted);font-size:11px}.import-row input{position:absolute;width:1px;height:1px;opacity:0}.import-confirm{justify-self:start}.workbench-grid{display:grid;grid-template-columns:minmax(0,1.2fr) minmax(300px,.8fr);gap:12px}.file-panel,.evidence-panel{display:grid;align-content:start;gap:12px;min-width:0}.file-tabs{display:flex;gap:6px;overflow:auto}.file-tabs button{padding:7px 10px;border:1px solid var(--color-border-soft);border-radius:6px;background:var(--color-row);color:var(--color-muted);cursor:pointer;white-space:nowrap}.file-tabs button.active{border-color:var(--color-border-strong);background:var(--color-token-bg);color:var(--color-token-text)}.file-panel textarea{width:100%;min-height:370px;resize:vertical;padding:14px;border:1px solid var(--color-border);border-radius:6px;background:var(--color-input);color:var(--color-text);font:13px/1.6 ui-monospace,SFMono-Regular,Consolas,monospace}.editor-hint{margin:0;font-size:11px}.check-list{display:grid;gap:8px}.check-row{display:grid;gap:5px;padding:10px;border-left:3px solid var(--color-border);background:var(--color-row)}.check-row.passed{border-left-color:var(--color-success)}.check-row.failed{border-left-color:var(--color-danger)}.check-row strong,.check-row span,.check-row small{display:block}.check-row strong{font-size:12px}.check-row span,.check-row small{margin-top:3px;color:var(--color-muted);font-size:11px;line-height:1.45}.empty-evidence{display:grid;gap:5px;min-height:160px;place-items:center;padding:20px;text-align:center;background:var(--color-row)}.empty-evidence strong{color:var(--color-heading)}.empty-evidence span{max-width:250px;font-size:12px}.mentor-box{display:grid;gap:8px;padding-top:12px;border-top:1px solid var(--color-border-soft)}.mentor-box textarea{resize:vertical;padding:9px;border:1px solid var(--color-border);border-radius:6px;background:var(--color-input);color:var(--color-text)}.mentor-feedback{margin:0;padding:9px;background:var(--color-warning-bg);color:var(--color-warning-text);font-size:12px;line-height:1.6}.primary-button,.secondary-button,.publish-button{border:0;border-radius:6px;padding:9px 12px;cursor:pointer;font-weight:800}.primary-button{background:var(--color-primary);color:var(--color-on-primary)}.secondary-button{background:var(--color-surface-muted);color:var(--color-primary-strong)}.publish-button{background:var(--color-accent);color:var(--color-on-primary)}.primary-button:disabled,.secondary-button:disabled,.publish-button:disabled{opacity:.55;cursor:wait}.publish-note{color:var(--color-success);font-weight:700}.status{margin:0;padding:10px 12px;border-radius:6px;background:var(--color-surface-muted);color:var(--color-muted);font-size:12px}.status.error{background:var(--color-danger-bg);color:var(--color-danger-text)}.status.success{background:var(--color-success-bg);color:var(--color-success-text)}@media(max-width:1100px){.learning-hero{display:grid}.hero-metrics{justify-content:space-between}.learning-grid,.workbench-grid{grid-template-columns:1fr}}@media(max-width:720px){.task-meta-grid{grid-template-columns:1fr}.import-row{align-items:flex-start}.learning-hero{padding:16px}.learning-hero h2{font-size:22px}.hero-metrics{gap:10px}.hero-metrics strong{font-size:20px}.panel{padding:12px}.file-panel textarea{min-height:280px}}
</style>
