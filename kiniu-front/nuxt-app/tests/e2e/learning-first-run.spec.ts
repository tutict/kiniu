import { expect, test, type Page } from '@playwright/test'
import { readFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'

type CatalogOption = { id: string; label: string }
type CatalogQuestion = { id: string; options: CatalogOption[]; correctOptionId: string }
type CatalogTask = {
  id: string
  title: string
  elective?: boolean
  tonightPrompt?: string
  takeaway?: string
  quizQuestions: CatalogQuestion[]
}

function localToken(): string {
  const candidates = [
    resolve('../../.run/e2e/local-token'),
    resolve('../../.run/learnux/local-token'),
    resolve('../../.run/local-token'),
  ]
  for (const path of candidates) {
    if (existsSync(path)) return readFileSync(path, 'utf8').trim()
  }
  return ''
}

function catalogTasks(): CatalogTask[] {
  const catalog = JSON.parse(
    readFileSync(resolve('../../kiniu-back/data/learning-catalog.json'), 'utf8'),
  )
  return catalog.modules.flatMap((module: { tasks: CatalogTask[] }) => module.tasks)
}

function correctLabels(task: CatalogTask): string[] {
  return task.quizQuestions.map((question) => {
    const option = question.options.find((item) => item.id === question.correctOptionId)
    if (!option) throw new Error(`Missing correct option for ${task.id}/${question.id}`)
    return option.label
  })
}

test.beforeEach(async ({ request }) => {
  const port = process.env.KINIU_E2E_BACKEND_PORT || '18080'
  const token = localToken()
  const response = await request.post(`http://127.0.0.1:${port}/learn/progress/reset`, {
    headers: token ? { 'X-Local-Token': token } : {},
  })
  expect(response.ok()).toBeTruthy()
})

async function openFirstLab(page: Page) {
  await page.goto('/')
  await expect(page.locator('.learning-grid')).toBeVisible()
  await expect(page.getByRole('heading', { name: '先写清这个助手帮谁、不能干什么' })).toBeVisible()
  await expect(page.locator('#lab-brief')).toContainText('林舟加班到 22:30 回家')
  await expect(page.getByText('这一课大约 30 分钟。做完再看下一课。')).toBeVisible()
  await expect(page.locator('.takeaway')).toContainText('先写清帮谁、做成什么样、不能做什么，再让它动手。')
  await expect(page.locator('.remembered')).toHaveCount(0)
  await expect(page.getByText('大约 7 小时')).toHaveCount(0)
  await expect(page.getByRole('link', { name: '去作答', exact: true })).toHaveCount(0)
  await page.getByRole('link', { name: '读完了，去作答' }).click()
}

async function answerInOrder(page: Page, answers: string[]) {
  for (const [index, answer] of answers.entries()) {
    const question = page.locator('.quiz-question').nth(index)
    await expect(question).toBeVisible()
    await question.getByText(answer, { exact: true }).click()
    if (index < answers.length - 1) {
      await page.getByRole('button', { name: '下一题' }).click()
    }
  }
}

async function completeQuiz(page: Page, task: CatalogTask) {
  await expect(page.getByRole('heading', { name: task.title })).toBeVisible()
  const startQuiz = page.getByRole('link', { name: '读完了，去作答' })
  if (await startQuiz.count()) {
    await startQuiz.click()
  }
  await expect(page.locator('.quiz-question')).toHaveCount(task.quizQuestions.length)
  await expect(page.locator('.quiz-question:visible')).toHaveCount(1)
  await answerInOrder(page, correctLabels(task))
  const submit = page.getByRole('button', { name: '提交判断' })
  const check = page.waitForResponse((response) => {
    return new URL(response.url()).pathname === `/learn/tasks/${task.id}/check`
  })
  await submit.last().click()
  expect((await check).status()).toBe(200)
  await expect(page.getByText('已通过，得分 100。可以进入下一课。')).toBeVisible()
  await expect(page.locator('.quiz-question:visible')).toHaveCount(task.quizQuestions.length)
  if (task.takeaway) {
    await expect(page.locator('.takeaway')).toContainText(task.takeaway)
    await expect(page.locator('.remembered')).toContainText(task.takeaway)
  }
}

test('wrong answers show explanations and do not unlock the next lab', async ({ page }) => {
  await openFirstLab(page)

  const wrongOrRight = [
    '马上写更长的话，让它显得更懂林舟',
    '所有希望被理解的人',
    '需要改全组日历并通知同事的项目协调人',
    '让助手更聪明、更懂林舟',
    '只根据他贴出来的话给有限建议，并标明不确定的地方',
    '只使用他这次贴上来的待办',
    '改不回来的事先拒绝，没把握就停下来交给林舟',
    '拒绝改日历和群发，说明这回只根据贴上来的文字给建议',
    '标明缺了什么，或拒绝去做，不编造关键内容',
    '林舟贴一句待办后，最多返回 3 条下一步；做不到就明确拒绝',
  ]
  await answerInOrder(page, wrongOrRight)

  const submit = page.getByRole('button', { name: '提交判断' })
  const checkResponse = page.waitForResponse((response) => {
    return new URL(response.url()).pathname === '/learn/tasks/requirements-contract/check'
  })
  await submit.last().click()
  expect((await checkResponse).status()).toBe(200)

  await expect(page.getByText('未到及格分，当前得分 70。请看题下解释后再改。')).toBeVisible()
  await expect(page.getByText('本次 7/10 题正确')).toBeVisible()
  await expect(page.getByText('再看一眼').first()).toBeVisible()
  await expect(page.getByText('还有问题可以问')).toBeVisible()
  await expect(page.getByPlaceholder('这一题为什么错')).toBeHidden()
  const firstExplanation = page.locator('.quiz-question').first().locator('.quiz-explanation')
  await expect(firstExplanation).toContainText('先写清边界')
  await expect(firstExplanation).toBeInViewport()
  await expect(page.getByRole('button', { name: '进入下一课' })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '先写清这个助手帮谁、不能干什么' })).toBeVisible()
})

