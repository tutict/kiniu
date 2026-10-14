import assert from 'node:assert/strict'
import test from 'node:test'
import { resolveQuizKeyAction, type QuizKeyContext } from '../utils/learningQuizKeys.ts'

function context(overrides: Partial<QuizKeyContext>): QuizKeyContext {
  return {
    key: '',
    insideQuiz: true,
    isTextEntry: false,
    answered: false,
    optionCount: 4,
    ...overrides
  }
}

test('number keys pick the matching option', () => {
  assert.deepEqual(resolveQuizKeyAction(context({ key: '1' })), { type: 'select', optionIndex: 0 })
  assert.deepEqual(resolveQuizKeyAction(context({ key: '4' })), { type: 'select', optionIndex: 3 })
})

test('number keys past the last option do nothing', () => {
  assert.equal(resolveQuizKeyAction(context({ key: '5' })), null)
  assert.equal(resolveQuizKeyAction(context({ key: '0' })), null)
})

test('right arrow and enter advance only once answered', () => {
  assert.deepEqual(resolveQuizKeyAction(context({ key: 'ArrowRight', answered: true })), { type: 'next' })
  assert.deepEqual(resolveQuizKeyAction(context({ key: 'Enter', answered: true })), { type: 'next' })
  assert.equal(resolveQuizKeyAction(context({ key: 'ArrowRight', answered: false })), null)
})

test('left arrow steps back', () => {
  assert.deepEqual(resolveQuizKeyAction(context({ key: 'ArrowLeft' })), { type: 'previous' })
})

test('keys are ignored outside the quiz area or while typing', () => {
  assert.equal(resolveQuizKeyAction(context({ key: '1', insideQuiz: false })), null)
  assert.equal(resolveQuizKeyAction(context({ key: '1', isTextEntry: true })), null)
  assert.equal(resolveQuizKeyAction(context({ key: 'Enter', answered: true, isTextEntry: true })), null)
})

test('unrelated keys pass through', () => {
  assert.equal(resolveQuizKeyAction(context({ key: 'a' })), null)
  assert.equal(resolveQuizKeyAction(context({ key: 'Tab' })), null)
})
