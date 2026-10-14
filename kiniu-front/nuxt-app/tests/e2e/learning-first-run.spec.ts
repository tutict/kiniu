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
  await expect(page.locator('.hero-intro h2')).toHaveText('先写清这个助手帮谁、不能干什么')
  await expect(page.getByRole('heading', { name: '先写清这个助手帮谁、不能干什么' })).toBeVisible()
  await expect(page.locator('.task-facts')).toContainText('十题至少对八题')
  await expect(page.locator('.task-facts')).not.toContainText('80 分')
  await expect(page.locator('.scenario-copy p').first()).toContainText('林舟加班到 22:30 回家')
  expect(await page.locator('.scenario-copy p').count()).toBeGreaterThan(1)
  await expect(page.locator('.task-facts')).toContainText('怎样算过')
  await expect(page.locator('.task-facts')).not.toContainText('分钟')
  await expect(page.locator('#lab-brief')).toContainText('林舟加班到 22:30 回家')
  const briefText = await page.locator('#lab-brief').innerText()
  expect(briefText.indexOf('先写清帮谁、做成什么样、不能做什么，再让它动手。')).toBeLessThan(briefText.indexOf('林舟加班到 22:30 回家'))
  expect(briefText.indexOf('林舟加班到 22:30 回家')).toBeLessThan(briefText.indexOf('先看这一题'))
  await expect(page.locator('#lab-brief .lesson-title')).toHaveCount(0)
  await expect(page.locator('#lab-brief .summary')).toHaveCount(0)
  await expect(page.getByText('这一课大约 30 分钟。做完先去对话里试一句，再看下一课。')).toBeVisible()
  await expect(page.locator('.takeaway')).toContainText('先写清帮谁、做成什么样、不能做什么，再让它动手。')
  await expect(page.locator('#lab-brief')).toContainText('这里说的边界，就是帮谁、做成什么样、不能做什么。')
  await expect(page.locator('.boundary-gloss')).toHaveCount(0)
  await expect(page.locator('.model-gloss')).toHaveCount(0)
  await expect(page.locator('.quiz-remember')).toContainText('先写清帮谁、做成什么样、不能做什么，再让它动手。')
  await expect(page.locator('.remembered')).toHaveCount(0)
  await expect(page.locator('.previous-takeaway')).toHaveCount(0)
  await expect(page.getByText('大约 7 小时')).toHaveCount(0)
  await expect(page.getByRole('link', { name: '去作答', exact: true })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '按题目来选' })).toBeVisible()
  await expect(page.locator('.quiz-ask')).toHaveText('这题问什么，就选什么。别只挑听起来最稳的。')
  await expect(page.getByRole('button', { name: '开始做这十道题' })).toBeVisible()
  await expect(page.getByText('通过后，对话里会放进')).toHaveCount(0)
  await expect(page.locator('.quiz-question:visible')).toHaveCount(0)
  await page.getByRole('navigation', { name: 'Workspace' }).getByRole('button', { name: '对话' }).click()
  await expect(page.getByRole('heading', { name: '先做完这一课' })).toBeVisible()
  await expect(page.getByText('对话里要试的那一句，等这一课通过后再放进来。先回去做十道判断。')).toBeVisible()
  await expect(page.getByRole('button', { name: '帮我理清今晚' })).toHaveCount(0)
  await page.getByRole('button', { name: '先回去做这一课' }).click()
  await expect(page.getByRole('heading', { name: '先写清这个助手帮谁、不能干什么' })).toBeVisible()
}

async function answerInOrder(page: Page, answers: string[]) {
  for (const [index, answer] of answers.entries()) {
    const question = page.locator('.quiz-question').nth(index)
    await expect(question).toBeVisible()
    await question.getByText(answer, { exact: true }).click()
    if (index === 0) {
      await expect(question.getByText('你选的是这个', { exact: true })).toBeVisible()
    }
    if (index < answers.length - 1) {
      const next = page.locator('.quiz-actions').getByRole('button', { name: '下一题' })
      await expect(next).toBeEnabled()
      await next.click()
    }
  }
}


