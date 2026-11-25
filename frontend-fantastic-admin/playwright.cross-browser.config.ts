import { defineConfig, devices } from '@playwright/test'
import base from './playwright.config'

// Explicit candidate qualification; the default PR/release Chromium gate is unchanged.
const warmup = base.projects!.find(project => project.name === 'warmup')!
const workflowChecks = /(?:v11-entry-quality|v11-loaded-states|v11-repair-workbench|legacy-route-settings|auth-storage-healing|database-browse-demo|runtime-error-detail|query-page-consistency|shared-ui-layout|global-visual)\.spec\.ts$/
const routeMatrix = /candidate-route-matrix\.qualification\.ts$/

export default defineConfig({
  ...base,
  workers: 2,
  forbidOnly: true,
  retries: 0,
  outputDir: 'test-results-cross-browser',
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-cross-browser-report', open: 'never' }],
    ['junit', { outputFile: 'test-results-cross-browser/e2e-junit.xml' }],
  ],
  projects: [
    warmup,
    {
      name: 'chromium-matrix',
      testMatch: routeMatrix,
      use: { ...devices['Desktop Chrome'] },
      dependencies: ['warmup'],
    },
    {
      name: 'firefox',
      testMatch: [routeMatrix, workflowChecks],
      use: { ...devices['Desktop Firefox'] },
      dependencies: ['warmup'],
    },
    {
      name: 'webkit',
      testMatch: [routeMatrix, workflowChecks],
      use: { ...devices['Desktop Safari'] },
      dependencies: ['warmup'],
    },
  ],
})
