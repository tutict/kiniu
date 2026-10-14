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
  (event: 'use-agent', agentId: string, prompt?: string): void
  (event: 'try-evening-plan', prompt?: string, takeaway?: string): void
  (event: 'open-settings'): void
}>()
const { t } = useUiI18n()

const DRAFT_STORAGE_KEY = 'kiniu.learn.taskDrafts'
const QUIZ_DRAFT_STORAGE_KEY = 'kiniu.learn.quizDrafts'
const RETURN_TASK_STORAGE_KEY = 'kiniu.learn.returnTaskId'
const catalog = ref<LearningCatalog | null>(null)
const progress = ref<LearningProgress | null>(null)
const selectedTaskId = ref('')
const files = ref<Record<string, string>>({})
const activeFile = ref('')
const drafts = ref<Record<string, Record<string, string>>>({})
const quizDrafts = ref<Record<string, Record<string, string>>>({})
const quizAnswers = ref<Record<string, string>>({})
const quizIndex = ref(0)
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
const noticeTone = ref<'success' | 'error'>('success')
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

const scenarioSentences = computed(() => {
  const text = selectedTask.value?.scenario?.trim() ?? ''
  if (!text) return []
  return text.split(/(?<=。)/).map(sentence => sentence.trim()).filter(Boolean)
})

const completed = computed(() => new Set(progress.value?.completedTaskIds ?? []))
const score = computed(() => progress.value?.bestScores[selectedTaskId.value] ?? 0)
const coreTasks = computed(() => tasks.value.filter(task => !task.elective))
const electiveTasks = computed(() => tasks.value.filter(task => task.elective))
const coreCompletedCount = computed(() => coreTasks.value.filter(task => completed.value.has(task.id)).length)
const electiveCompletedCount = computed(() => electiveTasks.value.filter(task => completed.value.has(task.id)).length)
const coreComplete = computed(() => coreTasks.value.length > 0 && coreCompletedCount.value === coreTasks.value.length)
const progressPercent = computed(() => coreTasks.value.length
  ? Math.round((coreCompletedCount.value / coreTasks.value.length) * 100)
  : 0)
const selectedTaskIndex = computed(() => Math.max(0, tasks.value.findIndex(task => task.id === selectedTask.value?.id)))
const selectedModule = computed(() => catalog.value?.modules.find(module =>
  module.tasks.some(task => task.id === selectedTask.value?.id)) ?? null)
const extraOpenModules = ref<string[]>([])
const courseTracks = computed(() => {
  const modules = catalog.value?.modules ?? []
  const toTrack = (id: 'core' | 'elective', title: string, elective: boolean) => ({
    id,
    title,
    completed: tasks.value.filter(task => Boolean(task.elective) === elective && completed.value.has(task.id)).length,
    total: tasks.value.filter(task => Boolean(task.elective) === elective).length,
    modules: modules
      .map(module => ({ ...module, tasks: module.tasks.filter(task => Boolean(task.elective) === elective) }))
      .filter(module => module.tasks.length)
  })
  const tracks = [toTrack('core', t('learningCoreSection'), false)]
  const electives = toTrack('elective', t('learningElectiveSection'), true)
  if (electives.total) tracks.push(electives)
  return tracks
})
const visibleTracks = computed(() => courseTracks.value
  .filter(track => track.id === 'core' || coreComplete.value || track.completed > 0)
  .map(track => ({
    ...track,
    modules: track.modules.filter(module => module.tasks.some(task => taskUnlocked(task)))
  }))
  .filter(track => track.modules.length > 0))

function isModuleOpen(moduleId: string) {
  return selectedModule.value?.id === moduleId || extraOpenModules.value.includes(moduleId)
}

function toggleModule(moduleId: string) {
  if (selectedModule.value?.id === moduleId) return
  extraOpenModules.value = extraOpenModules.value.includes(moduleId)
    ? extraOpenModules.value.filter(id => id !== moduleId)
    : [...extraOpenModules.value, moduleId]
}

const isQuizTask = computed(() => selectedTask.value?.evidenceMode === 'quiz')
const quizQuestions = computed(() => selectedTask.value?.quizQuestions ?? [])
const acceptanceCount = computed(() => isQuizTask.value
  ? quizQuestions.value.length
  : (selectedTask.value?.checks.length ?? 0))
const quizAnsweredCount = computed(() => quizQuestions.value.filter(question => Boolean(quizAnswers.value[question.id])).length)
const quizComplete = computed(() => quizQuestions.value.length > 0
  && quizAnsweredCount.value === quizQuestions.value.length)
const quizCorrectCount = computed(() => results.value.filter(result => result.passed).length)
const quizReviewing = computed(() => results.value.length > 0)
const rememberedTasks = computed(() => tasks.value.filter(task => completed.value.has(task.id) && Boolean(task.takeaway)))
const previousTakeaway = computed(() => {
  const index = tasks.value.findIndex(task => task.id === selectedTask.value?.id)
  if (index <= 0) return ''
  const previous = tasks.value[index - 1]
  if (!previous?.takeaway || !completed.value.has(previous.id)) return ''
  return previous.takeaway
})
const canQuizPrevious = computed(() => quizIndex.value > 0 && !quizReviewing.value)
const currentQuestionAnswered = computed(() => {
  const question = quizQuestions.value[quizIndex.value]
  return Boolean(question && quizAnswers.value[question.id])
})
const nextAvailableTask = computed(() => {
  const id = progress.value?.currentTaskId
  if (!id || id === selectedTask.value?.id) return null
  const task = tasks.value.find(item => item.id === id)
  return task && taskUnlocked(task) ? task : null
})
const missedAnswersUnchanged = computed(() => {
  if (!quizReviewing.value || noticeTone.value !== 'error') return false
  return results.value.some(result =>
    !result.passed && quizAnswers.value[result.checkId] === submittedAnswers.value[result.checkId]
  )
})

