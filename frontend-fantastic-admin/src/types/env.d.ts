interface ImportMetaEnv {
  // Auto generate by env-parse
  readonly VITE_APP_API_BASEURL: string
  readonly VITE_APP_DEBUG_TOOL: string
  readonly VITE_APP_DEMO_MODE: boolean
  readonly VITE_APP_DISABLE_DEVTOOL: boolean
  /**
   * 无后端开发与自动化测试模式（不包含敏感信息）
   */
  readonly VITE_APP_SETTING: boolean
  readonly VITE_APP_TITLE: string
  /**
   * Mock 模式由 vite-plugin-fake-server 拦截 /proxy 下的请求
   */
  readonly VITE_BUILD_MOCK: boolean
  readonly VITE_OPEN_DEVTOOLS: boolean
  readonly VITE_OPEN_PROXY: boolean
}