test('after the first lab a learner can try tonight in chat and come back', async ({ page }) => {
  const first = catalogTasks().find((task) => task.id === 'requirements-contract')
  if (!first) throw new Error('missing first lab')
  await openFirstLab(page)
  await completeQuiz(page, first)
  await expect(page.locator('.skill-block')).toHaveCount(0)
  await page.getByRole('button', { name: '去对话里试试今晚' }).click()
  await expect(page.getByRole('heading', { name: '试今晚' })).toBeVisible()
  await expect(page.getByText('下面这句是按刚才那一课写的。直接发送，看它会不会越出边界。')).toBeVisible()
  await expect(page.getByText('直接发送下面写好的那句。点「帮我理清今晚」也会带上这一句。')).toBeVisible()
  await expect(page.getByText('这是一个本地学习工作台')).toHaveCount(0)
  await expect(page.getByRole('button', { name: /帮我理清今晚/ })).toBeVisible()
  await expect(page.locator('.choice-button')).toHaveCount(1)
  await expect(page.locator('.choice-button.suggested')).toContainText('帮我理清今晚')
  await expect(page.getByRole('button', { name: '随便聊聊' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '写作教练' })).toHaveCount(0)
  await expect(page.locator('.context-panel')).toHaveCount(0)
  await expect(page.locator('#playerInput')).toBeFocused()
  if (!first.tonightPrompt) throw new Error('missing tonight prompt')
  await expect(page.locator('#playerInput')).toHaveValue(first.tonightPrompt)
  const chatResponse = page.waitForResponse((response) => new URL(response.url()).pathname === '/agent/next')
  await page.getByRole('button', { name: /帮我理清今晚/ }).click()
  expect((await chatResponse).status()).toBe(200)
  const reply = page.locator('.message.assistant').last()
  await expect(reply).toContainText('回客户邮件')
  await expect(reply).toContainText('不要下单')
  await expect(reply).not.toContainText('我可以陪你聊聊今晚')
  await expect(reply).not.toContainText('晚间计划助手:')
  await expect(page.locator('.dialogue-feed')).not.toContainText('Container framing')
  await expect(page.locator('.dialogue-feed')).not.toContainText('relationship vector')
  await expect(page.locator('.dialogue-feed')).not.toContainText('Current objective')
  await expect(page.locator('.dialogue-feed')).not.toContainText('acts as an independent')
  await page.getByRole('button', { name: '试完了？回到学习' }).click()
  await expect(page.getByRole('heading', { name: '点了生成之后，这次调用发生了什么' })).toBeVisible()
  await expect(page.locator('#lab-brief')).toContainText('点了「生成」之后')
})

