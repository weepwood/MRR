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
  // E2E 跑在 Vite dev server 上，断言默认的 5s 对「会话校验 + 路由守卫跳转」这类
  // 需要等待接口返回的场景偏紧：并行 6 worker 时实测 1.6~2.3s 的跳转会被拉到 5s 以上，
  // 从而出现「行为正确但超时」的假失败。这里统一放宽到 15s；对时延本身有要求的
  // 用例仍在调用处显式指定更精确的 timeout 或使用 expect.poll。
  expect: {
    timeout: 15_000,
  },
  use: {
    baseURL,
    storageState: 'e2e/.auth/admin.json',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      // 先预热冷启动的 Vite dev server，避免首批并发用例拿到空页面而假失败。
      // setup project 与 webServer 的先后顺序由 Playwright 保证。
      name: 'warmup',
      testMatch: /warmup\.setup\.ts/,
    },
    {
      name: 'chromium',
      testMatch: /.*\.spec\.ts/,
      use: { ...devices['Desktop Chrome'] },
      dependencies: ['warmup'],
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
