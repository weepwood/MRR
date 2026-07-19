import path from 'node:path'
import vue from '@vitejs/plugin-vue'
import autoImport from 'unplugin-auto-import/vite'
import { defineConfig } from 'vitest/config'

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
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
  },
})