test('an ordinary learner can finish the core path and reach electives', async ({ page }) => {
  test.setTimeout(180_000)
  const tasks = catalogTasks()
  const core = tasks.filter((task) => !task.elective)
  const electives = tasks.filter((task) => task.elective)
  expect(core).toHaveLength(17)
  expect(electives).toHaveLength(4)

  await openFirstLab(page)
  await expect(page.locator('.hero-intro .hero-copy')).toContainText('先读场景和讲义，再做十道判断')
  await expect(page.locator('.quiz-steps')).toContainText('读场景和讲义')
  await expect(page.locator('.quiz-steps')).toContainText('做完十道判断')
  await expect(page.locator('.quiz-steps')).toContainText('提交后看解释，80 分过关')
  const brief = page.locator('#lab-brief')
  await expect(brief.getByText('林舟加班到 22:30 回家')).toBeVisible()
  await expect(brief.getByText('核心概念')).toBeVisible()
  await expect(brief.getByText('官方参考')).toHaveCount(0)
  await expect(page.locator('.task-appendix')).toContainText('官方参考')
  await expect(page.getByRole('button', { name: '提交判断' }).first()).toBeDisabled()
  await expect(page.getByText('还差 10 题')).toBeVisible()
  await expect(page.getByText('第 1 / 10 题')).toBeVisible()
  await expect(page.getByRole('button', { name: '下一题' })).toBeDisabled()
  await expect(page.locator('.quiz-question:visible')).toHaveCount(1)
  await expect(page.getByPlaceholder('这一题为什么错')).toHaveCount(0)
  await expect(page.getByText('还没有结果')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '从晚间计划助手学起' })).toBeVisible()
  await expect(page.locator('.module-block.open .task-button')).toHaveCount(3)
  await expect(page.locator('.module-block.open .task-button').first()).toContainText('先写清这个助手帮谁、不能干什么')
  await expect(page.locator('.module-block.open .task-button.locked')).toHaveCount(2)
  await expect(page.locator('.module-block.open .lock-reason').first()).toHaveText('先完成上一课')
  await expect(page.getByRole('button', { name: /它看到什么、记住什么、留下什么 0\/3/ })).toHaveAttribute('aria-expanded', 'false')
  await expect(page.getByText('主线已完成，可继续选修。')).toHaveCount(0)

  for (const [index, task] of core.entries()) {
    if (index > 0) {
      await page.getByRole('button', { name: '进入下一课' }).click()
      await expect(page.locator('#lab-brief')).toBeInViewport()
    }
    await completeQuiz(page, task)
    if (index === 0) {
      await expect(page.getByRole('button', { name: /进入下一课/ })).toContainText('点了生成之后，这次调用发生了什么')
    }
  }

  await expect(page.getByText('主线已完成，可继续选修。')).toBeVisible()
  for (const [index, task] of electives.entries()) {
    await page.getByRole('button', { name: '进入下一课' }).click()
    await completeQuiz(page, task)
    if (index === electives.length - 1) {
      await expect(page.getByRole('button', { name: '进入下一课' })).toHaveCount(0)
    }
  }
})

test('on a narrow screen the current lesson comes before the syllabus', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await expect(page.locator('.learning-grid')).toBeVisible()
  await expect(page.getByRole('heading', { name: '先写清这个助手帮谁、不能干什么' })).toBeVisible()
  await expect(page.getByText('这一课大约 30 分钟。做完再看下一课。')).toBeVisible()
  await expect(page.locator('#lab-brief')).toBeInViewport()
  await expect(page.locator('.skill-block')).toBeHidden()
  const lessonTop = (await page.locator('#lab-brief').boundingBox())?.y ?? 0
  const railTop = (await page.locator('.course-rail').boundingBox())?.y ?? 0
  expect(lessonTop).toBeLessThan(railTop)
  await page.getByRole('link', { name: '读完了，去作答' }).click()
  await expect(page.locator('.quiz-steps')).toBeHidden()
  const option = page.locator('.quiz-option').first()
  await expect(option).toBeVisible()
  expect((await option.boundingBox())?.height ?? 0).toBeGreaterThanOrEqual(44)
})
