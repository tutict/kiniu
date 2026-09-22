export type LearningFile = { path: string; content: string }
export type TaskCheckDefinition = { id: string; type: string; path: string; rule: string; required: boolean; points: number; message: string }
export type LearningReference = { title: string; url: string; publisher: string; version: string; accessedAt: string }
export type LearningQuizOption = { id: string; label: string }
export type LearningQuizQuestion = { id: string; prompt: string; options: LearningQuizOption[]; points: number }
export type LearningTask = {
  id: string
  title: string
  summary: string
  level: string
  kind: string
  estimatedMinutes: number
  skills: string[]
  objective: string
  scenario: string
  mentorAgentId: string
  starterFiles: LearningFile[]
  checks: TaskCheckDefinition[]
  lesson: string
  deliverables: string[]
  prerequisiteTaskIds: string[]
  evidenceMode: 'document' | 'import' | 'quiz'
  references: LearningReference[]
  quizQuestions?: LearningQuizQuestion[]
  passingScore?: number
  elective?: boolean
  tonightPrompt?: string
  takeaway?: string
}
export type LearningModule = { id: string; title: string; summary: string; level: string; tasks: LearningTask[] }
export type LearningCatalog = { version: number; modules: LearningModule[] }
export type LearningProgress = { schemaVersion: number; currentTaskId: string; completedTaskIds: string[]; bestScores: Record<string, number>; weakSkills: string[] }
export type TaskCheckResult = { checkId: string; passed: boolean; required: boolean; points: number; evidence: string; message: string; correctOptionId?: string }
export type LearningCheckResponse = { attemptId: string; taskId: string; passed: boolean; score: number; results: TaskCheckResult[]; progress: LearningProgress }
export type LearningPublishedAgent = {
  id: string
  name: string
  role: string
  summary: string
  personality: string
  systemPrompt: string
  activeScenes: string[]
  personalityParameters: Record<string, string>
  coreGoals: string[]
  hiddenMotives: string[]
  initiative: number
  memoryStyle: string
}
export type LearningPublishResponse = {
  taskId: string
  attemptId: string
  agent: LearningPublishedAgent
}
