import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/e2e',
  outputDir: '../../output/playwright/results',
  reporter: [
    ['line'],
    ['html', { outputFolder: '../../output/playwright/report', open: 'never' }],
  ],
  timeout: 30_000,
  expect: {
    timeout: 10_000,
  },
  workers: 1,
  use: {
    baseURL: process.env.KINIU_E2E_FRONTEND_URL || 'http://127.0.0.1:13000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
})
