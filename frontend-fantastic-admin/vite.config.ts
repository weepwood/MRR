import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { defineConfig, loadEnv } from 'vite'
import pkg from './package.json'
import createVitePlugins from './vite/plugins'

interface ReleaseBaseline {
  database: {
    minimumCompatibleMigration: string
    maximumCompatibleMigration: string
    backwardCompatibleWithPreviousApplication: boolean
  }
  applicationRollback: {
    allowed: boolean
    reason: string
  }
  configuration: {
    schemaVersion: number
  }
}

const repositoryRoot = path.resolve(__dirname, '..')

function readProductVersion(): string {
  const version = fs.readFileSync(path.join(repositoryRoot, 'VERSION'), 'utf8').trim()
  if (!version) {
    throw new Error('Repository root VERSION is empty')
  }
  return version
}

function readReleaseBaseline(): ReleaseBaseline {
  return JSON.parse(
    fs.readFileSync(path.join(repositoryRoot, 'release-baseline.json'), 'utf8'),
  ) as ReleaseBaseline
}

function resolveGitCommit(): string {
  const injectedCommit = process.env.MRR_GIT_COMMIT || process.env.GITHUB_SHA
  if (injectedCommit) {
    return injectedCommit
  }
  try {
    return execFileSync('git', ['rev-parse', 'HEAD'], {
      cwd: repositoryRoot,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim()
  }
  catch {
    return 'unknown'
  }
}

function resolveProxyOrigin(target: string): string | undefined {
  try {
    return new URL(target).origin
  }
  catch {
    return undefined
  }
}

// https://vitejs.dev/config/
export default defineConfig(({ mode, command }) => {
  const env = loadEnv(mode, process.cwd())
  const proxyOrigin = resolveProxyOrigin(env.VITE_APP_API_BASEURL)
  const productVersion = readProductVersion()
  const releaseBaseline = readReleaseBaseline()
  const gitCommit = resolveGitCommit()
  const buildTime = process.env.MRR_BUILD_TIME || new Date().toISOString()
  // 全局 scss 资源
  const scssResources: string[] = []
  fs.readdirSync('src/assets/styles/resources').forEach((dirname) => {
    if (fs.statSync(`src/assets/styles/resources/${dirname}`).isFile()) {
      scssResources.push(`@use "/src/assets/styles/resources/${dirname}" as *;`)
    }
  })
  return {
    // 开发服务器选项 https://cn.vitejs.dev/config/server-options
    server: {
      open: true,
      host: true,
      port: 9200,
      // Mock 模式由 vite-plugin-fake-server 直接处理 /proxy 请求，不再创建后端代理。
      proxy: env.VITE_BUILD_MOCK === 'true'
        ? undefined
        : {
            '/proxy': {
              target: env.VITE_APP_API_BASEURL,
              changeOrigin: command === 'serve' && env.VITE_OPEN_PROXY === 'true',
              rewrite: path => path.replace(/\/proxy/, ''),
              configure(proxy) {
                // 浏览器访问的是 http://localhost:9200/proxy，Vite 使用 changeOrigin
                // 转发后 Host 会变成后端地址，但原始 Origin 仍是 9200。Spring CORS
                // 会把这种开发代理请求误判为未授权跨域并在登录控制器前返回 403。
                // 仅开发代理改写 Origin，不扩大后端正式环境的 CORS 白名单。
                if (!proxyOrigin) {
                  return
                }
                proxy.on('proxyReq', (proxyRequest, request) => {
                  if (request.headers.origin) {
                    proxyRequest.setHeader('origin', proxyOrigin)
                  }
                })
              },
            },
          },
    },
    // 构建选项 https://cn.vitejs.dev/config/build-options
    build: {
      // Chrome 109 是内网终端的最低兼容基线；其余浏览器保持既有要求。
      target: ['chrome109', 'edge111', 'firefox114', 'safari16.4'],
      outDir: mode === 'production' ? 'dist' : `dist-${mode}`,
      sourcemap: env.VITE_BUILD_SOURCEMAP === 'true',
      rolldownOptions: {
        output: {
          // Vue 核心是所有页面的稳定公共依赖，单独分组便于长期缓存。
          // 其余依赖交给 Rolldown 按实际引用关系拆分，避免把仅在懒加载
          // 页面使用的 Element Plus、表单和 UI 模块合并进首屏 vendor。
          codeSplitting: {
            minSize: 20_000,
            groups: [
              {
                name: 'vendor-vue',
                test: /node_modules[\\/](?:@vue[\\/]|vue[\\/]|vue-router[\\/]|pinia[\\/])/,
                priority: 40,
              },
            ],
          },
        },
      },
    },
    define: {
      __SYSTEM_INFO__: JSON.stringify({
        product: {
          name: 'MRR',
          version: productVersion,
          gitCommit,
          buildTime,
          database: releaseBaseline.database,
          applicationRollback: releaseBaseline.applicationRollback,
          configurationSchemaVersion: releaseBaseline.configuration.schemaVersion,
        },
        template: {
          version: pkg.version,
        },
        dependencies: pkg.dependencies,
        devDependencies: pkg.devDependencies,
      }),
    },
    plugins: createVitePlugins(mode, command === 'build'),
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
        '#': path.resolve(__dirname, 'src/types'),
      },
    },
    css: {
      preprocessorOptions: {
        scss: {
          additionalData: scssResources.join(''),
        },
      },
    },
  }
})
