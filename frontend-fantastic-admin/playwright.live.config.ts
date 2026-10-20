import process from 'node:process'
import { defineConfig, devices } from '@playwright/test'

if (process.env.MRR_LIVE_MANAGED !== '1' || !/^http:\/\/127\.0\.0\.1:\d+$/.test(process.env.MRR_LIVE_API_URL || '')) {
  throw new Error('Run scripts/run_live_e2e.py to create an isolated backend and database.')
}
const port = Number(process.env.MRR_LIVE_WEB_PORT)
if (!Number.isInteger(port) || port < 1024 || port > 65535) {
  throw new Error('A managed loopback web port is required.')
}
const baseURL = `http://127.0.0.1:${port}`

export default defineConfig({
  testDir: './e2e-live',
  outputDir: process.env.MRR_LIVE_RESULTS_DIR,
  workers: 1,
  retries: 0,
  forbidOnly: true,
  timeout: 90_000,
  expect: { timeout: 30_000 },
  reporter: 'list',
  // Login credentials and JWTs must not enter traces/screenshots/artifacts.
  use: { baseURL, trace: 'off', screenshot: 'off', video: 'off' },
  projects: [{ name: 'live-chromium', use: { ...devices['Desktop Chrome'] } }],
})
