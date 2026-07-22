import process from 'node:process'
import { defineConfig, devices } from '@playwright/test'

const baseURL = 'http://127.0.0.1:9000'
const authenticatedStorageState = {
  cookies: [],
  origins: [
    {
      origin: baseURL,
      localStorage: [
        { name: 'mrr:auth:schema-version', value: '1' },
        { name: 'mrr:auth:token', value: 'mock-access-token' },
        { name: 'mrr:auth:account', value: '系统管理员' },
        {
          name: 'mrr:auth:profile',
          value: JSON.stringify({
            id: 1,
            username: 'admin',
            displayName: '系统管理员',
            roleCode: 'ADMIN',
            roleName: '管理员',
            status: 'ACTIVE',
            permissions: ['*'],
            mustChangePassword: false,
          }),
        },
        { name: 'mrr:auth:permissions', value: JSON.stringify(['*']) },
      ],
    },
  ],
}

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: 'list',
  use: {
    baseURL,
    storageState: authenticatedStorageState,
    trace: 'on-first-retry',
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
