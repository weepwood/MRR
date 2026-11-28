interface ImportMetaEnv {
  // Auto generate by env-parse
  /**
   * 接口请求地址，会设置到 axios 的 baseURL 参数上
   */
  readonly VITE_APP_API_BASEURL: string
  /**
   * 调试工具，可设置 eruda 或 vconsole，如果不需要开启则留空
   */
  readonly VITE_APP_DEBUG_TOOL: string
  /**
   * 生产环境必须使用后端真实 JWT 登录。
   */
  readonly VITE_APP_DEMO_MODE: boolean
  /**
   * 是否禁用开发者工具，可防止被调试
   */
  readonly VITE_APP_DISABLE_DEVTOOL: boolean
  /**
   * 应用配置面板
   */
  readonly VITE_APP_SETTING: boolean
  /**
   * 页面标题
   */
  readonly VITE_APP_TITLE: string
  /**
   * 是否在打包后生成存档，支持 zip 和 tar
   */
  readonly VITE_BUILD_ARCHIVE: string
  /**
   * 是否在打包时开启压缩，支持 gzip 和 brotli
   */
  readonly VITE_BUILD_COMPRESS: string
  /**
   * 是否在打包时启用 Mock
   */
  readonly VITE_BUILD_MOCK: boolean
  /**
   * 是否为现代构建补充少量运行时 polyfill；不会生成 IE/旧 Edge 兼容包
   */
  readonly VITE_BUILD_MODERN_POLYFILLS: boolean
  /**
   * 是否在打包时生成 sourcemap
   */
  readonly VITE_BUILD_SOURCEMAP: boolean
}