async function expectGlossBeforeScenario(page: Page, gloss: string) {
  const briefText = await page.locator('#lab-brief').innerText()
  expect(briefText.indexOf(gloss)).toBeGreaterThanOrEqual(0)
  expect(briefText.indexOf(gloss)).toBeLessThan(briefText.indexOf('场景'))
}

async function completeQuiz(page: Page, task: CatalogTask) {
  await expect(page.getByRole('heading', { name: task.title })).toBeVisible()
  const startQuiz = page.getByRole('button', { name: '开始做这十道题' })
  if (await startQuiz.count()) {
    await startQuiz.click()
  }
  await expect(page.locator('.quiz-question')).toHaveCount(task.quizQuestions.length)
  await expect(page.locator('.quiz-question:visible')).toHaveCount(1)
  await expect(page.getByText('第 1 / 10 题')).toBeVisible()
  await expect(page.getByText('还差 10 题')).toBeVisible()
  await expect(page.getByRole('button', { name: '下一题' })).toBeDisabled()
  await expect(page.getByRole('button', { name: '提交判断' })).toHaveCount(0)
  await answerInOrder(page, correctLabels(task))
  const submit = page.getByRole('button', { name: '提交判断' })
  const check = page.waitForResponse((response) => {
    return new URL(response.url()).pathname === `/learn/tasks/${task.id}/check`
  })
  await submit.last().click()
  expect((await check).status()).toBe(200)
  await expect(page.getByText('对了 10 题，过了。先去对话里试试这句，也可以进入下一课。')).toBeVisible()
  await expect(page.getByText('最好对了')).toHaveCount(0)
  await expect(page.getByText(/本次 \d+\/10 题正确/)).toHaveCount(0)
  await expect(page.locator('.quiz-question:visible')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '这十题都对了，点开可以再看' })).toBeVisible()
  await expect(page.getByRole('button', { name: '去对话里试试今晚' })).toBeVisible()
  if (task.takeaway) {
    await expect(page.locator('.takeaway')).toContainText(task.takeaway)
    await expect(page.locator('.remembered')).toContainText(task.takeaway)
  }
}

