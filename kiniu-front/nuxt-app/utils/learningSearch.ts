import type { LearningModule, LearningTask } from '../types/learning'

/**
 * Case-insensitive substring match over the fields a learner would search by. Lesson bodies and
 * quiz prompts are intentionally excluded so results stay about the lessons themselves.
 */
export function learningTaskMatches(task: LearningTask, query: string): boolean {
  const needle = query.trim().toLowerCase()
  if (!needle) return true
  const haystack = [
    task.title,
    task.summary,
    task.objective,
    task.takeaway ?? '',
    ...(task.skills ?? [])
  ]
    .join('\n')
    .toLowerCase()
  return haystack.includes(needle)
}

/** Narrow each module to its matching tasks and drop modules that no longer match. */
export function filterLearningModules(modules: LearningModule[], query: string): LearningModule[] {
  if (!query.trim()) return modules
  return modules
    .map(module => ({
      ...module,
      tasks: module.tasks.filter(task => learningTaskMatches(task, query))
    }))
    .filter(module => module.tasks.length > 0)
}

/** How many tasks in the catalog match the query. Zero for an empty query. */
export function countLearningMatches(tasks: LearningTask[], query: string): number {
  if (!query.trim()) return 0
  return tasks.filter(task => learningTaskMatches(task, query)).length
}
