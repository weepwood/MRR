import type { EffectiveSystemSettings } from '@/utils/system-settings'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { saveSystemSettings } from '@/api/modules/settings'
import { actionDialog } from '@/ui/components/ActionDialog'
import {
  createDefaultSystemSettings,
  isAllowedDocumentationUrl,
  loadEffectiveSystemSettings,
  parseSystemSettings,
  serializeSystemSettings,
  writeLocalSystemSettings,
} from '@/utils/system-settings'
import { describeSettingChanges } from '../settings-changes'

export type SettingsSection = 'system' | 'login-support' | 'documentation' | 'archive' | 'ocr' | 'security' | 'runtime' | 'developer' | 'department' | 'external-links' | 'appearance'

export const settingsNavItems = [
  { key: 'system', title: '系统信息', description: '名称、机构与系统简介', icon: 'i-lucide:info' },
  { key: 'login-support', title: '登录与支持', description: '登录展示与管理员联系', icon: 'i-lucide:headset' },
  { key: 'documentation', title: '帮助与文档', description: '用户、开发与运维文档链接', icon: 'i-lucide:book-open' },
  { key: 'archive', title: '档案浏览', description: '图片来源与加载策略', icon: 'i-lucide:image' },
  { key: 'ocr', title: 'OCR 与智能分类', description: '引擎、词库与分类安全阈值', icon: 'i-lucide:scan' },
  { key: 'security', title: '访问安全', description: '水印、身份证与 IP 限制', icon: 'i-lucide:shield-check' },
  { key: 'runtime', title: '运行参数', description: '限流阈值、会话与日志留存', icon: 'i-lucide:layout-dashboard' },
  { key: 'developer', title: '开发者模式', description: '旧接口与可信来源', icon: 'i-lucide:code-xml' },
  { key: 'department', title: '科室档案袋配色', description: '档案袋颜色规则', icon: 'i-lucide:palette' },
  { key: 'external-links', title: '外部链接', description: '维护“其他”导航入口', icon: 'i-lucide:link-2' },
  { key: 'appearance', title: '界面外观', description: '主题、导航与页面样式', icon: 'i-lucide:layout-dashboard' },
] as const

function splitDeveloperSources(value: string): string[] {
  return value
    .split(/[,;\r\n]+/)
    .map(item => item.trim())
    .filter(Boolean)
}

function isValidDeveloperSourceShape(value: string): boolean {
  const parts = value.split('/')
  if (parts.length > 2 || !/^[0-9a-f:.]+$/i.test(parts[0] || '')) { return false }
  const address = parts[0] || ''
  if (!address.includes('.') && !address.includes(':')) { return false }
  if (parts.length === 1) { return true }

  const prefix = Number(parts[1])
  const maxPrefix = address.includes(':') ? 128 : 32
  return Number.isInteger(prefix) && prefix >= 0 && prefix <= maxPrefix
}

function isValidEmailAddress(value: string): boolean {
  const email = value.trim()
  if (!email || /\s/.test(email)) { return false }

  const atIndex = email.indexOf('@')
  if (atIndex <= 0 || atIndex !== email.lastIndexOf('@')) { return false }

  const domain = email.slice(atIndex + 1)
  if (!domain || domain.startsWith('.') || domain.endsWith('.')) { return false }

  const labels = domain.split('.')
  return labels.length >= 2 && labels.every(label => label.length > 0)
}

