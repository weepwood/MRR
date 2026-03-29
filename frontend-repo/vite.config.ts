import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

const __dirname = dirname(fileURLToPath(import.meta.url))

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.VITE_API_TARGET || 'http://localhost:18045'

  return {
    plugins: [
      vue(),
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia'],
        resolvers: [ElementPlusResolver()],
        eslintrc: {
          enabled: true,
          filepath: './.eslintrc-auto-import.json',
          globalsPropValue: true
        }
      }),
      Components({
        resolvers: [ElementPlusResolver()]
      })
    ],
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
      setupFiles: ['./src/test/setup.ts'],
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
          target,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, '/v1')
        },
        '/loginApi': {
          target,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/loginApi/, '/login')
        },
        '/searchApi': {
          target,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/searchApi/, '/v2')
        }
      }
    }
  }
})
