import type { RecursiveRequired, Settings } from '#/global'
import type { RouteMeta } from 'vue-router'
import { cloneDeep } from 'es-toolkit'
import settingsDefault from '@/settings'
import { merge } from '@/utils/object'

const APP_SETTINGS_STORAGE_KEY = 'MRR-ADMIN:app-settings'
const DEFAULT_THEME_COLOR = '#2563EB'

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

export const useSettingsStore = defineStore(
  // 唯一ID
  'settings',
  () => {
    const settings = ref(getInitialSettings())

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

    function saveAppSettings() {
      localStorage.setItem(APP_SETTINGS_STORAGE_KEY, JSON.stringify(settings.value))
    }

    function resetAppSettings() {
      localStorage.removeItem(APP_SETTINGS_STORAGE_KEY)
      settings.value = cloneDeep(settingsDefault)
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
    }
  },
)
