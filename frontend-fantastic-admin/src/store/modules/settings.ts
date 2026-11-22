import type { RouteMeta } from 'vue-router'
import type { RecursiveRequired, Settings } from '#/global'
import { cloneDeep } from 'es-toolkit'
import settingsDefault from '@/settings'
import { readAuthStorage } from '@/utils/auth-storage'
import { notify } from '@/utils/notification'
import { merge } from '@/utils/object'
import { checkSessionPermission } from '@/utils/permission'
import { buildThemeColorScale, toCssVariables } from '@/utils/theme-color'
import { applyThemePalette, gr8rPrimary, normalizeThemePalette } from '@/utils/theme-palette'

const APP_SETTINGS_STORAGE_KEY = 'MRR-ADMIN:app-settings'

/** 服务端存储键：全局界面外观与导航外链。与后端 SystemSettingController.UI_SETTINGS_KEY 保持一致。 */
const UI_SETTINGS_SERVER_KEY = 'adminAppSettings'

/**
 * 写入全站界面配置所需的权限。
 * 与后端 {@code PUT /api/v1/settings} 的 {@code @RequirePermissions("system:manage")} 对齐。
 */
const UI_SETTINGS_SYNC_PERMISSION = 'system:manage'

/**
 * 全站界面配置的同步状态，供界面明确告知用户配置的实际生效范围。
 */
export type UiSettingsSyncState
  /** 尚未判定，或未登录 */
  = | 'idle'
  /** 已写入服务端，对所有终端生效 */
    | 'synced'
  /** 当前账号无 system:manage 权限，配置仅在本机生效 */
    | 'local-only'
  /** 已保存在本机，但写入服务端失败 */
    | 'failed'

/** 推送到服务端的防抖间隔，避免连续调整滑块时产生大量请求。 */
const UI_SETTINGS_PUSH_DELAY_MS = 800

/**
 * 需要全站统一的界面配置白名单。
 * <p>
 * 刻意**不包含** {@code menu.subMenuCollapse} 这类本机状态，
 * 避免把单个用户的折叠习惯推到全站；也不包含权限与路由来源等安全相关开关。
 * </p>
 */
const UI_SETTINGS_PICK: Record<string, readonly string[]> = {
  app: ['colorScheme', 'themePalette', 'themeColor', 'radius', 'radiusShape', 'pageTitleStyle', 'enableMournMode', 'enableColorAmblyopiaMode', 'enableDynamicTitle', 'enableProgress'],
  layout: ['enableMobileAdaptation'],
  menu: ['mode', 'mainMenuClickMode', 'subMenuUniqueOpened', 'enableSubMenuCollapseButton', 'enableHotkeys', 'externalLinks'],
  topbar: ['mode'],
  tabbar: ['enable', 'enableIcon', 'enableHotkeys'],
  toolbar: ['enable', 'breadcrumb', 'navSearch', 'fullscreen', 'pageReload', 'colorScheme'],
  mainPage: ['enableHotkeys'],
  navSearch: ['enableHotkeys'],
  home: ['enable', 'title', 'fullPath'],
  copyright: ['enable', 'dates', 'company', 'website', 'beian'],
}

function pickKeys(source: Record<string, unknown>, keys: readonly string[]): Record<string, unknown> {
  const picked: Record<string, unknown> = {}
  for (const key of keys) {
    if (Object.hasOwn(source, key)) {
      picked[key] = source[key]
    }
  }
  return picked
}

/** 抽取需要同步到服务端的界面配置切片。 */
function collectUiSettings(source: RecursiveRequired<Settings.all>): Record<string, unknown> {
  const raw = source as unknown as Record<string, unknown>
  const collected: Record<string, unknown> = {}
  for (const [section, keys] of Object.entries(UI_SETTINGS_PICK)) {
    const sectionValue = raw[section]
    if (sectionValue && typeof sectionValue === 'object' && !Array.isArray(sectionValue)) {
      collected[section] = pickKeys(sectionValue as Record<string, unknown>, keys)
    }
  }
  return collected
}

/**
 * 把主色写入根节点。
 * 色阶的推导（hover/active/light-x 等）见 `@/utils/theme-color`：明亮与暗黑两套方向不同，
 * 因此配色方案切换时必须重新推导，否则暗色下会残留浅色填充。
 */