const canRunChecks = computed(() => {
  if (!selectedTask.value || checking.value || publishing.value || missedAnswersUnchanged.value) return false
  return isQuizTask.value ? quizComplete.value : true
})

const referenceNotes: Record<string, string> = {
  "AI RMF Generative AI Profile": "生成式助手的风险怎么管",
  "Evaluation best practices": "怎样检查它真的会了",
  "HTTP Semantics": "点了生成之后，什么叫做成、什么叫失败",
  "JSON Data Interchange Syntax": "回来的内容要长成说好的样子",
  "Structured Outputs": "事先说好要有的那几样",
  "Context engineering for AI agents": "它该听谁的、篇幅不够先留什么",
  "Building effective agents": "什么时候按事先排好的步骤，什么时候才自己决定",
  "Function calling": "看和改要分成两件事",
  "MCP Tools": "公告是资料，日历才是会改东西的工具",
  "Agent Skills Overview": "反复做的步骤写成短说明",
  "Equipping agents for the real world with Agent Skills": "说明里不要写整本规章",
  "Demystifying evals for AI agents": "用没见过的例子来检查",
  "Retrieval": "只用找得到的资料，找不到就说不知道",
  "OWASP Top 10 for LLM Applications": "公告里的假命令和链接不能当成新命令",
  "MCP Authorization": "登录后那张证明不能转给别的地方",
  "A2A Specification": "交给别的助手，发出去不算做完",
  "OpenTelemetry GenAI semantic conventions": "变慢了要能对上是哪一次"
}

function referencePlain(title: string) {
  return referenceNotes[title] ?? ""
}

function sectionKind(title: string) {
  if (title.includes('别踩')) return 'is-pit'
  if (title.includes('稳妥')) return 'is-practice'
  return 'is-example'
}

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

function learnerVisibleParts(task: { lesson: string; summary: string; objective: string; scenario: string; takeaway?: string; deliverables: string[] }) {
  return [
    task.lesson,
    task.summary,
    task.objective,
    task.scenario,
    task.takeaway ?? '',
    ...task.deliverables,
    ...quizQuestions.value.flatMap(question => [question.prompt, ...question.options.map(option => option.label)]),
    ...results.value.map(result => result.message)
  ]
}

const showBoundaryGloss = computed(() => {
  const task = selectedTask.value
  if (!task || task.lesson.includes('这里说的边界')) return false
  if (task.boundaryGloss) return true
  return learnerVisibleParts(task).some(part => part.includes('边界'))
})

