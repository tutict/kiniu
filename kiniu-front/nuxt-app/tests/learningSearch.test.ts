import assert from 'node:assert/strict'
import test from 'node:test'
import {
  countLearningMatches,
  filterLearningModules,
  learningTaskMatches
} from '../utils/learningSearch.ts'
import type { LearningModule, LearningTask } from '../types/learning.ts'

function task(overrides: Partial<LearningTask>): LearningTask {
  return {
    id: '',
    title: '',
    summary: '',
    level: '',
    kind: '',
    estimatedMinutes: 0,
    skills: [],
    objective: '',
    scenario: '',
    mentorAgentId: '',
    starterFiles: [],
    checks: [],
    lesson: '',
    deliverables: [],
    prerequisiteTaskIds: [],
    evidenceMode: 'quiz',
    references: [],
    ...overrides
  } as LearningTask
}

const boundary = task({
  id: 'boundary',
  title: '先写清边界',
  summary: '学会说清帮谁、不做什么',
  objective: '能写出边界',
  takeaway: '先写清帮谁',
  skills: ['边界判断', '需求澄清']
})
const evaluation = task({
  id: 'evaluation',
  title: '怎样算合格',
  summary: '用没见过的例子检查',
  objective: '会设计验收',
  takeaway: '先定验收',
  skills: ['评价']
})

test('empty query matches every task', () => {
  assert.equal(learningTaskMatches(boundary, ''), true)
  assert.equal(learningTaskMatches(boundary, '   '), true)
})

test('matches title, summary, objective, takeaway and skills case-insensitively', () => {
  assert.equal(learningTaskMatches(boundary, '边界'), true)
  assert.equal(learningTaskMatches(boundary, '帮谁、不做什么'), true)
  assert.equal(learningTaskMatches(boundary, '需求澄清'), true)
  assert.equal(learningTaskMatches(boundary, '先定验收'), false)
  assert.equal(learningTaskMatches(evaluation, 'EVA'), false)
})

test('does not match lesson body text', () => {
  const withLesson = task({ id: 'x', title: '标题', lesson: '正文里的机密词' })
  assert.equal(learningTaskMatches(withLesson, '机密词'), false)
})

test('filtering keeps matching tasks grouped by module and drops empty modules', () => {
  const modules: LearningModule[] = [
    { id: 'm1', title: '模块一', summary: '', level: '', tasks: [boundary, evaluation] },
    { id: 'm2', title: '模块二', summary: '', level: '', tasks: [task({ id: 'other', title: '无关' })] }
  ]
  const filtered = filterLearningModules(modules, '边界')
  assert.equal(filtered.length, 1)
  assert.equal(filtered[0].id, 'm1')
  assert.deepEqual(filtered[0].tasks.map(item => item.id), ['boundary'])
})

test('an empty query leaves the catalog untouched', () => {
  const modules: LearningModule[] = [
    { id: 'm1', title: '模块一', summary: '', level: '', tasks: [boundary, evaluation] }
  ]
  assert.equal(filterLearningModules(modules, '  '), modules)
})

test('counts matches and reports zero for an empty query', () => {
  assert.equal(countLearningMatches([boundary, evaluation], '边界'), 1)
  assert.equal(countLearningMatches([boundary, evaluation], ''), 0)
})