test('wrong answers show explanations and do not unlock the next lab', async ({ page }) => {
  await openFirstLab(page)
  await page.getByRole('button', { name: '开始做这十道题' }).click()
  await expect(page.locator('.quiz-actions.docked')).toContainText('先写清帮谁、做成什么样、不能做什么，再让它动手。')
  await expect(page.locator('.option-mark')).toHaveCount(0)

  const wrongOrRight = [
    '马上写更长的话，让它显得更懂林舟',
    '所有希望被理解的人，只要他说想被理解，就算这回帮对了人',
    '改全组日历，并通知同事',
    '让助手更聪明、更懂林舟，回复也更有温度、更愿意再来',
    '根据他这句给有限建议，并标明不确定的地方',
    '只使用他这次贴上来的待办',
    '改不回来的事先拒绝，没把握就停下来交给林舟',
    '拒绝改日历和群发，说明这回只根据贴上来的文字给建议',
    '标明缺了什么，或拒绝去做，不编造关键内容',
    '林舟贴一句待办后，最多给出 3 条下一步；做不到就明确拒绝',
  ]
  await answerInOrder(page, wrongOrRight)

  const submit = page.getByRole('button', { name: '提交判断' })
  const checkResponse = page.waitForResponse((response) => {
    return new URL(response.url()).pathname === '/learn/tasks/requirements-contract/check'
  })
  await submit.last().click()
  expect((await checkResponse).status()).toBe(200)

  await expect(page.getByText('对了 7 题，还没到 8 题。点每道错题上的「题目要的是这个」，再交一次。')).toBeVisible()
  await expect(page.locator('.quiz-ask')).toBeVisible()
  await expect(page.getByText('本次 7/10 题正确')).toBeVisible()
  await expect(page.getByText('这题要改').first()).toBeVisible()
  await expect(page.getByText('还有问题可以问')).toHaveCount(0)
  await expect(page.getByPlaceholder('这一题为什么错')).toHaveCount(0)
  const firstExplanation = page.locator('.quiz-question').first().locator('.quiz-explanation')
  await expect(firstExplanation).toContainText('先写清帮谁、做成什么样、不能做什么')
  await expect(firstExplanation).toContainText('你选的是：马上写更长的话，让它显得更懂林舟')
  await expect(firstExplanation).toContainText('题目要的是：先写清帮谁、做成什么样、明确不做什么、怎样算合格')
  const explanationText = await firstExplanation.innerText()
  expect(explanationText.indexOf('你选的是')).toBeLessThan(explanationText.indexOf('先写清帮谁、做成什么样、不能做什么'))
  await expect(firstExplanation).toBeInViewport()
  const resubmit = page.getByRole('button', { name: '改完再交一次' })
  await expect(resubmit).toBeDisabled()
  const safer = page.getByText('题目要的是这个', { exact: true })
  await expect(safer).toHaveCount(3)
  for (let index = 0; index < 3; index += 1) {
    await safer.nth(index).click()
  }
  await expect(resubmit).toBeEnabled()
  await expect(page.locator('.quiz-question').first().locator('.quiz-explanation strong')).toHaveText('改好了，交上去再看')
  await expect(page.locator('.quiz-question').first().locator('.quiz-explanation')).toContainText('你改成了：先写清帮谁、做成什么样、明确不做什么、怎样算合格')
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
  await expect(page.getByText('下面这句是按刚才那一课写的。直接发送。回复里会有「我守住的是」，拿它和顶上「这一课记住」对一下：多做了你没让它做的事，就是越出去了。')).toBeVisible()
  await expect(page.getByText('说明', { exact: true })).toBeVisible()
  await expect(page.getByText('引导', { exact: true })).toHaveCount(0)
  await expect(page.locator('.learning-boundary')).toContainText(first.takeaway || '')
  await expect(page.getByText('直接发送下面写好的那句。点「帮我理清今晚」也会带上这一句。')).toBeVisible()
  await expect(page.getByText('这是一个本地学习工作台')).toHaveCount(0)
  await expect(page.getByRole('button', { name: /帮我理清今晚/ })).toBeVisible()
  await expect(page.locator('.choice-button')).toHaveCount(1)
  await expect(page.locator('.choice-button.suggested')).toContainText('帮我理清今晚')
  await expect(page.locator('.choice-button.suggested')).toContainText('发出下面写好的那句')
  await expect(page.locator('.choice-button')).not.toContainText('排出最多三条')
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
  await expect(reply.locator('.kept-line')).toContainText('我守住的是')
  await expect(reply.locator('.kept-line')).toContainText('帮谁')
  await expect(reply.locator('.kept-line')).toContainText('做成什么样')
  await expect(reply.locator('.kept-line')).toContainText('不能做什么')
  await expect(reply.locator('.kept-line')).toContainText('不要下单')
  await expect(page.locator('.choice-button')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '接着聊今晚' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '换一件别的事' })).toHaveCount(0)
  await expect(reply.locator('.kept-caption')).toHaveText('拿这句去对「这一课记住」')
  await expect(page.getByText('看回复里的「我守住的是」，再和顶上「这一课记住」对一下。多做了你没让它做的事，就是越出去了。想换自己的今晚，改下面这句再发送。')).toBeVisible()
  await expect(reply).not.toContainText('我可以陪你聊聊今晚')
  await expect(reply).not.toContainText('晚间计划助手:')
  await expect(page.locator('.dialogue-feed')).not.toContainText('Container framing')
  await expect(page.locator('.dialogue-feed')).not.toContainText('relationship vector')
  await expect(page.locator('.dialogue-feed')).not.toContainText('Current objective')
  await expect(page.locator('.dialogue-feed')).not.toContainText('acts as an independent')
  await page.getByRole('button', { name: '试完了？回到这一课' }).click()
  await expect(page.getByRole('heading', { name: '先写清这个助手帮谁、不能干什么' })).toBeVisible()
  await page.getByRole('navigation', { name: 'Workspace' }).getByRole('button', { name: '对话' }).click()
  await expect(page.getByRole('heading', { name: '试今晚' })).toBeVisible()
  await expect(page.locator('.kept-caption')).toHaveText('拿这句去对「这一课记住」')
  await expect(page.getByRole('button', { name: '接着聊今晚' })).toHaveCount(0)
  await page.getByRole('button', { name: '试完了？回到这一课' }).click()
  await expect(page.getByRole('heading', { name: '先写清这个助手帮谁、不能干什么' })).toBeVisible()
  await expect(page.locator('.previous-takeaway')).toHaveCount(0)
  await expect(page.getByRole('button', { name: /进入下一课/ })).toContainText('点了生成之后，这次有没有做成')
  await page.getByRole('button', { name: '进入下一课' }).click()
  await expect(page.getByRole('heading', { name: '点了生成之后，这次有没有做成' })).toBeVisible()
  await expect(page.locator('.previous-takeaway')).toContainText('先写清帮谁、做成什么样、不能做什么，再让它动手。')
  await expect(page.locator('#lab-brief')).toContainText('点了「生成」之后')
  await expectGlossBeforeScenario(page, '模型就是背后负责回答的那个程序。')
})

