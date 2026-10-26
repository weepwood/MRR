import type { RouteMeta } from 'vue-router'
import type { RecursiveRequired, Settings } from '#/global'
import { cloneDeep } from 'es-toolkit'
import settingsDefault from '@/settings'
import { readAuthStorage } from '@/utils/auth-storage'
import { notify } from '@/utils/notification'
import { merge } from '@/utils/object'
import { checkSessionPermission } from '@/utils/permission'

const APP_SETTINGS_STORAGE_KEY = 'MRR-ADMIN:app-settings'
const DEFAULT_THEME_COLOR = '#2563EB'

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
  app: ['colorScheme', 'themeColor', 'radius', 'pageTitleStyle', 'enableMournMode', 'enableColorAmblyopiaMode', 'enableDynamicTitle', 'enableProgress'],
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

function normalizeThemeColor(value: unknown): string {
  const color = String(value || '').trim()
  return /^#[\dA-F]{6}$/i.test(color) ? color.toUpperCase() : DEFAULT_THEME_COLOR
}

function mixHexColor(source: string, target: string, sourceWeight: number): string {
  const sourceValue = Number.parseInt(source.slice(1), 16)
  const targetValue = Number.parseInt(target.slice(1), 16)
  const channels = [16, 8, 0].map((shift) => {
    const from = (sourceValue >> shift) & 0xFF
    const to = (targetValue >> shift) & 0xFF
    return Math.round(from * sourceWeight + to * (1 - sourceWeight))
  })
  return `#${channels.map(value => value.toString(16).padStart(2, '0')).join('').toUpperCase()}`
}

function toHslToken(color: string): string {
  const value = Number.parseInt(color.slice(1), 16)
  const red = ((value >> 16) & 0xFF) / 255
  const green = ((value >> 8) & 0xFF) / 255
  const blue = (value & 0xFF) / 255
  const maximum = Math.max(red, green, blue)
  const minimum = Math.min(red, green, blue)
  const lightness = (maximum + minimum) / 2
  const delta = maximum - minimum
  const saturation = delta === 0 ? 0 : delta / (1 - Math.abs(2 * lightness - 1))
  let hue = 0
  if (delta !== 0) {
    if (maximum === red) {
      hue = 60 * (((green - blue) / delta) % 6)
    }
    else if (maximum === green) {
      hue = 60 * ((blue - red) / delta + 2)
    }
    else {
      hue = 60 * ((red - green) / delta + 4)
    }
  }
  if (hue < 0) {
    hue += 360
  }
  return `${Math.round(hue)} ${Math.round(saturation * 100)}% ${Math.round(lightness * 100)}%`
}

function applyThemeColor(value: unknown) {
  const primary = normalizeThemeColor(value)
  const rootStyle = document.documentElement.style
  const primaryHsl = toHslToken(primary)
  const variables = {
    '--color-primary': primary,
    '--color-primary-hover': mixHexColor(primary, '#000000', 0.86),
    '--color-primary-active': mixHexColor(primary, '#000000', 0.72),
    '--color-primary-deep': mixHexColor(primary, '#000000', 0.58),
    '--primary': primaryHsl,
    '--ring': primaryHsl,
    '--el-color-primary': primary,
    '--el-color-primary-light-3': mixHexColor(primary, '#FFFFFF', 0.7),
    '--el-color-primary-light-5': mixHexColor(primary, '#FFFFFF', 0.5),
    '--el-color-primary-light-7': mixHexColor(primary, '#FFFFFF', 0.3),
    '--el-color-primary-light-8': mixHexColor(primary, '#FFFFFF', 0.2),
    '--el-color-primary-light-9': mixHexColor(primary, '#FFFFFF', 0.1),
    '--el-color-primary-dark-2': mixHexColor(primary, '#000000', 0.8),
  }
  Object.entries(variables).forEach(([name, color]) => rootStyle.setProperty(name, color))
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

    watch(() => settings.value.app.themeColor, applyThemeColor, {
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
      try {
        const { saveSystemSettings } = await import('@/api/modules/settings')
        await saveSystemSettings({
          [UI_SETTINGS_SERVER_KEY]: JSON.stringify(collectUiSettings(settings.value)),
        })
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