const showModelGloss = computed(() => {
  const task = selectedTask.value
  if (!task || task.lesson.includes('模型就是')) return false
  return learnerVisibleParts(task).some(part => part.includes('模型'))
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

function rememberLessonForReturn() {
  const taskId = selectedTask.value?.id
  if (!taskId) return
  try {
    sessionStorage.setItem(RETURN_TASK_STORAGE_KEY, taskId)
  } catch {
    // Returning still opens the next lesson if the browser blocks storage.
  }
}

function takeReturnTaskId(nextCatalog: LearningCatalog) {
  let stored = ''
  try {
    stored = sessionStorage.getItem(RETURN_TASK_STORAGE_KEY) || ''
  } catch {
    stored = ''
  }
  const tasksInCatalog = nextCatalog.modules.flatMap(module => module.tasks)
  return tasksInCatalog.some(task => task.id === stored) ? stored : ''
}

function firstAvailableTask(nextCatalog: LearningCatalog, nextProgress: LearningProgress) {
  const allTasks = nextCatalog.modules.flatMap(module => module.tasks)
  const done = new Set(nextProgress.completedTaskIds)
  const available = allTasks.filter(task => !done.has(task.id)
    && (!task.prerequisiteTaskIds?.length || task.prerequisiteTaskIds.every(id => done.has(id))))
  const nextIncomplete = available.find(task => !task.elective)?.id ?? available[0]?.id
  if (nextIncomplete) return nextIncomplete
  if (nextProgress.currentTaskId && allTasks.some(task => task.id === nextProgress.currentTaskId)) {
    return nextProgress.currentTaskId
  }
  for (let index = nextProgress.completedTaskIds.length - 1; index >= 0; index -= 1) {
    const taskId = nextProgress.completedTaskIds[index]
    if (allTasks.some(task => task.id === taskId)) return taskId
  }
  return allTasks[0]?.id ?? ''
}

function prefersReducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function scrollElement(node: Element | null, block: ScrollLogicalPosition = 'start') {
  if (!node) return
  node.scrollIntoView({ behavior: prefersReducedMotion() ? 'auto' : 'smooth', block })
}

function scrollToBrief() {
  scrollElement(document.getElementById('lab-brief'))
}

function scrollQuizOutcome(passed: boolean) {
  if (passed) {
    scrollElement(document.querySelector('.tonight-ready, .pass-next, .quiz-actions'), 'center')
    return
  }
  scrollElement(document.querySelector('.quiz-explanation.failed'), 'center')
}

function selectTask(task: LearningTask) {
  if (!taskUnlocked(task) || checking.value || publishing.value) return
  if (task.id !== selectedTaskId.value) {
    try {
      sessionStorage.removeItem(RETURN_TASK_STORAGE_KEY)
    } catch {
      // Choosing another lesson still works if storage is blocked.
    }
  }
  selectedTaskId.value = task.id
  void nextTick(scrollToBrief)
}

function goToNextTask() {
  if (nextAvailableTask.value) selectTask(nextAvailableTask.value)
}

watch(selectedTask, task => {
  if (!task) return
  const restored = drafts.value[task.id]
  const restoredAnswers = quizDrafts.value[task.id]
  if (task.evidenceMode === 'quiz') {
    files.value = {}
    activeFile.value = ''
    const answers = restoredAnswers ? { ...restoredAnswers } : {}
    quizAnswers.value = answers
    quizStarted.value = Object.keys(answers).length > 0
    const unanswered = (task.quizQuestions ?? []).findIndex(question => !answers[question.id])
    quizIndex.value = unanswered < 0 ? 0 : unanswered
  } else {
    files.value = restored ? { ...restored } : Object.fromEntries(task.starterFiles.map(file => [file.path, file.content]))
    activeFile.value = task.starterFiles[0]?.path ?? ''
    quizAnswers.value = {}
    quizIndex.value = 0
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
    selectedTaskId.value = takeReturnTaskId(nextCatalog) || firstAvailableTask(nextCatalog, nextProgress)
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



const quizStarted = ref(false)
const showPassedQuestions = ref(false)
const passedQuestionCount = computed(() => results.value.filter(result => result.passed).length)
watch(results, () => {
  showPassedQuestions.value = false
})
function isQuestionVisible(question: { id: string }, index: number) {
  if (!quizStarted.value && !quizReviewing.value) return false
  if (!quizReviewing.value) return index === quizIndex.value
  if (noticeTone.value !== 'error') return showPassedQuestions.value
  const result = quizResultFor(question.id)
  if (result && !result.passed) return true
  return showPassedQuestions.value
}


function showSaferChoice(questionId: string, optionId: string) {
  const result = quizResultFor(questionId)
  return Boolean(result && !result.passed && result.correctOptionId === optionId)
}

function showPickedChoice(questionId: string, optionId: string) {
  if (quizAnswers.value[questionId] !== optionId) return false
  const result = quizResultFor(questionId)
  return !result?.passed
}

function selectQuizAnswer(questionId: string, optionId: string) {
  quizAnswers.value = { ...quizAnswers.value, [questionId]: optionId }
  saveQuizDraft()
  if (quizReviewing.value || checking.value) return
  const index = quizQuestions.value.findIndex(question => question.id === questionId)
  if (index !== quizIndex.value || index < 0) return
  if (index >= quizQuestions.value.length - 1) {
    void nextTick(() => {
      document.querySelector(".quiz-actions")?.scrollIntoView({ block: "nearest", behavior: scrollBehavior() })
    })
  }
}

function scrollBehavior(): ScrollBehavior {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth"
}


function startQuiz(event?: Event) {
  event?.preventDefault()
  quizStarted.value = true
  const firstUnanswered = quizQuestions.value.findIndex(question => !quizAnswers.value[question.id])
  const index = firstUnanswered >= 0 ? firstUnanswered : 0
  quizIndex.value = index
  void nextTick(() => focusQuizQuestion(index))
}

function focusQuizQuestion(index: number) {
  const next = document.querySelectorAll<HTMLElement>(".quiz-question")[index]
  if (!next) return
  next.scrollIntoView({ block: "nearest", behavior: scrollBehavior() })
  if (!next.hasAttribute("tabindex")) next.setAttribute("tabindex", "-1")
  next.focus({ preventScroll: true })
}

function optionSubmitted(questionId: string, optionId: string) {
  return submittedAnswers.value[questionId] === optionId
}

function quizResultFor(questionId: string) {
  return results.value.find(result => result.checkId === questionId)
}

function changedChoiceLabel(questionId: string) {
  const current = quizAnswers.value[questionId]
  const submitted = submittedAnswers.value[questionId]
  if (!current || !submitted || current === submitted) return ''
  return quizQuestions.value
    .find(question => question.id === questionId)
    ?.options.find(option => option.id === current)?.label ?? ''
}

function goQuiz(offset: number) {
  if (quizReviewing.value || checking.value) return
  const next = quizIndex.value + offset
  if (next < 0 || next >= quizQuestions.value.length) return
  if (offset > 0) {
    const current = quizQuestions.value[quizIndex.value]
    if (!current || !quizAnswers.value[current.id]) return
  }
  quizIndex.value = next
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
    if (task.evidenceMode === 'quiz') {
      const correct = response.results.filter(result => result.passed).length
      const total = response.results.length
      const need = Math.ceil(total * (task.passingScore ?? 80) / 100)
      notice.value = response.passed
        ? t('learningQuizPassed', { correct, total })
        : t('learningQuizFailed', { correct, need })
    } else {
      notice.value = response.passed
        ? t('learningCheckPassed', { score: response.score })
        : t('learningCheckFailed', { score: response.score })
    }
    noticeTone.value = response.passed ? 'success' : 'error'
    if (task.evidenceMode === 'quiz') {
      await nextTick()
      scrollQuizOutcome(response.passed)
    }
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
      rememberLessonForReturn()
      emit('use-agent', response.agent.id, task.tonightPrompt)
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
        <h3 v-if="selectedModule" class="course-kicker">{{ selectedModule.title }}</h3>
        <h2>{{ selectedTask?.title || t('learningTitle') }}</h2>
        <p class="hero-copy">{{ t('learningHeroCopy') }}</p>
        <p class="hero-meta">
          <template v-if="selectedTask && completed.has(selectedTask.id)">
            {{ t('learningLessonPassedNext') }}
          </template>
          <template v-else-if="selectedTask">
            {{ t('learningThisLesson', { minutes: selectedTask.estimatedMinutes }) }}
          </template>
          <template v-else>
            {{ t('learningLabs', { count: coreTasks.length || 17 }) }}
            <template v-if="coreComplete"> · {{ t('learningElectiveLabs', { count: electiveTasks.length || 4 }) }}</template>
            · {{ t('learningDuration') }}
          </template>
        </p>
      </div>
      <div v-if="progressPercent" class="hero-progress-card">
        <p v-if="!coreComplete" class="hero-meta">{{ t('learningCoreOnly') }}</p>
        <p v-if="coreComplete" class="hero-meta">{{ t('learningCoreComplete') }}</p>
      </div>
    </header>

    <p v-if="loading" class="status">{{ t('learningLoading') }}</p>
    <div v-if="error" class="status error" role="alert">
      <span>{{ error }}</span>
      <button v-if="errorAction === 'settings'" type="button" @click="emit('open-settings')">
        {{ t('learningOpenSettings') }}
      </button>
    </div>
    <p v-if="notice && !(isQuizTask && results.length)" class="status" :class="noticeTone">{{ notice }}</p>

    <div v-if="catalog" class="learning-grid">
      <aside class="course-rail">
        <details v-if="rememberedTasks.length" class="remembered">
          <summary>{{ t('learningRemembered', { count: rememberedTasks.length }) }}</summary>
          <ol>
            <li v-for="task in rememberedTasks" :key="task.id">{{ task.takeaway }}</li>
          </ol>
        </details>
        <div v-for="track in visibleTracks" :key="track.id" class="course-track" :class="track.id">
          <div class="course-rail-head"><strong>{{ track.title }}</strong></div>
          <div v-for="(module, moduleIndex) in track.modules" :key="`${track.id}-${module.id}`" class="module-block" :class="{ open: isModuleOpen(module.id) }">
            <button
              class="module-heading"
              type="button"
              :aria-expanded="isModuleOpen(module.id)"
              @click="toggleModule(module.id)"
            >
              <span class="module-index">{{ moduleIndex + 1 }}</span>
              <div>
                <strong>{{ module.title }}</strong>
              </div>
            </button>
            <button
              v-for="(task, taskIndex) in module.tasks"
              v-show="isModuleOpen(module.id)"
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
                <span v-if="selectedTask?.id === task.id && !completed.has(task.id)" class="task-badges">
                  <small class="doing-now">{{ t('learningInProgress') }}</small>
                </span>
                <small v-if="!taskUnlocked(task)" class="lock-reason">{{ t('learningLocked') }}</small>
              </span>
            </button>
          </div>
        </div>
      </aside>

      <main v-if="selectedTask" class="task-workspace">
        <header v-if="!isQuizTask" class="lab-context-bar">
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
          <p v-if="previousTakeaway" class="previous-takeaway">
            <strong>{{ t('learningPreviousTakeaway') }}</strong>
            <span>{{ previousTakeaway }}</span>
          </p>
          <div v-if="!isQuizTask" class="section-heading">
            <p class="lesson-title">{{ selectedTask.title }}</p>
            <span v-if="score" class="score-chip">{{ t('learningBestScore', { score }) }}</span>
          </div>
          <p v-if="!isQuizTask" class="summary">{{ selectedTask.summary }}</p>
          <p v-if="showBoundaryGloss" class="boundary-gloss">{{ t('learningBoundaryGloss') }}</p>
          <p v-if="showModelGloss" class="model-gloss">{{ t('learningModelGloss') }}</p>
          <p v-if="selectedTask.takeaway" class="takeaway">
            <strong>{{ t('learningTakeaway') }}</strong>
            <span>{{ selectedTask.takeaway }}</span>
          </p>
          <div class="scenario"><strong>{{ t('learningScenario') }}</strong><div class="scenario-copy"><p v-for="(sentence, index) in scenarioSentences" :key="index">{{ sentence }}</p></div></div>
          <div class="lesson-block">
            <p class="eyebrow">{{ t('learningLesson') }}</p>
            <div class="lesson-copy">
              <section v-for="(section, index) in lessonSections" :key="`${section.title}-${index}`" class="lesson-section" :class="sectionKind(section.title)">
                <strong v-if="section.title">{{ section.title }}</strong>
                <p v-if="section.body">{{ section.body }}</p>
              </section>
            </div>
          </div>
          <div class="task-facts">
            <div v-if="!isQuizTask"><small>{{ t('learningTime') }}</small><strong>{{ selectedTask.estimatedMinutes }} {{ t('learningMinuteUnit') }}</strong></div>
            <div v-if="!isQuizTask"><small>{{ t('learningChecks') }}</small><strong>{{ acceptanceCount }}</strong></div>
            <div v-if="isQuizTask"><small>{{ t('learningPassLine') }}</small><strong>{{ t('learningPassScore', { score: selectedTask.passingScore ?? 80 }) }}</strong></div>
          </div>
          <div v-if="!isQuizTask" class="objective"><strong>{{ t('learningObjective') }}</strong><span>{{ selectedTask.objective }}</span></div>
          <div v-if="!isQuizTask" class="skill-block">
            <p class="eyebrow">{{ t('learningSkills') }}</p>
            <div class="skill-list"><span v-for="skill in selectedTask.skills" :key="skill">{{ skill }}</span></div>
          </div>
        </section>

        <section id="lab-quiz" class="workbench-grid" :class="{ 'quiz-layout': isQuizTask }">
          <div class="panel file-panel">
            <div class="section-heading">
              <div>
                <h3>{{ isQuizTask ? t('learningWorkspaceQuiz') : t('learningWorkspace') }}</h3>
              </div>
              <UiButton v-if="!isQuizTask" variant="primary" size="sm" :disabled="!canRunChecks" @click="checkTask">
                {{ checking ? t('learningChecking') : t('learningRunChecks') }}
              </UiButton>
            </div>
            <template v-if="isQuizTask">
              <p v-if="!completed.has(selectedTask.id)" class="quiz-ask">{{ t('learningQuizAsk') }}</p>
              <p v-if="showBoundaryGloss" class="boundary-gloss">{{ t('learningBoundaryGloss') }}</p>
              <p v-if="showModelGloss" class="model-gloss">{{ t('learningModelGloss') }}</p>
              <p v-if="selectedTask.takeaway" class="quiz-remember">
                <strong>{{ t('learningTakeaway') }}</strong>
                <span>{{ selectedTask.takeaway }}</span>
              </p>
              <ol v-if="!quizStarted && !completed.has(selectedTask.id)" class="quiz-steps">
                <li>{{ t('learningHowRead') }}</li>
                <li>{{ t('learningHowAnswer') }}</li>
                <li>{{ t('learningHowSubmit') }}</li>
              </ol>
              <UiButton
                v-if="!quizStarted && !completed.has(selectedTask.id)"
                class="start-quiz-action"
                variant="accent"
                size="lg"
                @click="startQuiz()"
              >
                {{ t('learningStartQuizAfter') }}
              </UiButton>
              <div v-if="quizStarted && !quizReviewing" class="quiz-nav">
                <UiButton variant="secondary" size="sm" :disabled="!canQuizPrevious || checking" @click="goQuiz(-1)">
                  {{ t('learningQuizPrevious') }}
                </UiButton>
                <span>{{ t('learningQuizAt', { current: quizIndex + 1, total: quizQuestions.length }) }}</span>
              </div>
              <div class="quiz-list">
                <article
                  v-for="(question, index) in quizQuestions"
                  v-show="isQuestionVisible(question, index)"
                  :key="question.id"
                  class="quiz-question"
                >
                  <p class="quiz-prompt"><span class="quiz-index">{{ index + 1 }}</span>{{ question.prompt }}</p>
                  <div class="quiz-options">
                    <label
                      v-for="option in question.options"
                      :key="option.id"
                      :class="['quiz-option', {
                        selected: quizAnswers[question.id] === option.id,
                        passed: optionSubmitted(question.id, option.id) && quizResultFor(question.id)?.passed,
                        failed: optionSubmitted(question.id, option.id) && quizResultFor(question.id) && !quizResultFor(question.id)?.passed,
                        correct: Boolean(quizResultFor(question.id)?.correctOptionId) && option.id === quizResultFor(question.id)?.correctOptionId
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
                      <em
                        v-if="showPickedChoice(question.id, option.id)"
                        class="picked-mark"
                      >{{ t('learningPicked') }}</em>
                      <em
                        v-if="showSaferChoice(question.id, option.id)"
                        class="safer-mark"
                      >{{ t('learningSaferChoice') }}</em>
                    </label>
                  </div>
                  <p
                    v-if="quizResultFor(question.id)"
                    class="quiz-explanation"
                    :class="quizResultFor(question.id)?.passed ? 'passed' : 'failed'"
                    role="status"
                  >
                    <strong>{{ changedChoiceLabel(question.id) ? t('learningAnswerPending') : (quizResultFor(question.id)?.passed ? t('learningQuizRight') : t('learningQuizWrong')) }}</strong>
                    <span v-if="changedChoiceLabel(question.id)">{{ t('learningAnswerChanged', { choice: changedChoiceLabel(question.id) }) }}</span>
                    <span v-else-if="quizResultFor(question.id) && !quizResultFor(question.id)?.passed" class="quiz-choice">{{ quizResultFor(question.id)?.evidence }}</span>
                    <span>{{ quizResultFor(question.id)?.message }}</span>
                  </p>
                </article>
              </div>
              <button
                v-if="quizReviewing && noticeTone === 'error' && passedQuestionCount"
                class="show-passed"
                type="button"
                @click="showPassedQuestions = !showPassedQuestions"
              >
                {{ showPassedQuestions ? t('learningHidePassed') : t('learningShowPassed', { count: passedQuestionCount }) }}
              </button>
              <button
                v-if="quizReviewing && noticeTone !== 'error'"
                class="show-passed"
                type="button"
                @click="showPassedQuestions = !showPassedQuestions"
              >
                {{ showPassedQuestions ? t('learningHidePassedReview') : t('learningShowPassedReview') }}
              </button>
              <p v-if="completed.has(selectedTask.id) && selectedTask.tonightPrompt" class="tonight-ready">
                <strong>{{ t('learningTonightUse') }}</strong>
                {{ selectedTask.tonightPrompt }}
              </p>
              <div v-if="quizStarted || quizReviewing || completed.has(selectedTask.id)" class="quiz-actions" :class="{ ready: quizComplete && !results.length, docked: quizStarted && !quizReviewing && !completed.has(selectedTask.id) }">
                <p v-if="quizStarted && !quizReviewing && !completed.has(selectedTask.id) && selectedTask.takeaway" class="dock-remember">
                  <strong>{{ t('learningTakeaway') }}</strong>
                  <span>{{ selectedTask.takeaway }}</span>
                </p>
                <p class="quiz-progress" :class="{ failed: results.length && noticeTone === 'error', passed: results.length && noticeTone === 'success' }">
                  <template v-if="results.length">
                    {{ notice }}
                    <span v-if="noticeTone === 'error'">{{ t('learningAttemptSummary', { passed: quizCorrectCount, total: results.length }) }}</span>
                  </template>
                  <template v-else-if="quizComplete">
                    {{ t('learningQuizReady') }}
                  </template>
                  <template v-else>
                    {{ t('learningQuizProgress', { answered: quizAnsweredCount, total: quizQuestions.length }) }}
                    <span>{{ t('learningQuizRemain', { remain: quizQuestions.length - quizAnsweredCount }) }}</span>
                  </template>
                </p>
                <UiButton
                  v-if="quizStarted && !quizReviewing && !completed.has(selectedTask.id) && quizIndex < quizQuestions.length - 1"
                  variant="primary"
                  size="lg"
                  :disabled="!currentQuestionAnswered || checking"
                  @click="goQuiz(1)"
                >
                  {{ t('learningQuizNext') }}
                </UiButton>
                <UiButton
                  v-if="!completed.has(selectedTask.id) && (quizComplete || quizReviewing)"
                  :variant="results.length && noticeTone === 'error' ? 'accent' : 'accent'"
                  size="lg"
                  :disabled="!canRunChecks"
                  @click="checkTask"
                >
                  {{ checking ? t('learningChecking') : (results.length && noticeTone === 'error' ? t('learningResubmit') : t('learningRunChecks')) }}
                </UiButton>
                <div v-if="completed.has(selectedTask.id)" class="pass-next">
                  <UiButton
                    variant="accent"
                    size="lg"
                    :disabled="checking || publishing"
                    @click="rememberLessonForReturn(); emit('try-evening-plan', selectedTask.tonightPrompt, selectedTask.takeaway)"
                  >
                    {{ t('learningTryTonight') }}
                  </UiButton>
                  <UiButton
                    v-if="nextAvailableTask"
                    variant="secondary"
                    size="lg"
                    :disabled="checking || publishing"
                    :title="nextAvailableTask.title"
                    @click="goToNextTask"
                  >
                    {{ t('learningNextLesson') }}
                    <span class="next-lesson-title">{{ nextAvailableTask.title }}</span>
                  </UiButton>
                </div>
                <UiButton
                  v-if="selectedTask.kind === 'agent-project' && attemptId && completed.has(selectedTask.id)"
                  variant="accent"
                  size="sm"
                  :disabled="publishing"
                  @click="publishAgent"
                >
                  {{ publishing ? t('learningPublishing') : t('learningPublishUse') }}
                </UiButton>
                <small v-if="publishedAgentName" class="publish-note">{{ publishedAgentName }}</small>
              </div>
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

          <div v-if="!isQuizTask" class="panel evidence-panel">
            <div class="section-heading"><h3>{{ t('learningChecklist') }}</h3><span>{{ results.length }}/{{ acceptanceCount }}</span></div>
            <div v-if="results.length" class="check-list">
              <article v-for="result in results" :key="result.checkId" :class="['check-row', result.passed ? 'passed' : 'failed']">
                <strong>{{ result.passed ? t('learningPassed') : t('learningNeedsFix') }}</strong>
                <span>{{ result.message }}</span>
                <small>{{ result.evidence }}</small>
              </article>
            </div>
            <div v-else><UiEmptyState :title="t('learningNoEvidence')" :copy="t('learningNoEvidenceCopy')" /></div>
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

        <section class="task-brief panel task-appendix">
          <details>
            <summary>{{ t('learningReferences') }}</summary>
            <p class="reference-note">{{ t('learningReferencesNote') }}</p>
            <div class="reference-list">
              <a v-for="reference in selectedTask.references" :key="reference.url" :href="reference.url" target="_blank" rel="noopener noreferrer">
                {{ referencePlain(reference.title) || reference.title }}
              </a>
            </div>
            <div v-if="!isQuizTask" class="task-meta-grid">
              <div><strong>{{ t('learningDeliverables') }}</strong><ul><li v-for="item in selectedTask.deliverables" :key="item">{{ item }}</li></ul></div>
              <div><strong>{{ t('learningPrerequisites') }}</strong><span v-if="!selectedTask.prerequisiteTaskIds?.length">{{ t('learningNoPrerequisites') }}</span><ul v-else><li v-for="item in prerequisiteTitles(selectedTask)" :key="item">{{ item }}</li></ul></div>
            </div>
          </details>
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
  font-size: 12px;
  font-weight: 900;
  letter-spacing: .04em;
}
.course-kicker {
  margin: 0;
  color: var(--color-primary-strong);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
}
.learning-hero h2 {
  margin: 0;
  color: var(--color-heading);
  font-size: var(--text-xl);
  font-weight: var(--font-bold);
  line-height: 1.2;
}
.lesson-title {
  margin: 3px 0 0;
  color: var(--color-heading);
  font-size: 20px;
  font-weight: var(--font-bold);
  line-height: 1.3;
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
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.module-heading div {
  display: grid;
  gap: 3px;
}
.module-heading small {
  color: var(--color-faint);
  font-size: 12px;
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
  font-size: 12px;
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
  font-size: 12px;
}
.task-button .doing-now {
  color: var(--color-primary-strong);
  font-weight: 700;
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
  grid-template-columns: repeat(2, minmax(0, 1fr));
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
  font-size: 12px;
  font-weight: 700;
}
.task-facts strong {
  color: var(--color-heading);
  font-size: var(--text-xs);
  line-height: 1.4;
  white-space: normal;
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
.scenario-copy {
  display: grid;
  gap: 6px;
}
.scenario-copy p {
  margin: 0;
}
.previous-takeaway {
  display: grid;
  gap: 2px;
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.5;
}
.previous-takeaway strong {
  color: var(--color-primary-strong);
  font-size: 12px;
}
.previous-takeaway span {
  color: var(--color-heading);
}
.takeaway {
  display: grid;
  gap: 4px;
  margin: 12px 0 0;
  padding: 11px 13px;
  border-left: 4px solid var(--color-heading);
  background: var(--color-surface-muted);
}
.takeaway strong {
  color: var(--color-primary-strong);
  font-size: 12px;
}
.takeaway span {
  color: var(--color-heading);
  font-size: var(--text-sm);
  line-height: 1.5;
}
.quiz-nav {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 0 0 10px;
}
.quiz-nav span {
  color: var(--color-muted);
  font-size: var(--text-xs);
  font-weight: 700;
}
.remembered {
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-panel);
}
.remembered summary {
  cursor: pointer;
  color: var(--color-heading);
  font-size: var(--text-xs);
  font-weight: 700;
}
.remembered ol {
  margin: 8px 0 0;
  padding-left: 1.2em;
  color: var(--color-muted);
  font-size: var(--text-xs);
  line-height: 1.55;
}
.objective strong,
.scenario strong {
  font-size: 12px;
  color: var(--color-primary-strong);
}
.skill-block {
  display: grid;
  gap: 6px;
  margin-top: var(--space-3);
}
.skill-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
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
.lesson-section.is-example,
.lesson-section.is-pit {
  padding-left: 12px;
  border-left: 2px solid var(--color-primary);
}
.lesson-section.is-pit {
  border-left-color: var(--color-danger);
}
.lesson-section strong {
  color: var(--color-heading);
  font-size: var(--text-sm);
}
.lesson-section p {
  margin: 0;
  white-space: pre-line;
}
.boundary-gloss,
.model-gloss {
  margin: 0;
  color: var(--color-heading);
  font-size: var(--text-sm);
  line-height: 1.6;
}
.task-brief > .boundary-gloss,
.task-brief > .model-gloss {
  margin-bottom: 8px;
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
  font-size: 12px;
}
.task-meta-grid ul {
  margin: 6px 0 0;
  padding-left: 18px;
}
.reference-note {
  margin: 8px 0 0;
  color: var(--color-muted);
  font-size: var(--text-sm);
  line-height: 1.5;
}
.reference-list a {
  display: grid;
  gap: 2px;
}
.reference-list strong {
  color: var(--color-heading);
  font-size: var(--text-sm);
}
.reference-list {
  display: grid;
  gap: 5px;
  margin-top: 14px;
  padding-top: var(--space-3);
  border-top: 1px solid var(--color-border-soft);
  font-size: 12px;
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
.workbench-grid.quiz-layout {
  grid-template-columns: minmax(0, 1fr);
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
  font-size: 12px;
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
  padding-bottom: 128px;
}
.quiz-question:focus {
  outline: none;
}
.quiz-question:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 4px;
}
.quiz-question {
  display: grid;
  gap: 8px;
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--color-border-soft);
  scroll-margin-bottom: 128px;
}
.quiz-question:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}
.quiz-prompt {
  margin: 0;
  color: var(--color-heading);
  font-size: var(--text-md);
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
  min-height: 44px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-input);
  color: var(--color-text);
  font-size: var(--text-base);
  line-height: 1.55;
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
.picked-mark {
  margin-left: auto;
  color: var(--color-primary-strong);
  font-style: normal;
  font-weight: 700;
  font-size: 12px;
  white-space: nowrap;
}
.safer-mark {
  margin-left: 8px;
  color: var(--color-success, #1f7a4d);
  font-style: normal;
  font-weight: 700;
  font-size: 12px;
}
.quiz-option.correct {
  border-color: var(--color-success);
}
.quiz-explanation {
  display: grid;
  gap: 4px;
  margin: 8px 0 0;
  padding: 10px 12px;
  scroll-margin-bottom: 128px;
  border-left: 3px solid var(--color-border);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  background: var(--color-row);
  color: var(--color-text);
  font-size: var(--text-base);
  line-height: 1.6;
}
.quiz-choice {
  font-weight: 700;
}
.quiz-explanation strong {
  font-size: var(--text-md);
}
.quiz-explanation.passed {
  border-left-color: var(--color-success);
}
.quiz-explanation.failed {
  border-left-color: var(--color-danger);
}
.quiz-explanation small {
  color: var(--color-text);
  font-size: var(--text-sm);
  line-height: 1.55;
}
.course-track {
  display: grid;
  gap: var(--space-3);
}
.course-track.elective {
  padding-top: var(--space-4);
  border-top: 1px dashed var(--color-border);
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
.hero-meta {
  margin: 6px 0 0;
  color: var(--color-muted);
  font-size: 12px;
}
.start-quiz,
.start-quiz-action {
  margin: 10px 0 0;
  justify-self: start;
}
.start-quiz a {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 0 16px;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: #fff;
  font-size: var(--text-base);
  font-weight: 700;
  text-decoration: none;
}
.start-quiz a:hover {
  text-decoration: none;
  filter: brightness(1.06);
}
.start-quiz a:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 3px;
}
#lab-brief,
#lab-quiz {
  scroll-margin-top: 16px;
}
.quiz-ask {
  margin: 0;
  color: var(--color-heading);
  font-size: var(--text-sm);
  font-weight: 700;
  line-height: 1.5;
}
.quiz-remember {
  display: grid;
  gap: 4px;
  margin: 0;
  padding: 10px 12px;
  border-left: 4px solid var(--color-heading);
  background: var(--color-surface-muted);
}
.quiz-remember strong {
  color: var(--color-primary-strong);
  font-size: 12px;
}
.quiz-remember span {
  color: var(--color-heading);
  font-size: var(--text-sm);
  line-height: 1.5;
}
.quiz-steps {
  display: grid;
  gap: 4px;
  margin: 0 0 8px;
  padding-left: 1.2em;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.5;
}
.show-passed {
  justify-self: start;
  margin: 8px 0 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-muted);
  font: inherit;
  font-size: 12px;
  line-height: 1.4;
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}
.show-passed:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 3px;
}
.pass-next {
  display: grid;
  gap: 10px;
  margin-top: 8px;
}
.quiz-actions.ready {
  margin-top: 8px;
  padding: 12px;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
}
.quiz-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface-panel);
}
.quiz-actions.docked {
  position: sticky;
  bottom: calc(8px + env(safe-area-inset-bottom, 0px));
  z-index: 3;
}
.dock-remember {
  flex: 1 0 100%;
  margin: 0;
  color: var(--color-heading);
  font-size: 13px;
  line-height: 1.45;
}
.dock-remember strong {
  margin-right: 6px;
  color: var(--color-primary-strong);
  font-size: 12px;
}
.quiz-actions .quiz-progress {
  margin: 0 auto 0 0;
}
.tonight-ready {
  margin: 12px 0 0;
  color: var(--color-muted);
  font-size: var(--text-sm);
  line-height: 1.5;
}
.tonight-ready strong {
  display: block;
  color: var(--color-heading);
  font-size: 12px;
}
.quiz-actions .quiz-progress.failed {
  color: var(--color-danger);
}
.quiz-actions .quiz-progress.passed {
  color: var(--color-success);
}
.quiz-actions .quiz-progress span {
  display: block;
  font-weight: 600;
}
.quiz-actions :deep(.ui-button--accent) {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  height: auto;
  white-space: normal;
}
.quiz-actions :deep(.ui-button--accent .ui-button__content) {
  display: grid;
  justify-items: start;
  white-space: normal;
}
.next-lesson-title {
  display: block;
  max-width: min(18rem, 70vw);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
  white-space: normal;
  opacity: .92;
}
.attempt-summary {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
}
.task-appendix {
  opacity: .92;
}
.task-appendix details {
  display: grid;
  gap: var(--space-3);
}
.task-appendix summary {
  cursor: pointer;
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 700;
}
.start-quiz.after-lesson {
  margin-top: var(--space-4);
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
.mentor-box summary {
  cursor: pointer;
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 700;
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
  .task-workspace {
    order: 1;
  }
  .evidence-panel,
  .course-rail {
    position: static;
    max-height: none;
  }
  .course-rail {
    order: 2;
    max-height: 280px;
  }
}
@media (max-width: 760px) {
  .learning-hero {
    grid-template-columns: 1fr;
    gap: 10px;
    padding: 12px 14px;
  }
  .hero-progress-card {
    display: none;
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
  .panel {
    padding: 15px;
  }
  .file-panel textarea {
    min-height: 330px;
  }
  .quiz-actions :deep(button) {
    min-height: 44px;
  }
  .quiz-prompt {
    font-size: 15px;
  }
  .quiz-option {
    min-height: 48px;
    padding: 12px;
    font-size: 14px;
  }
  .editor-hint,
  .skill-block {
    display: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .progress-track span,
  .file-import-label {
    transition: none;
  }
}
</style>

