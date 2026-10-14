import { expect, test } from '@playwright/test'

test('bootstraps the local token and follows custom backend ports', async ({ page }) => {
  const progressResponse = page.waitForResponse((response) => {
    return new URL(response.url()).pathname === '/learn/progress'
  })

  await page.goto('/')

  const response = await progressResponse
  const request = response.request()
  expect(response.status()).toBe(200)
  expect(new URL(request.url()).port).toBe(process.env.KINIU_E2E_BACKEND_PORT || '18080')
  expect(request.headers()['x-local-token']).toBeTruthy()
  await expect(page.locator('.learning-grid')).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'Workspace' }).getByRole('button', { name: /流程|Flow/ })).toHaveCount(0)
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('shows a compact live-backend authentication error that opens settings', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('.learning-grid')).toBeVisible()

  await page.getByRole('button', { name: /设置|Settings/i }).click()
  const localTokenInput = page.getByRole('textbox', { name: /启动口令|Startup passphrase/i })
  await expect(localTokenInput).toBeHidden()
  await expect(page.getByText('学习课和试今晚都不用填模型')).toBeVisible()
  await expect(page.getByText('启动口令由一键启动填好，一般不用改')).toBeVisible()
  await expect(page.getByRole('button', { name: '恢复默认', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '只恢复语言和深浅色' })).toBeVisible()
  await expect(page.getByText('才需要模型地址和密钥')).toHaveCount(0)
  await expect(page.getByRole('textbox', { name: /模型地址|Upstream API/i })).toBeHidden()
  await page.locator('.advanced-studio summary').click()
  await expect(localTokenInput).toBeVisible()
  await localTokenInput.fill('definitely-wrong-local-token')
  await page.getByRole('button', { name: '只恢复语言和深浅色' }).click()
  await expect(localTokenInput).toHaveValue('definitely-wrong-local-token')
  await page.getByRole('button', { name: /保存设置|Save Settings/i }).click()

  const progressResponse = page.waitForResponse((response) => {
    return new URL(response.url()).pathname === '/learn/progress'
  })
  await page.getByRole('navigation', { name: 'Workspace' }).getByRole('button', { name: /学习|Learn/ }).click()
  const response = await progressResponse
  expect(response.status()).toBe(401)
  expect(response.request().headers()['x-local-token']).toBe('definitely-wrong-local-token')

  const alert = page.getByRole('alert')
  await expect(alert).toBeVisible()
  await expect(alert).toContainText(/启动口令|passphrase/i)
  expect((await alert.boundingBox())?.height).toBeLessThan(140)

  await alert.getByRole('button').click()
  await expect(localTokenInput).toBeVisible()
  await expect(localTokenInput).toHaveValue('definitely-wrong-local-token')
})
