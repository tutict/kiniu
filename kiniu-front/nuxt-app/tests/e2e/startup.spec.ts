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
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('shows a compact live-backend authentication error that opens settings', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('.learning-grid')).toBeVisible()

  await page.getByRole('button', { name: /设置|Settings/i }).click()
  const localTokenInput = page.getByRole('textbox', { name: /本机访问令牌|Local access token/i })
  await localTokenInput.fill('definitely-wrong-local-token')
  await page.getByRole('button', { name: /保存设置|Save Settings/i }).click()

  const progressResponse = page.waitForResponse((response) => {
    return new URL(response.url()).pathname === '/learn/progress'
  })
  await page.getByRole('button', { name: /学习中心|Learning Center/i }).click()
  const response = await progressResponse
  expect(response.status()).toBe(401)
  expect(response.request().headers()['x-local-token']).toBe('definitely-wrong-local-token')

  const alert = page.getByRole('alert')
  await expect(alert).toBeVisible()
  await expect(alert).toContainText(/令牌|token/i)
  expect((await alert.boundingBox())?.height).toBeLessThan(140)

  await alert.getByRole('button').click()
  await expect(localTokenInput).toHaveValue('definitely-wrong-local-token')
})
