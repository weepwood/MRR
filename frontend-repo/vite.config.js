import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  esbuild: {
    target: 'chrome86'
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  build: {
    target: 'chrome86',
    cssTarget: 'chrome86',
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('@element-plus/icons-vue')) return 'vendor-icons'
            if (id.includes('element-plus')) return 'vendor-element-plus'
            if (id.includes('vue')) return 'vendor-vue'
            if (id.includes('jspdf') || id.includes('html2canvas') || id.includes('viewerjs')) {
              return 'vendor-archive'
            }
            return 'vendor'
          }
        }
      }
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.js'],
    include: ['src/**/*.test.{js,ts}']
  },
  optimizeDeps: {
    esbuildOptions: {
      target: 'chrome86'
    }
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
