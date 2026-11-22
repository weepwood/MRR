import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import autoImport from 'unplugin-auto-import/vite'
import path from 'node:path'

export default defineConfig({
  plugins: [
    vue(),
    autoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      dirs: ['./src/store/modules', './src/utils/composables'],
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '#': path.resolve(__dirname, 'src/types'),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    restoreMocks: true,
    // Node 25+ 内置的 Web Storage 会遮住 jsdom 的 localStorage，见 vitest.setup.ts
    setupFiles: ['./vitest.setup.ts'],
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    coverage: {
      provider: 'v8',
      reportsDirectory: 'coverage',
      reporter: ['text', 'json-summary', 'lcov', 'html'],
      include: ['src/**/*.{ts,tsx,vue}'],
      exclude: [
        'src/**/*.d.ts',
        'src/**/*.test.{ts,tsx}',
        'src/**/__mocks__/**',
        'src/mock/**',
        'src/types/**',
        'src/router/routes/**',
        'src/router/generated/**',
      ],
    },
  },
})
