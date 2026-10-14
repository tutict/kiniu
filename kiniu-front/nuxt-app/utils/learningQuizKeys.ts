export type QuizKeyAction =
  | { type: 'select'; optionIndex: number }
  | { type: 'next' }
  | { type: 'previous' }

export type QuizKeyContext = {
  key: string
  /** Whether focus sits inside the quiz answer area. */
  insideQuiz: boolean
  /** Whether the focused element is a text entry, so we never hijack typing. */
  isTextEntry: boolean
  /** Whether the current question already has an answer. */
  answered: boolean
  /** Number of options on the current question. */
  optionCount: number
}

/**
 * Maps a keydown on the quiz to a single action, or null when the key should pass through. Kept pure
 * so the mapping can be unit tested without a DOM.
 */
export function resolveQuizKeyAction(context: QuizKeyContext): QuizKeyAction | null {
  if (!context.insideQuiz || context.isTextEntry) return null
  if (/^[1-9]$/.test(context.key)) {
    const optionIndex = Number(context.key) - 1
    return optionIndex < context.optionCount ? { type: 'select', optionIndex } : null
  }
  if (context.key === 'ArrowRight' || context.key === 'Enter') {
    return context.answered ? { type: 'next' } : null
  }
  if (context.key === 'ArrowLeft') return { type: 'previous' }
  return null
}
