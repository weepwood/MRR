import process from 'node:process'
import { defineConfig, devices } from '@playwright/test'

const baseURL = 'http://127.0.0.1:9000'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: process.env.CI
    ? [
        ['list'],
        ['html', { outputFolder: 'playwright-report', open: 'never' }],
        ['junit', { outputFile: 'test-results/e2e-junit.xml' }],
      ]
    : 'list',
  outputDir: 'test-results',
  use: {
    baseURL,
    storageState: 'e2e/.auth/admin.json',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    // E2E 使用真实路由认证流程和内置 Mock API，不启用自动注入 Dev User 的演示会话。
    command: 'pnpm exec vite --mode mock --host 127.0.0.1 --port 9000',
    env: {
      VITE_APP_DEMO_MODE: 'false',
    },
    url: baseURL,
    reuseExistingServer: !process.env.CI,
  },
})