test('an ordinary learner can finish the core path and reach electives', async ({ page }) => {
  test.setTimeout(180_000)
  const tasks = catalogTasks()
  const core = tasks.filter((task) => !task.elective)
  const electives = tasks.filter((task) => task.elective)
  expect(core).toHaveLength(17)
  expect(electives).toHaveLength(4)

  await openFirstLab(page)
  await expect(page.locator('.hero-intro .hero-copy')).toContainText('先记住这一句，再读场景和讲义，然后做十道判断')
  await expect(page.locator('.quiz-steps')).toContainText('先记住这一句，再读场景和讲义')
  await expect(page.locator('.quiz-steps')).toContainText('选好了再点下一题')
  await expect(page.locator('.quiz-steps')).toContainText('十题至少对八题才过')
  const brief = page.locator('#lab-brief')
  await expect(brief.getByText('林舟加班到 22:30 回家')).toBeVisible()
  await expect(brief.getByText('先看这一题')).toBeVisible()
  await expect(brief.getByText('官方参考')).toHaveCount(0)
  await expect(page.locator('.task-appendix')).toContainText('想核对原文')
  await page.locator('.task-appendix summary').click()
  await expect(page.locator('.reference-list')).toContainText('生成式助手的风险怎么管')
  await expect(page.locator('.reference-list')).not.toContainText('AI RMF')
  await expect(page.locator('.reference-list')).not.toContainText('NIST')
  await expect(page.locator('.quiz-question:visible')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '提交判断' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '开始做这十道题' })).toBeVisible()
  await expect(page.getByPlaceholder('这一题为什么错')).toHaveCount(0)
  await expect(page.getByText('还没有结果')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '先说清楚，再看点了生成之后' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '从晚间计划助手学起' })).toHaveCount(0)
  await expect(page.locator('.module-block.open .task-button')).toHaveCount(3)
  await expect(page.getByText('先写清帮谁、不能干什么；再看点了生成之后发生了什么')).toHaveCount(0)
  await expect(page.locator('.module-block.open .task-button').first()).toContainText('先写清这个助手帮谁、不能干什么')
  await expect(page.locator('.module-block.open .task-button.locked')).toHaveCount(2)
  await expect(page.locator('.module-block.open .lock-reason').first()).toHaveText('先完成上一课')
  await expect(page.locator('.module-heading small')).toHaveCount(0)
  await expect(page.locator('.task-button').getByText('分钟')).toHaveCount(0)
  await expect(page.getByRole('button', { name: /它看到什么、记住什么、留下什么/ })).toHaveCount(0)
  await expect(page.getByText('这些课学完了。后面的可以接着学，也可以停在这里。')).toHaveCount(0)
  await expect(page.locator('.course-rail-head').first()).toContainText('先学这些')
  await expect(page.locator('.course-rail-head').first()).not.toContainText('/17')
  await expect(page.getByText('学完了多少')).toHaveCount(0)

  for (const [index, task] of core.entries()) {
    if (index > 0) {
      await page.getByRole('button', { name: '进入下一课' }).click()
      await expect(page.locator('#lab-brief')).toBeInViewport()
    }
    if (task.id === 'http-json-basics') {
      await expect(page.locator('.boundary-gloss')).toHaveCount(0)
      await expect(page.locator('.model-gloss')).toHaveCount(2)
      await expect(page.locator('#lab-brief .model-gloss')).toHaveText('模型就是背后负责回答的那个程序。')
      await expect(page.locator('#lab-quiz .model-gloss')).toHaveText('模型就是背后负责回答的那个程序。')
      await expectGlossBeforeScenario(page, '模型就是背后负责回答的那个程序。')
    }
    if (task.id === 'model-response-contract') {
      await expect(page.locator('.model-gloss')).toHaveCount(2)
      await expect(page.locator('#lab-brief .model-gloss')).toHaveText('模型就是背后负责回答的那个程序。')
      await expect(page.locator('#lab-quiz .model-gloss')).toHaveText('模型就是背后负责回答的那个程序。')
    }
    if (task.id === 'data-lifecycle') {
      await expect(page.getByRole('button', { name: /它看到什么、记住什么、留下什么/ })).toBeVisible()
      await expect(page.getByRole('heading', { name: '它看到什么、记住什么、留下什么' })).toBeVisible()
    }
    if (task.id === 'data-lifecycle' || task.id === 'mcp-integration') {
      await expect(page.locator('.boundary-gloss')).toHaveCount(2)
      await expect(page.locator('#lab-brief .boundary-gloss')).toHaveText('边界就是帮谁、做成什么样、不能做什么。')
      await expect(page.locator('#lab-quiz .boundary-gloss')).toHaveText('边界就是帮谁、做成什么样、不能做什么。')
      await expectGlossBeforeScenario(page, '边界就是帮谁、做成什么样、不能做什么。')
    }
    await completeQuiz(page, task)
    if (index === 0) {
      await expect(page.getByRole('button', { name: /进入下一课/ })).toContainText('点了生成之后，这次有没有做成')
      await expect(page.locator('.hero-intro .hero-meta')).toHaveText('这一课过了。先去对话里试试今晚。')
      await expect(page.getByText('大约 7 小时')).toHaveCount(0)
      await expect(page.getByText('learningCoreOnly')).toHaveCount(0)
      await expect(page.locator('.hero-progress-card')).toContainText('一次只做眼前这一课。后面的课等这些做完再出现。')
      await expect(page.locator('.hero-progress-card')).not.toContainText('/17')
      await expect(page.getByText('学完了多少')).toHaveCount(0)
    }
  }

  await expect(page.getByText('这些课学完了。后面的可以接着学，也可以停在这里。')).toBeVisible()
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
  await expect(page.getByText('这一课大约 30 分钟。做完先去对话里试一句，再看下一课。')).toBeVisible()
  await expect(page.locator('#lab-brief')).toBeInViewport()
  await expect(page.locator('.skill-block')).toBeHidden()
  const lessonTop = (await page.locator('#lab-brief').boundingBox())?.y ?? 0
  const railTop = (await page.locator('.course-rail').boundingBox())?.y ?? 0
  expect(lessonTop).toBeLessThan(railTop)
  await page.getByRole('button', { name: '开始做这十道题' }).click()
  await expect(page.locator('.quiz-steps')).toBeHidden()
  const option = page.locator('.quiz-option').first()
  await expect(option).toBeVisible()
  expect((await option.boundingBox())?.height ?? 0).toBeGreaterThanOrEqual(44)
})