function applyThemeColor(color: unknown, scheme: 'light' | 'dark') {
  const variables = toCssVariables(buildThemeColorScale(color, scheme))
  const rootStyle = document.documentElement.style
  Object.entries(variables).forEach(([name, value]) => rootStyle.setProperty(name, value))
}

function normalizePageTitleStyle(value: unknown): 'plain' | 'card' {
  if (value === 'plain' || value === 'compact') {
    return 'plain'
  }
  return 'card'
}

function getInitialSettings(): RecursiveRequired<Settings.all> {
  const defaults = cloneDeep(settingsDefault)

  try {
    const savedSettings = localStorage.getItem(APP_SETTINGS_STORAGE_KEY)
    const initialSettings = savedSettings ? merge(JSON.parse(savedSettings), defaults) : defaults
    initialSettings.app.pageTitleStyle = normalizePageTitleStyle(initialSettings.app.pageTitleStyle)
    initialSettings.app.themePalette = normalizeThemePalette(initialSettings.app.themePalette)
    return initialSettings
  }
  catch {
    return defaults
  }
}

/**
 * 判断请求错误是否由权限不足引起。
 * 后端以业务码 403 拒绝无权限写入（见 api/index.ts 的 BusinessRequestError）。
 */
function isForbiddenError(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return false
  }
  const { code, status } = error as { code?: unknown, status?: unknown }
  return [code, status].some(value => String(value ?? '') === '403')
}

