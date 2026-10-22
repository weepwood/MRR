import fs from 'node:fs'
import process from 'node:process'
import { defineConfig } from '@playwright/test'

const executablePath = process.env.MRR_COMPAT_BROWSER_EXECUTABLE
const expectedMajor = process.env.MRR_COMPAT_BROWSER_MAJOR
if (Boolean(executablePath) !== Boolean(expectedMajor) || (expectedMajor && !/^\d+$/.test(expectedMajor))) {
  throw new Error('固定版本回归必须同时设置 MRR_COMPAT_BROWSER_EXECUTABLE 和数字 MRR_COMPAT_BROWSER_MAJOR。')
}
if (!fs.existsSync('dist/index.html')) {
  throw new Error('请先执行 pnpm build，兼容回归只测试生产构建 dist。')
}

export default defineConfig({
  testDir: './e2e/compat',
  outputDir: 'test-results/compat',
  workers: 1,
  retries: 0,
  forbidOnly: true,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: 'list',
  use: {
    browserName: 'chromium',
    baseURL: 'http://127.0.0.1:9010',
    launchOptions: { executablePath },
    serviceWorkers: 'block',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
  webServer: {
    command: 'pnpm exec vite preview --host 127.0.0.1 --port 9010 --strictPort',
    url: 'http://127.0.0.1:9010',
    reuseExistingServer: false,
  },
})