export function useUnifiedSettings() {
  const route = useRoute()
  const router = useRouter()
  const activeSection = ref<SettingsSection>('system')
  const loading = ref(false)
  const saving = ref(false)
  /**
   * 是否已从服务器读到可保存的基线。读取成功前表单里只有默认值（或上一次成功读取的值），
   * 既不算修改，也不能保存：保存会提交全部字段，回退值会整批覆盖服务器设置。
   */
  const loaded = ref(false)
  const loadError = ref('')
  const savedSnapshot = ref<Record<string, string>>({})
  const settings = ref<EffectiveSystemSettings>(createDefaultSystemSettings())

  const activeMeta = computed(() => settingsNavItems.find(item => item.key === activeSection.value)!)
  const isServerSettingSection = computed(() => [
    'system',
    'login-support',
    'documentation',
    'archive',
    'security',
    'developer',
  ].includes(activeSection.value))
  const currentSnapshot = computed(() => serializeSystemSettings(settings.value))
  const changedKeys = computed(() => Object.keys(currentSnapshot.value).filter(key => currentSnapshot.value[key] !== savedSnapshot.value[key]))
  const isDirty = computed(() => loaded.value && changedKeys.value.length > 0)
  /** 保存前复核用的可读变更清单（字段中文名 + 旧值 → 新值）。 */
  const changedItems = computed(() => describeSettingChanges(
    changedKeys.value,
    currentSnapshot.value,
    savedSnapshot.value,
  ))
  const developerModeChanged = computed(() => changedKeys.value.some(key => [
    'developerModeEnabled',
    'developerModeAllowedSources',
  ].includes(key)))
  const savedDeveloperModeEnabled = computed(() => savedSnapshot.value.developerModeEnabled === 'true')

  function isSettingsSection(value: unknown): value is SettingsSection {
    return settingsNavItems.some(item => item.key === value)
  }

  /**
   * 记录已保存状态。
   * 保存成功后必须用「实际提交的快照」而不是实时表单值：请求在途时用户仍可继续
   * 输入，用实时值会让这些未提交的改动被标记为已保存，静默丢失。
   */
  function markAsSaved(snapshot: Record<string, string> = currentSnapshot.value) {
    savedSnapshot.value = { ...snapshot }
  }

  /**
   * 放弃未保存的修改，把表单还原到最近一次保存／加载时的快照。
   * 设置页被 KeepAlive 缓存，离开再回来不会重新拉取设置，因此「放弃」必须自己回滚，
   * 否则被放弃的值会留在表单里，并可能在下一次保存时写进系统数据库。
   */
  function discardUnsavedChanges() {
    settings.value = parseSystemSettings({ ...savedSnapshot.value })
  }

  async function loadSettings(showMessage = false): Promise<boolean> {
    loading.value = true
    try {
      const result = await loadEffectiveSystemSettings()
      if (!result.serverReachable) {
        // 回退值只供只读页面降级使用。已有服务器基线时保留当前表单与基线；首次失败由页面显示错误与重试。
        loadError.value = '系统设置读取失败'
        if (loaded.value || showMessage) { ElMessage.error('系统设置读取失败，当前内容未更新，请稍后重新加载') }
        return false
      }
      // 服务器可达但还没有任何设置（全新部署）时以默认值为基线，不采用可能来自其他会话的本地缓存。
      settings.value = result.source === 'server' ? result.settings : createDefaultSystemSettings()
      markAsSaved()
      loaded.value = true
      loadError.value = ''
      if (showMessage) { ElMessage.success('设置已重新加载') }
      return true
    }
    finally {
      loading.value = false
    }
  }

  function validateSettings() {
    const value = settings.value
    if (!value.systemName.trim() || !value.systemShortName.trim() || !value.systemEnglishName.trim()) {
      ElMessage.warning('系统名称、简称和英文名称不能为空')
      return false
    }
    if (value.systemAdminEmail && !isValidEmailAddress(value.systemAdminEmail)) {
      ElMessage.warning('系统管理员邮箱格式不正确')
      return false
    }
    if (value.systemAdminContactEnabled && value.systemAdminPublicVisible && !value.systemAdminPhone && !value.systemAdminEmail) {
      ElMessage.warning('公开管理员信息时至少填写联系电话或联系邮箱')
      return false
    }

    const documentationLinks = [
      ['用户使用手册', value.documentationUserGuideUrl],
      ['开发文档', value.documentationDeveloperUrl],
      ['运维指南', value.documentationOperationsUrl],
    ] as const
    const invalidDocumentationLink = documentationLinks.find(([, url]) => !isAllowedDocumentationUrl(url))
    if (invalidDocumentationLink) {
      ElMessage.warning(`${invalidDocumentationLink[0]}链接仅允许 HTTP(S) 地址或以 / 开头的站内路径`)
      return false
    }

    if (!Number.isInteger(value.archiveIpMaxChanges) || value.archiveIpMaxChanges < 0 || value.archiveIpMaxChanges > 20) {
      ElMessage.warning('每日 IP 切换次数必须是 0 到 20 之间的整数')
      return false
    }

    const developerSources = splitDeveloperSources(value.developerModeAllowedSources)
    if (value.developerModeEnabled && developerSources.length === 0) {
      ElMessage.warning('启用开发者模式前，至少配置一个允许访问的 IP 或网段')
      return false
    }
    const invalidSource = developerSources.find(item => !isValidDeveloperSourceShape(item))
    if (invalidSource) {
      ElMessage.warning(`可信来源格式不正确：${invalidSource}`)
      return false
    }
    return true
  }

  async function confirmDeveloperModeEnable() {
    if (!developerModeChanged.value || !settings.value.developerModeEnabled || savedDeveloperModeEnabled.value) { return true }
    try {
      await actionDialog.confirm(
        '启用后，只有配置的 IP 或网段才能通过本机 Nginx 以只读方式打开影像档案袋。请确认可信来源范围没有配置过大。',
        '确认启用开发者模式',
        { type: 'warning', confirmButtonText: '确认启用', cancelButtonText: '取消' },
      )
      return true
    }
    catch {
      return false
    }
  }

  /** 保存前列出本次修改，确认后才写入系统数据库。 */
  async function confirmSaveChanges(): Promise<boolean> {
    try {
      await actionDialog.confirm(
        '以下改动将写入系统数据库，保存后立即对全站生效。',
        '确认保存设置',
        {
          confirmButtonText: '确认保存',
          cancelButtonText: '返回修改',
          details: changedItems.value,
          detailsCaption: `共 ${changedItems.value.length} 项修改`,
        },
      )
      return true
    }
    catch {
      return false
    }
  }

  async function handleSave(options: { confirmChanges?: boolean } = {}): Promise<boolean> {
    // 页头按钮靠 loading 自禁用，但「离开前保存」与页头保存可以同时进入，需要显式互斥。
    if (saving.value) { return false }
    if (!loaded.value) {
      ElMessage.warning('系统设置尚未从服务器读取成功，请重新加载后再保存')
      return false
    }
    if (!validateSettings()) { return false }
    if (options.confirmChanges !== false && isDirty.value && !await confirmSaveChanges()) { return false }
    if (!await confirmDeveloperModeEnable()) { return false }
    saving.value = true
    const developerChangeRequested = developerModeChanged.value
    // 提交内容固定在请求发出前，保存结果只认这份快照。
    const payload = serializeSystemSettings(settings.value)
    try {
      await saveSystemSettings(payload)
      const savedSettings = parseSystemSettings({ ...payload })
      writeLocalSystemSettings(savedSettings)
      window.dispatchEvent(new CustomEvent('mrr:login-page-settings-updated', {
        detail: {
          ...savedSettings,
          systemAdminContactVisible: savedSettings.systemAdminContactEnabled && savedSettings.systemAdminPublicVisible,
        },
      }))
      markAsSaved(payload)
      ElMessage.success(developerChangeRequested
        ? `开发者模式配置已保存，当前状态：${payload.developerModeEnabled === 'true' ? '启用' : '关闭'}`
        : '系统设置已保存')
      return true
    }
    catch (error: any) {
      if (developerChangeRequested) { settings.value.developerModeEnabled = savedDeveloperModeEnabled.value }
      ElMessage.error(error?.response?.data?.message || error?.message || '服务端保存失败，设置未生效')
      return false
    }
    finally {
      saving.value = false
    }
  }

  async function handleReset() {
    if (!loaded.value) { return }
    try {
      await actionDialog.confirm('恢复默认会覆盖当前页面中的设置修改，恢复后仍需点击“保存”才会生效。确定继续吗？', '恢复默认设置', {
        type: 'warning',
        confirmButtonText: '确认恢复',
        cancelButtonText: '取消',
      })
    }
    catch {
      return
    }

    settings.value = createDefaultSystemSettings()
    ElMessage.info('已恢复默认值，保存后生效')
  }

  async function handleReload() {
    if (isDirty.value) {
      try {
        await actionDialog.confirm('重新加载会丢弃当前未保存修改，是否继续？', '重新加载设置', {
          type: 'warning',
          confirmButtonText: '继续加载',
          cancelButtonText: '取消',
        })
      }
      catch {
        return
      }
    }
    await loadSettings(true)
  }

  async function selectSection(section: SettingsSection) {
    activeSection.value = section
    await router.replace({ query: { ...route.query, section } })
  }

  watch(() => route.query.section, (value) => {
    if (isSettingsSection(value)) { activeSection.value = value }
  })

  onMounted(() => {
    if (isSettingsSection(route.query.section)) { activeSection.value = route.query.section }
    void loadSettings()
  })

  return {
    settingsNavItems,
    activeSection,
    activeMeta,
    loading,
    loaded,
    loadError,
    saving,
    settings,
    changedKeys,
    changedItems,
    isDirty,
    isServerSettingSection,
    selectSection,
    handleSave,
    handleReload,
    handleReset,
    discardUnsavedChanges,
  }
}
