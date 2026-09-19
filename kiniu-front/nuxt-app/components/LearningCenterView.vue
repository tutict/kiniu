<script setup lang="ts">
import type {
  LearningCatalog,
  LearningProgress,
  LearningTask,
  LearningCheckResponse,
  TaskCheckResult,
  LearningPublishResponse
} from '../types/learning'
import { useUiI18n, type I18nKey } from '../i18n'
import UiButton from './ui/UiButton.vue'
import UiTabs from './ui/UiTabs.vue'
import UiEmptyState from './ui/UiEmptyState.vue'
import {
  buildLearningRequestHeaders,
  type LearningRequestKind
} from '../utils/learningRequestHeaders'
import { classifyRequestFailure } from '../utils/requestFailure'

const props = defineProps<{
  backendUrl: string
  localToken: string
  providerUrl: string
  apiKey: string
  model: string
}>()
const emit = defineEmits<{
  (event: 'use-agent', agentId: string): void
  (event: 'open-settings'): void
}>()
const { t } = useUiI18n()

const DRAFT_STORAGE_KEY = 'kiniu.learn.taskDrafts'
const QUIZ_DRAFT_STORAGE_KEY = 'kiniu.learn.quizDrafts'
const catalog = ref<LearningCatalog | null>(null)
const progress = ref<LearningProgress | null>(null)
const selectedTaskId = ref('')
const files = ref<Record<string, string>>({})
const activeFile = ref('')
const drafts = ref<Record<string, Record<string, string>>>({})
const quizDrafts = ref<Record<string, Record<string, string>>>({})
const quizAnswers = ref<Record<string, string>>({})
const submittedAnswers = ref<Record<string, string>>({})
const results = ref<TaskCheckResult[]>([])
const attemptId = ref('')
const loading = ref(false)
const checking = ref(false)
const mentorChecking = ref(false)
const publishing = ref(false)
const error = ref('')
const errorAction = ref<'' | 'settings'>('')
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
const fileTabItems = computed(() => Object.keys(files.value).map(path => ({ id: path, label: path })))
const selectedTask = computed<LearningTask | null>(() =>
  tasks.value.find(task => task.id === selectedTaskId.value) ?? tasks.value[0] ?? null)
const completed = computed(() => new Set(progress.value?.completedTaskIds ?? []))
const score = computed(() => progress.value?.bestScores[selectedTaskId.value] ?? 0)
const progressPercent = computed(() => tasks.value.length
  ? Math.round((completed.value.size / tasks.value.length) * 100)
  : 0)
const selectedTaskIndex = computed(() => Math.max(0, tasks.value.findIndex(task => task.id === selectedTask.value?.id)))
const selectedModule = computed(() => catalog.value?.modules.find(module =>
  module.tasks.some(task => task.id === selectedTask.value?.id)) ?? null)

const isQuizTask = computed(() => selectedTask.value?.evidenceMode === 'quiz')
const quizQuestions = computed(() => selectedTask.value?.quizQuestions ?? [])
const acceptanceCount = computed(() => isQuizTask.value
  ? quizQuestions.value.length
  : (selectedTask.value?.checks.length ?? 0))
const quizAnsweredCount = computed(() => quizQuestions.value.filter(question => Boolean(quizAnswers.value[question.id])).length)
const quizComplete = computed(() => quizQuestions.value.length > 0
  && quizAnsweredCount.value === quizQuestions.value.length)
