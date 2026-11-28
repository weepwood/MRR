import process from 'node:process'
import { defineConfig } from '@playwright/test'

// Separate from Mock E2E: the real production bundle, loopback only, synthetic API fixtures.
const baseURL = 'http://127.0.0.1:19383'
const dist = process.env.MRR_STARTUP_DIST || 'dist'

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/startup-production.qualification.ts',
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: 'list',
  outputDir: process.env.MRR_STARTUP_RESULTS || 'test-results/startup-production',
  use: {
    baseURL,
    viewport: { width: 1440, height: 900 },
    storageState: { cookies: [], origins: [] },
    serviceWorkers: 'block',
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.0.0 Safari/537.36',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: `pnpm exec vite preview --host 127.0.0.1 --port 19383 --strictPort --outDir ${JSON.stringify(dist)}`,
    url: baseURL,
    reuseExistingServer: false,
    timeout: 30_000,
  },
})
