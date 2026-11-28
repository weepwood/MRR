import path from 'node:path'
import process from 'node:process'
import { defineConfig } from '@playwright/test'
import base from './playwright.local-table-unify.config'

const dist = process.env.MRR_PRODUCTION_MOCK_DIST
if (!dist || !dist.startsWith('/') || !dist.endsWith('/mock-dist')) {
  throw new Error('指定独立的绝对 mock-dist 产物目录')
}

export default defineConfig({
  ...base,
  outputDir: path.join(path.dirname(dist), 'browser-results'),
  webServer: {
    command: 'pnpm exec vite preview --mode mock --host 127.0.0.1 --port 9041 --strictPort --outDir "$MRR_PRODUCTION_MOCK_DIST"',
    env: { VITE_APP_DEMO_MODE: 'false' },
    url: 'http://127.0.0.1:9041',
    reuseExistingServer: false,
  },
})