const canRunChecks = computed(() => {
  if (!selectedTask.value || checking.value || publishing.value) return false
  return isQuizTask.value ? quizComplete.value : true
})
const lessonSections = computed(() => {
  const lesson = selectedTask.value?.lesson?.trim() ?? ''
  if (!lesson) return []
  const sections: { title: string; body: string }[] = []
  let current: { title: string; body: string } | null = null
  for (const rawLine of lesson.split(/\r?\n/)) {
    const heading = rawLine.match(/^#{1,6}\s+(.+)$/)
    if (heading) {
      if (current) sections.push({ ...current, body: current.body.trim() })
      current = { title: heading[1].trim(), body: '' }
      continue
    }
    if (!current) current = { title: '', body: '' }
    current.body += (current.body ? '\n' : '') + rawLine
  }
  if (current) sections.push({ ...current, body: current.body.trim() })
  return sections.filter(section => section.title || section.body)
})

function learningHeaders(kind: LearningRequestKind) {
  return buildLearningRequestHeaders(kind, {
    localToken: props.localToken,
    providerUrl: props.providerUrl,
    apiKey: props.apiKey,
    model: props.model
  })
}

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

function clearRequestError() {
  error.value = ''
  errorAction.value = ''
}

function showRequestFailure(requestError: unknown, fallbackKey: I18nKey) {
  const kind = classifyRequestFailure(requestError)
  if (kind === 'unauthorized') {
    error.value = t('learningAuthRequired')
    errorAction.value = 'settings'
    return
  }
  if (kind === 'forbidden') {
    error.value = t('learningOriginForbidden')
    errorAction.value = 'settings'
    return
  }
  error.value = requestError instanceof Error ? requestError.message : t(fallbackKey)
  errorAction.value = ''
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
  const restoredAnswers = quizDrafts.value[task.id]
  if (task.evidenceMode === 'quiz') {
    files.value = {}
    activeFile.value = ''
    quizAnswers.value = restoredAnswers ? { ...restoredAnswers } : {}
  } else {
    files.value = restored ? { ...restored } : Object.fromEntries(task.starterFiles.map(file => [file.path, file.content]))
    activeFile.value = task.starterFiles[0]?.path ?? ''
    quizAnswers.value = {}
  }
  submittedAnswers.value = {}
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
  try {
    quizDrafts.value = JSON.parse(localStorage.getItem(QUIZ_DRAFT_STORAGE_KEY) || '{}')
  } catch {
    quizDrafts.value = {}
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
    errorAction.value = 'settings'
    return
  }
  const generation = ++requestGeneration
  loading.value = true
  clearRequestError()
  try {
    assertSafeSecretTarget()
    const [nextCatalog, nextProgress] = await Promise.all([
      $fetch<LearningCatalog>('/learn/catalog', { baseURL: props.backendUrl.trim(), headers: learningHeaders('catalog') }),
      $fetch<LearningProgress>('/learn/progress', { baseURL: props.backendUrl.trim(), headers: learningHeaders('progress') })
    ])
    if (generation !== requestGeneration) return
    catalog.value = nextCatalog
    progress.value = nextProgress
    selectedTaskId.value = firstAvailableTask(nextCatalog, nextProgress)
    notice.value = ""
  } catch (requestError) {
    if (generation === requestGeneration) {
      showRequestFailure(requestError, 'learningCatalogLoadFailed')
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

function saveQuizDraft() {
  if (!selectedTask.value) return
  quizDrafts.value[selectedTask.value.id] = { ...quizAnswers.value }
  localStorage.setItem(QUIZ_DRAFT_STORAGE_KEY, JSON.stringify(quizDrafts.value))
}

function selectQuizAnswer(questionId: string, optionId: string) {
  quizAnswers.value = { ...quizAnswers.value, [questionId]: optionId }
  saveQuizDraft()
}

function optionSubmitted(questionId: string, optionId: string) {
  return submittedAnswers.value[questionId] === optionId
}

function quizResultFor(questionId: string) {
  return results.value.find(result => result.checkId === questionId)
}

async function checkTask() {
  const task = selectedTask.value
  if (!task || !props.backendUrl.trim() || !taskUnlocked(task)) return
  if (task.evidenceMode === 'quiz') {
    saveQuizDraft()
    if (!quizComplete.value) {
      error.value = t('learningAnswerAll')
      return
    }
  } else {
    saveDraft()
  }
  const taskId = task.id
  const sequence = ++requestSequence
  const generation = requestGeneration
  checking.value = true
  clearRequestError()
  notice.value = ''
  try {
    assertSafeSecretTarget()
    const response = await $fetch<LearningCheckResponse>(
      `/learn/tasks/${encodeURIComponent(taskId)}/check`,
      {
        baseURL: props.backendUrl.trim(),
        method: 'POST',
        headers: learningHeaders('check'),
        body: task.evidenceMode === 'quiz' ? { answers: quizAnswers.value } : { files: files.value }
      })
    if (generation !== requestGeneration) return
    progress.value = response.progress
    if (selectedTaskId.value !== taskId || sequence !== requestSequence) return
    results.value = response.results
    attemptId.value = response.attemptId
    if (task.evidenceMode === 'quiz') {
      submittedAnswers.value = { ...quizAnswers.value }
    }
    notice.value = response.passed
      ? t('learningCheckPassed', { score: response.score })
      : t('learningCheckFailed', { score: response.score })
  } catch (requestError) {
    if (generation === requestGeneration && selectedTaskId.value === taskId) {
      showRequestFailure(requestError, 'learningCheckRequestFailed')
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
  clearRequestError()
  try {
    assertSafeSecretTarget(true)
    const response = await $fetch<{ feedback: string }>(
      `/learn/tasks/${encodeURIComponent(taskId)}/feedback`,
      {
        baseURL: props.backendUrl.trim(),
        method: 'POST',
        headers: learningHeaders('feedback'),
        body: { attemptId: currentAttemptId, question: mentorQuestion.value }
      })
    if (generation === requestGeneration && selectedTaskId.value === taskId && attemptId.value === currentAttemptId) {
      mentorFeedback.value = response.feedback
    }
  } catch (requestError) {
    if (generation === requestGeneration && selectedTaskId.value === taskId) {
      showRequestFailure(requestError, 'learningMentorRequestFailed')
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
  clearRequestError()
  try {
    assertSafeSecretTarget()
    const response = await $fetch<LearningPublishResponse>(
      `/learn/tasks/${encodeURIComponent(taskId)}/publish-agent`,
      {
        baseURL: props.backendUrl.trim(),
        method: 'POST',
        headers: learningHeaders('publish'),
        body: { attemptId: currentAttemptId }
      })
    if (generation === requestGeneration && selectedTaskId.value === taskId) {
      publishedAgentName.value = response.agent.name
      notice.value = t('learningPublished', { name: response.agent.name })
      emit('use-agent', response.agent.id)
    }
  } catch (requestError) {
    if (generation === requestGeneration && selectedTaskId.value === taskId) {
      showRequestFailure(requestError, 'learningPublishFailed')
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
      <div class="hero-intro">
        <h2>{{ t('learningTitle') }}</h2>
        <p class="hero-copy">{{ t('learningLabs', { count: tasks.length || 21 }) }} · {{ t('learningDuration') }}</p>
      </div>
      <div class="hero-progress-card">
        <div class="progress-label"><span>{{ t('learningProgress') }}</span><strong>{{ progressPercent }}%</strong></div>
        <div class="progress-track" aria-hidden="true"><span :style="{ width: `${progressPercent}%` }" /></div>
        <p class="hero-copy">{{ progress?.completedTaskIds.length ?? 0 }}/{{ tasks.length || 21 }} {{ t('learningCompleted') }}</p>
      </div>
    </header>

    <p v-if="loading" class="status">{{ t('learningLoading') }}</p>
    <div v-if="error" class="status error" role="alert">
      <span>{{ error }}</span>
      <button v-if="errorAction === 'settings'" type="button" @click="emit('open-settings')">
        {{ t('learningOpenSettings') }}
      </button>
    </div>
    <p v-if="notice" class="status success">{{ notice }}</p>

    <div v-if="catalog" class="learning-grid">
      <aside class="course-rail">
        <div class="course-rail-head"><strong>{{ t('learningSyllabus') }}</strong><span>{{ completed.size }}/{{ tasks.length }}</span></div>
        <div v-for="(module, moduleIndex) in catalog.modules" :key="module.id" class="module-block">
          <div class="module-heading"><span class="module-index">{{ moduleIndex + 1 }}</span><div><small>{{ module.level }}</small><strong>{{ module.title }}</strong></div></div>
          <p>{{ module.summary }}</p>
          <button
            v-for="(task, taskIndex) in module.tasks"
            :key="task.id"
            class="task-button"
            :class="{ active: selectedTask?.id === task.id, locked: !taskUnlocked(task) }"
            type="button"
            :disabled="!taskUnlocked(task) || checking || publishing"
            :title="taskUnlocked(task) ? task.title : t('learningLockedPrerequisites', { prerequisites: prerequisiteTitles(task).join('、') || t('learningPreviousTask') })"
            @click="selectTask(task)"
          >
            <span class="task-state" :class="{ done: completed.has(task.id), locked: !taskUnlocked(task) }">
              {{ completed.has(task.id) ? '✓' : taskUnlocked(task) ? taskIndex + 1 : '—' }}
            </span>
            <span>
              <strong>{{ task.title }}</strong>
              <span class="task-badges">
                <small>{{ t('learningMinutes', { minutes: task.estimatedMinutes, level: task.level }) }}</small>
              </span>
              <small v-if="!taskUnlocked(task)" class="lock-reason">{{ t('learningLockedPrerequisites', { prerequisites: prerequisiteTitles(task).join('、') || t('learningPreviousTask') }) }}</small>
            </span>
          </button>
        </div>
      </aside>

      <main v-if="selectedTask" class="task-workspace">
        <header class="lab-context-bar">
          <nav class="lab-breadcrumb" :aria-label="t('learningSyllabus')">
            <span>{{ selectedModule?.title }}</span>
          </nav>
          <div class="lab-context-status">
            <span :class="['live-state', { complete: completed.has(selectedTask.id) }]">
              {{ completed.has(selectedTask.id) ? t('learningCompletedMark') : t('learningInProgress') }}
            </span>
          </div>
        </header>

        <section id="lab-brief" class="task-brief panel">
          <div class="section-heading">
            <h3>{{ selectedTask.title }}</h3>
            <span class="score-chip">{{ score || '—' }}</span>
          </div>
          <p class="summary">{{ selectedTask.summary }}</p>
          <div class="task-facts">
            <div><small>{{ t('learningDifficulty') }}</small><strong>{{ selectedTask.level }}</strong></div>
            <div><small>{{ t('learningTime') }}</small><strong>{{ selectedTask.estimatedMinutes }} {{ t('learningMinuteUnit') }}</strong></div>
            <div><small>{{ t('learningChecks') }}</small><strong>{{ acceptanceCount }}</strong></div>
          </div>
          <div class="objective"><strong>{{ t('learningObjective') }}</strong><span>{{ selectedTask.objective }}</span></div>
          <div class="scenario"><strong>{{ t('learningScenario') }}</strong><span>{{ selectedTask.scenario }}</span></div>
          <div class="skill-list"><span v-for="skill in selectedTask.skills" :key="skill">{{ skill }}</span></div>
          <div class="lesson-block">
            <p class="eyebrow">{{ t('learningLesson') }}</p>
            <div class="lesson-copy">
              <section v-for="(section, index) in lessonSections" :key="`${section.title}-${index}`" class="lesson-section">
                <strong v-if="section.title">{{ section.title }}</strong>
                <p v-if="section.body">{{ section.body }}</p>
              </section>
            </div>
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
              <div>
                <h3>{{ isQuizTask ? t('learningWorkspaceQuiz') : t('learningWorkspace') }}</h3>
                <p v-if="isQuizTask" class="quiz-progress">{{ t('learningQuizProgress', { answered: quizAnsweredCount, total: quizQuestions.length }) }}</p>
              </div>
              <UiButton variant="primary" size="sm" :disabled="!canRunChecks" @click="checkTask">
                {{ checking ? t('learningChecking') : t('learningRunChecks') }}
              </UiButton>
            </div>
            <template v-if="isQuizTask">
              <p class="editor-hint">{{ t('learningQuizMode') }}</p>
              <div class="quiz-list">
                <article v-for="(question, index) in quizQuestions" :key="question.id" class="quiz-question">
                  <p class="quiz-prompt"><span class="quiz-index">{{ index + 1 }}</span>{{ question.prompt }}</p>
                  <div class="quiz-options">
                    <label
                      v-for="option in question.options"
                      :key="option.id"
                      :class="['quiz-option', {
                        selected: quizAnswers[question.id] === option.id,
                        passed: optionSubmitted(question.id, option.id) && quizResultFor(question.id)?.passed,
                        failed: optionSubmitted(question.id, option.id) && quizResultFor(question.id) && !quizResultFor(question.id)?.passed
                      }]"
                    >
                      <input
                        type="radio"
                        :name="`quiz-${question.id}`"
                        :value="option.id"
                        :checked="quizAnswers[question.id] === option.id"
                        :disabled="checking || publishing"
                        @change="selectQuizAnswer(question.id, option.id)"
                      />
                      <span>{{ option.label }}</span>
                    </label>
                  </div>
                </article>
              </div>
              <p class="editor-hint">{{ t('learningQuizHint') }}</p>
            </template>
            <template v-else>
              <UiTabs
                :model-value="activeFile"
                :items="fileTabItems"
                variant="underline"
                :aria-label="t('learningWorkspace')"
                @update:model-value="selectActiveFile"
              />
              <div class="import-row">
                <input id="learning-file-import" type="file" accept=".json,.md,.markdown,.txt,application/json,text/markdown,text/plain" @change="importActiveFile" />
                <label for="learning-file-import" class="file-import-label">{{ t('learningImportFile') }}</label>
                <span>{{ selectedTask.evidenceMode === 'import' ? t('learningImportMode') : t('learningDocumentMode') }}</span>
              </div>
              <p v-if="importFileInfo" class="status success">{{ importFileInfo }}</p>
              <UiButton v-if="pendingImport" class="import-confirm" variant="secondary" size="sm" @click="confirmImport">{{ t('learningImportConfirm') }}</UiButton>
              <p v-if="importError" class="status error">{{ importError }}</p>
              <textarea :value="files[activeFile]" spellcheck="false" @input="updateActiveFile" />
              <p class="editor-hint">{{ t('learningEditorHint') }}</p>
            </template>
          </div>

          <div class="panel evidence-panel">
            <div class="section-heading"><h3>{{ t('learningChecklist') }}</h3><span>{{ results.length }}/{{ acceptanceCount }}</span></div>
            <div v-if="results.length" class="check-list">
              <article v-for="result in results" :key="result.checkId" :class="['check-row', result.passed ? 'passed' : 'failed']">
                <strong>{{ result.passed ? t('learningPassed') : t('learningNeedsFix') }}</strong>
                <span>{{ result.message }}</span>
                <small>{{ result.evidence }}</small>
              </article>
            </div>
            <div v-else><UiEmptyState :title="t('learningNoEvidence')" :copy="isQuizTask ? t('learningNoEvidenceCopyQuiz') : t('learningNoEvidenceCopy')" /></div>
            <div class="mentor-box">
              <p class="eyebrow">{{ t('learningMentor') }}</p>
              <textarea v-model="mentorQuestion" :placeholder="t('learningMentorPlaceholder')" rows="3" />
              <UiButton variant="secondary" size="sm" :disabled="!attemptId || mentorChecking || checking" @click="askMentor">
                {{ mentorChecking ? t('learningRequesting') : t('learningRequestExplanation') }}
              </UiButton>
              <p v-if="mentorFeedback" class="mentor-feedback">{{ mentorFeedback }}</p>
              <UiButton v-if="selectedTask.kind === 'agent-project' && results.length && attemptId && progress?.completedTaskIds.includes(selectedTask.id)" variant="accent" size="sm" :disabled="publishing" @click="publishAgent">
                {{ publishing ? t('learningPublishing') : t('learningPublishUse') }}
              </UiButton>
              <small v-if="publishedAgentName" class="publish-note">{{ publishedAgentName }}</small>
            </div>
          </div>
        </section>
      </main>
    </div>
  </section>
</template>

<style scoped>
.learning-view {
  display: grid;
  gap: var(--space-5);
  align-content: start;
  min-width: 0;
  min-height: 0;
}
.learning-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(220px, .5fr);
  gap: var(--space-6);
  align-items: center;
  overflow: hidden;
  padding: 18px 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface-panel);
}
.learning-hero::before {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  width: 5px;
  background: var(--color-accent);
}
.hero-intro {
  display: grid;
  align-content: start;
  justify-items: start;
  gap: var(--space-2);
  min-width: 0;
}
.course-tag {
  display: inline-flex;
  padding: 5px 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-row);
  color: var(--color-primary-strong);
  font-size: 11px;
  font-weight: var(--font-bold);
}
.eyebrow {
  margin: 0;
  color: var(--color-primary-strong);
  font-size: 10px;
  font-weight: 900;
  letter-spacing: .04em;
  text-transform: uppercase;
}
.learning-hero h2 {
  margin: 0;
  color: var(--color-heading);
  font-size: var(--text-xl);
  font-weight: var(--font-bold);
  line-height: 1.2;
}
.hero-copy,
.summary,
.editor-hint,
.module-block p {
  color: var(--color-muted);
  line-height: 1.6;
}
.hero-copy {
  margin: 0;
  font-size: var(--text-xs);
}
.course-meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  margin-top: 6px;
  color: var(--color-muted);
  font-size: var(--text-xs);
  font-weight: 700;
}
.course-meta-line span {
  padding: 0 12px;
  border-left: 1px solid var(--color-border);
}
.course-meta-line span:first-child {
  padding-left: 0;
  border-left: 0;
}
.hero-progress-card {
  display: grid;
  align-content: center;
  gap: var(--space-2);
  padding-left: var(--space-5);
  border-left: 1px solid var(--color-border);
}
.progress-label {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: var(--space-3);
  color: var(--color-muted);
  font-size: var(--text-xs);
  font-weight: 700;
}
.progress-label strong {
  color: var(--color-heading);
  font-size: var(--text-lg);
  line-height: 1;
}
.progress-track {
  height: 8px;
  overflow: hidden;
  border-radius: var(--radius-sm);
  background: var(--color-token-muted-bg);
}
.progress-track span {
  display: block;
  height: 100%;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  transition: width var(--duration-base) var(--ease);
}
.hero-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  padding-top: var(--space-3);
  border-top: 1px solid var(--color-border-soft);
}
.hero-metrics div {
  display: grid;
  gap: 3px;
  min-width: 0;
  padding-right: var(--space-3);
}
.hero-metrics div + div {
  padding-left: var(--space-3);
  border-left: 1px solid var(--color-border-soft);
}
.hero-metrics strong {
  color: var(--color-heading);
  font-size: 16px;
}
.hero-metrics span {
  color: var(--color-faint);
  font-size: 10px;
}
.learning-grid {
  display: grid;
  grid-template-columns: 310px minmax(0, 1fr);
  gap: var(--space-5);
  align-items: start;
  min-height: 0;
}
.course-rail {
  position: sticky;
  top: 92px;
  max-height: calc(100dvh - 112px);
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface-panel);
  scrollbar-gutter: stable;
}
.course-rail-head {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface-panel);
}
.course-rail-head strong {
  color: var(--color-heading);
  font-size: var(--text-sm);
}
.course-rail-head span {
  color: var(--color-primary-strong);
  font: 700 11px/1 ui-monospace, SFMono-Regular, Consolas, monospace;
}
.module-block {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-4);
  border-bottom: 1px solid var(--color-border-soft);
}
.module-block:last-child {
  border-bottom: 0;
}
.module-heading {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr);
  gap: 9px;
  align-items: start;
}
.module-heading div {
  display: grid;
  gap: 3px;
}
.module-heading small {
  color: var(--color-faint);
  font-size: 10px;
  font-weight: 700;
}
.module-heading strong {
  color: var(--color-heading);
  font-size: var(--text-sm);
  line-height: 1.3;
}
.module-heading .module-index {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-row);
  color: var(--color-primary-strong);
  font: 800 11px/1 ui-monospace, SFMono-Regular, Consolas, monospace;
}
.module-block p {
  margin: 0;
  padding-left: 35px;
  font-size: 11px;
  line-height: 1.5;
}
.task-button {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 9px;
  align-items: start;
  width: 100%;
  padding: 9px 9px 9px 7px;
  border: 1px solid transparent;
  border-left: 3px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
}
.task-button:hover:not(:disabled) {
  border-color: var(--color-border-strong);
  background: var(--color-hover);
}
.task-button.active {
  border-color: var(--color-primary);
  background: var(--color-token-bg);
}
.task-button:disabled {
  opacity: .62;
  cursor: not-allowed;
}
.task-button strong {
  display: block;
}
.task-button small {
  display: block;
  margin-top: 3px;
  color: var(--color-faint);
  font-size: 11px;
}
.task-button .lock-reason {
  color: var(--color-warning-text);
  line-height: 1.4;
  white-space: normal;
}
.task-state {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-faint);
  font: 800 10px/1 ui-monospace, SFMono-Regular, Consolas, monospace;
}
.task-state.done {
  border-color: var(--color-success-border);
  background: var(--color-success-bg);
  color: var(--color-success);
}
.task-workspace {
  display: grid;
  gap: var(--space-4);
  min-width: 0;
}
.lab-context-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}
.lab-breadcrumb,
.lab-context-status {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  color: var(--color-faint);
  font-size: 11px;
}
.lab-breadcrumb span:last-child {
  overflow: hidden;
  color: var(--color-heading);
  font-weight: var(--font-bold);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lab-context-status {
  flex: 0 0 auto;
}
.lab-context-status strong {
  color: var(--color-heading);
  font: 800 11px/1 ui-monospace, SFMono-Regular, Consolas, monospace;
}
.live-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-primary-strong);
  font-weight: var(--font-bold);
}
.live-state::before {
  content: "";
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-accent) 16%, transparent);
}
.live-state.complete {
  color: var(--color-success);
}
.live-state.complete::before {
  background: var(--color-success);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-success) 16%, transparent);
}
.panel {
  padding: var(--space-5);
}
.task-brief {
  border-top: 3px solid var(--color-primary);
}
.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: var(--space-3);
}
.section-heading h3 {
  margin: 3px 0 0;
  color: var(--color-heading);
  font-size: 20px;
  line-height: 1.3;
}
.score-chip {
  padding: 5px 9px;
  border-radius: var(--radius-sm);
  background: var(--color-token-bg);
  color: var(--color-token-text);
  font-size: var(--text-xs);
  font-weight: var(--font-bold);
}
.summary {
  margin: 8px 0 0;
}
.task-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-row);
}
.task-facts div {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
  padding: 11px 13px;
}
.task-facts div + div {
  border-left: 1px solid var(--color-border-soft);
}
.task-facts small {
  color: var(--color-faint);
  font-size: 10px;
  font-weight: 700;
}
.task-facts strong {
  overflow: hidden;
  color: var(--color-heading);
  font-size: var(--text-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.objective,
.scenario {
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr);
  gap: var(--space-3);
  align-items: start;
  margin-top: 10px;
  padding: 11px 13px;
  border-left: 4px solid var(--color-primary);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  background: var(--color-surface-muted);
  font-size: var(--text-sm);
  line-height: 1.5;
}
.scenario {
  border-left-color: var(--color-accent);
}
.objective strong,
.scenario strong {
  font-size: 11px;
  color: var(--color-primary-strong);
}
.skill-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: var(--space-3);
}
.skill-list span {
  padding: 5px 8px;
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-sm);
  background: var(--color-row);
  color: var(--color-muted);
  font-size: 11px;
}
.lesson-block {
  display: grid;
  gap: 6px;
  margin-top: var(--space-4);
  padding-top: 14px;
  border-top: 1px solid var(--color-border-soft);
}
.lesson-copy {
  display: grid;
  gap: var(--space-4);
  color: var(--color-muted);
  font-size: var(--text-base);
  line-height: 1.75;
}
.lesson-section {
  display: grid;
  gap: 6px;
}
.lesson-section strong {
  color: var(--color-heading);
  font-size: var(--text-sm);
}
.lesson-section p {
  margin: 0;
  white-space: pre-line;
}
.task-meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-6);
  margin-top: 14px;
  color: var(--color-muted);
  font-size: var(--text-xs);
  line-height: 1.5;
}
.task-meta-grid strong,
.reference-list strong {
  display: block;
  color: var(--color-heading);
  font-size: 11px;
}
.task-meta-grid ul {
  margin: 6px 0 0;
  padding-left: 18px;
}
.reference-list {
  display: grid;
  gap: 5px;
  margin-top: 14px;
  padding-top: var(--space-3);
  border-top: 1px solid var(--color-border-soft);
  font-size: 11px;
}
.reference-list a {
  padding: 4px 0;
  color: var(--color-primary-strong);
  text-decoration: none;
}
.reference-list a:hover {
  text-decoration: underline;
}
.workbench-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(340px, .65fr);
  gap: var(--space-5);
  align-items: start;
}
.file-panel,
.evidence-panel {
  display: grid;
  align-content: start;
  gap: var(--space-3);
  min-width: 0;
}
.file-panel {
  border-top: 3px solid var(--color-accent);
}
.evidence-panel {
  position: sticky;
  top: 92px;
  max-height: calc(100dvh - 112px);
  overflow: auto;
  scrollbar-gutter: stable;
}
.import-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--color-muted);
  font-size: 11px;
}
.file-import-label {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 7px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
  color: var(--color-primary-strong);
  font-weight: var(--font-bold);
  cursor: pointer;
  transition: background var(--duration-base) var(--ease), border-color var(--duration-base) var(--ease);
}
.file-import-label:hover {
  border-color: var(--color-primary);
  background: var(--color-hover);
}
.import-row input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}
.import-confirm {
  justify-self: start;
}
.quiz-progress {
  margin: 4px 0 0;
  color: var(--color-muted);
  font-size: 11px;
  font-weight: 700;
}
.quiz-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.4em;
  margin-right: 8px;
  color: var(--color-primary-strong);
  font-variant-numeric: tabular-nums;
}
.quiz-list {
  display: grid;
  gap: var(--space-4);
}
.quiz-question {
  display: grid;
  gap: 8px;
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--color-border-soft);
}
.quiz-question:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}
.quiz-prompt {
  margin: 0;
  color: var(--color-heading);
  font-size: var(--text-xs);
  font-weight: 700;
  line-height: 1.55;
}
.quiz-options {
  display: grid;
  gap: 6px;
}
.quiz-option {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 9px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-input);
  color: var(--color-text);
  font-size: var(--text-xs);
  line-height: 1.5;
  cursor: pointer;
}
.quiz-option input {
  margin-top: 2px;
  accent-color: var(--color-primary);
}
.quiz-option.selected {
  border-color: var(--color-primary);
  background: var(--color-hover);
}
.quiz-option.passed {
  border-color: var(--color-success);
}
.quiz-option.failed {
  border-color: var(--color-danger);
}
.file-panel textarea {
  width: 100%;
  min-height: 470px;
  resize: vertical;
  padding: 14px;
  border: 1px solid var(--color-code-border);
  border-radius: var(--radius);
  background: var(--color-code-bg);
  color: var(--color-code-text);
  caret-color: var(--color-code-caret);
  font: 13px/1.6 ui-monospace, SFMono-Regular, Consolas, monospace;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .04);
}
.editor-hint {
  margin: 0;
  font-size: 11px;
}
.check-list {
  display: grid;
  gap: var(--space-2);
}
.check-row {
  display: grid;
  gap: 5px;
  padding: 10px;
  border-left: 3px solid var(--color-border);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  background: var(--color-row);
}
.check-row.passed {
  border-left-color: var(--color-success);
}
.check-row.failed {
  border-left-color: var(--color-danger);
}
.check-row strong {
  display: block;
  font-size: var(--text-xs);
}
.check-row span,
.check-row small {
  display: block;
  color: var(--color-muted);
  font-size: 11px;
  line-height: 1.45;
}
.mentor-box {
  display: grid;
  gap: var(--space-2);
  padding-top: var(--space-3);
  border-top: 1px solid var(--color-border-soft);
}
.mentor-box textarea {
  resize: vertical;
  padding: 9px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-input);
  color: var(--color-text);
}
.mentor-feedback {
  margin: 0;
  padding: 9px;
  background: var(--color-warning-bg);
  color: var(--color-warning-text);
  font-size: var(--text-xs);
  line-height: 1.6;
}
.publish-note {
  color: var(--color-success);
  font-weight: 700;
}
@media (max-width: 1180px) {
  .learning-grid,
  .workbench-grid {
    grid-template-columns: 1fr;
  }
  .evidence-panel,
  .course-rail {
    position: static;
    max-height: none;
  }
  .course-rail {
    max-height: 430px;
  }
}
@media (max-width: 760px) {
  .learning-hero {
    grid-template-columns: 1fr;
    gap: 22px;
    padding: 22px 18px;
  }
  .learning-hero h2 {
    font-size: 27px;
  }
  .hero-progress-card {
    padding: 18px 0 0;
    border-top: 1px solid var(--color-border);
    border-left: 0;
  }
  .course-meta-line {
    gap: 7px;
  }
  .course-meta-line span {
    padding: 0;
    border: 0;
  }
  .task-meta-grid,
  .objective,
  .scenario {
    grid-template-columns: 1fr;
  }
  .lab-context-bar {
    align-items: flex-start;
    flex-direction: column;
  }
  .lab-context-status {
    width: 100%;
    justify-content: space-between;
  }
  .task-facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .task-facts div:nth-child(3) {
    border-left: 0;
    border-top: 1px solid var(--color-border-soft);
  }
  .panel {
    padding: 15px;
  }
  .file-panel textarea {
    min-height: 330px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .progress-track span,
  .file-import-label {
    transition: none;
  }
}
</style>

