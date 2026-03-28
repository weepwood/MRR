import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.js'],
    include: ['src/**/*.test.{js,ts}']
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:18045',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '/v1')
      },
      '/loginApi':{
        target: 'http://localhost:18045',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/loginApi/,'/login')
      },
      '/searchApi':{
        target: 'http://localhost:18045',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/searchApi/,'/v2')
      }
    }
  }
})