export const useSettingsStore = defineStore(
  // 唯一ID
  'settings',
  () => {
    const settings = ref(getInitialSettings())

    /** 全站界面配置的同步状态，驱动界面上的生效范围提示。 */
    const uiSettingsSyncState = ref<UiSettingsSyncState>('idle')

    /** 全站界面配置推送的防抖句柄 */
    let uiSettingsPushTimer: number | undefined
    /**
     * 最近一次与服务端一致的全站界面配置（序列化后）。
     * 设置面板对整个 settings 做深度监听：折叠侧栏、切换标签等本机状态变化也会触发保存，
     * 而这些键并不在推送白名单里——原实现每次都会向服务端写一遍完全相同的内容。
     */
    let lastSyncedUiSettings = ''

    /** 当前登录会话是否具备把界面配置写入服务端的能力。 */
    function canSyncUiSettingsToServer(): boolean {
      const session = readAuthStorage()
      return checkSessionPermission(
        session.profile?.roleCode,
        session.permissions,
        UI_SETTINGS_SYNC_PERMISSION,
      )
    }

    const prefersColorScheme = window.matchMedia('(prefers-color-scheme: dark)')
    watch(() => settings.value.app.colorScheme, (val) => {
      document.documentElement.classList.add('disable-color-scheme-transition-duration')
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          document.documentElement.classList.remove('disable-color-scheme-transition-duration')
        })
      })
      if (val === '') {
        prefersColorScheme.addEventListener('change', updateTheme)
      }
      else {
        prefersColorScheme.removeEventListener('change', updateTheme)
      }
    }, {
      immediate: true,
    })

    const currentColorScheme = ref<Exclude<Settings.app['colorScheme'], ''>>()
    watch(() => settings.value.app.colorScheme, updateTheme, {
      immediate: true,
    })
    function updateTheme() {
      let colorScheme = settings.value.app.colorScheme
      if (colorScheme === '') {
        colorScheme = prefersColorScheme.matches ? 'dark' : 'light'
      }
      currentColorScheme.value = colorScheme
      switch (colorScheme) {
        case 'light':
          document.documentElement.classList.remove('dark')
          break
        case 'dark':
          document.documentElement.classList.add('dark')
          break
      }
    }

    watch(() => settings.value.app.radius, (val) => {
      document.documentElement.style.removeProperty('--radius')
      document.documentElement.style.setProperty('--radius', `${val}rem`)
    }, {
      immediate: true,
    })

    // G2 连续圆角（超椭圆）通过根节点属性开启，生效范围见 components/corner-shape.css。
    // 不支持的浏览器会忽略 corner-shape，仍按 border-radius 绘制标准圆弧。
    watch(() => settings.value.app.radiusShape, (val) => {
      document.documentElement.dataset.radiusShape = val === 'squircle' ? 'squircle' : 'round'
    }, {
      immediate: true,
    })

    watch([
      () => settings.value.app.themeColor,
      () => settings.value.app.themePalette,
      currentColorScheme,
    ], ([color, palette, scheme]) => {
      const resolvedScheme = scheme ?? 'light'
      const selected = normalizeThemePalette(palette)
      const root = document.documentElement
      // 切换完整调色板时先更新背景/导航/状态色，然后由唯一写入点推导主色色阶。
      applyThemePalette(root, selected, resolvedScheme)
      // Gr8r 深色主色单独提亮，原 MRR 自定义主色始终保留，不会被选择主题覆盖。
      applyThemeColor(selected === 'gr8r' ? gr8rPrimary(resolvedScheme) : color, resolvedScheme)
    }, {
      immediate: true,
    })

    watch(() => settings.value.app.pageTitleStyle, (val) => {
      document.documentElement.setAttribute('data-page-title-style', normalizePageTitleStyle(val))
    }, {
      immediate: true,
    })

    watch(() => settings.value.app.enableMotion, (enabled) => {
      document.documentElement.dataset.motion = enabled === false ? 'off' : 'on'
    }, {
      immediate: true,
    })

    watch([
      () => settings.value.app.enableMournMode,
      () => settings.value.app.enableColorAmblyopiaMode,
    ], (val) => {
      document.documentElement.style.removeProperty('filter')
      if (val[0] && val[1]) {
        document.documentElement.style.setProperty('filter', 'grayscale(100%) invert(80%)')
      }
      else if (val[0]) {
        document.documentElement.style.setProperty('filter', 'grayscale(100%)')
      }
      else if (val[1]) {
        document.documentElement.style.setProperty('filter', 'invert(80%)')
      }
    }, {
      immediate: true,
    })

    watch(() => settings.value.menu.mode, (val) => {
      document.body.setAttribute('data-menu-mode', val)
    }, {
      immediate: true,
    })

    // 操作系统
    const os = ref<'mac' | 'windows' | 'linux' | 'other'>('other')
    const agent = navigator.userAgent.toLowerCase()
    switch (true) {
      case agent.includes('mac os'):
        os.value = 'mac'
        break
      case agent.includes('windows'):
        os.value = 'windows'
        break
      case agent.includes('linux'):
        os.value = 'linux'
        break
    }

    // 页面是否刷新
    const isReloading = ref(false)
    // 切换当前页面是否刷新
    function setIsReloading(value?: boolean) {
      isReloading.value = value ?? !isReloading.value
    }

    // 页面标题
    const title = ref<RouteMeta['title']>()
    // 记录页面标题
    function setTitle(_title: RouteMeta['title']) {
      title.value = _title
    }

    // 显示模式
    const mode = ref<'pc' | 'mobile'>('pc')
    // 设置显示模式
    function setMode(width: number) {
      if (settings.value.layout.enableMobileAdaptation) {
        // 先判断 UA 是否为移动端设备（手机&平板）
        if (/Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)) {
          mode.value = 'mobile'
        }
        else {
          // 如果是桌面设备，则根据页面宽度判断是否需要切换为移动端展示
          mode.value = width < 1024 ? 'mobile' : 'pc'
        }
      }
      else {
        mode.value = 'pc'
      }
    }

    // 切换侧边栏导航展开/收起
    function toggleSidebarCollapse() {
      settings.value.menu.subMenuCollapse = !settings.value.menu.subMenuCollapse
    }
    // 次导航是否收起（用于记录 pc 模式下最后的状态）
    const subMenuCollapseLastStatus = ref(settingsDefault.menu.subMenuCollapse)
    watch(() => settings.value.menu.subMenuCollapse, (val) => {
      if (mode.value === 'pc') {
        subMenuCollapseLastStatus.value = val
      }
    })
    watch(mode, (val) => {
      switch (val) {
        case 'pc':
          settings.value.menu.subMenuCollapse = subMenuCollapseLastStatus.value
          break
        case 'mobile':
          settings.value.menu.subMenuCollapse = true
          break
      }
      document.body.setAttribute('data-mode', val)
    }, {
      immediate: true,
    })

    // 设置主题颜色模式
    function setColorScheme(color: Required<Settings.app>['colorScheme']) {
      settings.value.app.colorScheme = color
    }

    // 更新应用配置
    function updateSettings(data: Settings.all, fromBase = false) {
      settings.value = merge(data, fromBase ? getInitialSettings() : settings.value)
    }

    function writeLocalAppSettings() {
      localStorage.setItem(APP_SETTINGS_STORAGE_KEY, JSON.stringify(settings.value))
    }

    /**
     * 把全局界面外观与导航外链推送到服务端。
     * <p>
     * 服务端写接口要求 {@code system:manage} 权限，而外观入口对所有登录用户开放，
     * 因此必须区分三种结果，避免出现「看起来保存了、实际没有生效」：
     * </p>
     * <ul>
     *   <li>无权限：不发请求，标记为仅本机生效，由界面明确告知用户；</li>
     *   <li>成功：标记为已同步到全站；</li>
     *   <li>失败：保留本机配置并显式提示，不再静默吞掉错误。</li>
     * </ul>
     */
    async function pushUiSettings(): Promise<boolean> {
      const session = readAuthStorage()
      if (!session.token) {
        uiSettingsSyncState.value = 'idle'
        return false
      }
      if (!canSyncUiSettingsToServer()) {
        uiSettingsSyncState.value = 'local-only'
        return false
      }
      const payload = JSON.stringify(collectUiSettings(settings.value))
      if (payload === lastSyncedUiSettings) {
        uiSettingsSyncState.value = 'synced'
        return true
      }
      try {
        const { saveSystemSettings } = await import('@/api/modules/settings')
        await saveSystemSettings({
          [UI_SETTINGS_SERVER_KEY]: payload,
        })
        lastSyncedUiSettings = payload
        uiSettingsSyncState.value = 'synced'
        return true
      }
      catch (error) {
        if (isForbiddenError(error)) {
          // 权限可能在会话期内被回收，此时同样降级为仅本机生效。
          uiSettingsSyncState.value = 'local-only'
          return false
        }
        uiSettingsSyncState.value = 'failed'
        notify.error('界面外观已保存在本机，但同步到全站失败，请稍后重试')
        return false
      }
    }

    function scheduleUiSettingsPush() {
      if (typeof window === 'undefined') {
        return
      }
      if (uiSettingsPushTimer !== undefined) {
        window.clearTimeout(uiSettingsPushTimer)
      }
      uiSettingsPushTimer = window.setTimeout(() => {
        uiSettingsPushTimer = undefined
        void pushUiSettings()
      }, UI_SETTINGS_PUSH_DELAY_MS)
    }

    /**
     * 从服务端拉取全局外观与外链并应用到当前会话。
     * 服务端值优先，本机未覆盖的其余配置保持不变（白名单之外的本机状态不受影响）。
     * <p>
     * 无 {@code system:manage} 权限的账号不参与全站同步：既不推送也不拉取，
     * 否则其本机外观调整会在下次登录时被服务端值静默覆盖。
     * </p>
     *
     * @returns 请求是否已成功或无需请求。无权限与「服务端尚无数据」同样返回 true，
     *          用于避免无意义的重复请求。
     */
    async function loadUiSettings(): Promise<boolean> {
      const session = readAuthStorage()
      if (!session.token) {
        return false
      }
      if (!canSyncUiSettingsToServer()) {
        uiSettingsSyncState.value = 'local-only'
        return true
      }
      try {
        const { getUiSettings } = await import('@/api/modules/settings')
        const response = await getUiSettings()
        const raw = response.data?.[UI_SETTINGS_SERVER_KEY]
        if (!raw) {
          return true
        }
        const parsed = JSON.parse(raw)
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
          return true
        }
        settings.value = merge(parsed as Partial<Settings.all>, settings.value)
        writeLocalAppSettings()
        lastSyncedUiSettings = JSON.stringify(collectUiSettings(settings.value))
        return true
      }
      catch {
        return false
      }
    }

    // 本机持久化 + 全站同步：界面偏好既保留在本机，也同步到服务端供其他终端使用。
    // 无 system:manage 权限时 pushUiSettings 会降级为仅本机生效，并把结果写入 uiSettingsSyncState。
    function saveAppSettings() {
      writeLocalAppSettings()
      scheduleUiSettingsPush()
    }

    function resetAppSettings() {
      localStorage.removeItem(APP_SETTINGS_STORAGE_KEY)
      settings.value = cloneDeep(settingsDefault)
      scheduleUiSettingsPush()
    }

    return {
      settings,
      currentColorScheme,
      os,
      isReloading,
      setIsReloading,
      title,
      setTitle,
      mode,
      setMode,
      subMenuCollapseLastStatus,
      toggleSidebarCollapse,
      setColorScheme,
      updateSettings,
      saveAppSettings,
      resetAppSettings,
      loadUiSettings,
      uiSettingsSyncState,
    }
  },
)
