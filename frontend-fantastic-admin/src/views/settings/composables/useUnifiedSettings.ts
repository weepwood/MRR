import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { saveSystemSettings } from '@/api/modules/settings'
import {
  createDefaultSystemSettings,
  loadEffectiveSystemSettings,
  serializeSystemSettings,
  type EffectiveSystemSettings,
  type SettingsSource,
  writeLocalSystemSettings,
} from '@/utils/system-settings'

export type SettingsSection = 'system' | 'login-support' | 'archive' | 'security' | 'developer' | 'department' | 'external-links' | 'appearance'

export const settingsNavItems = [
  { key: 'system', title: '系统信息', description: '名称、机构与系统简介', icon: 'i-ri:information-line' },
  { key: 'login-support', title: '登录与支持', description: '登录展示与管理员联系', icon: 'i-ri:customer-service-2-line' },
  { key: 'archive', title: '档案浏览', description: '图片来源与加载策略', icon: 'i-ri:image-2-line' },
  { key: 'security', title: '访问安全', description: '水印、身份证与 IP 限制', icon: 'i-ri:shield-check-line' },
  { key: 'developer', title: '开发者模式', description: '接口兼容与跨域调试', icon: 'i-ri:code-box-line' },
  { key: 'department', title: '科室配色', description: '档案袋颜色规则', icon: 'i-ri:palette-line' },
  { key: 'external-links', title: '外部链接', description: '维护“其他”导航入口', icon: 'i-ri:links-line' },
  { key: 'appearance', title: '界面外观', description: '主题、导航与页面样式', icon: 'i-ri:layout-4-line' },
] as const

export function useUnifiedSettings() {
  const route = useRoute()
  const router = useRouter()
  const shellRef = ref<HTMLElement>()
  const activeSection = ref<SettingsSection>('system')
  const loading = ref(false)
  const saving = ref(false)
  const settingsSource = ref<SettingsSource>('default')
  const lastSyncedAt = ref('')
  const savedSnapshot = ref<Record<string, string>>({})
  const settings = ref<EffectiveSystemSettings>(createDefaultSystemSettings())

  const activeMeta = computed(() => settingsNavItems.find(item => item.key === activeSection.value)!)
  const isServerSettingSection = computed(() => ['system', 'login-support', 'archive', 'security', 'developer'].includes(activeSection.value))
  const sourceMeta = computed(() => ({
    server: { label: '服务器配置', type: 'success' as const },
    local: { label: '本地缓存', type: 'warning' as const },
    default: { label: '默认配置', type: 'info' as const },
  })[settingsSource.value])
  const currentSnapshot = computed(() => serializeSystemSettings(settings.value))
  const changedKeys = computed(() => Object.keys(currentSnapshot.value).filter(key => currentSnapshot.value[key] !== savedSnapshot.value[key]))
  const isDirty = computed(() => changedKeys.value.length > 0)
  const developerModeChanged = computed(() => changedKeys.value.includes('developerModeEnabled'))
  const savedDeveloperModeEnabled = computed(() => savedSnapshot.value.developerModeEnabled === 'true')

  function isSettingsSection(value: unknown): value is SettingsSection {
    return settingsNavItems.some(item => item.key === value)
  }

  function markAsSaved() {
    savedSnapshot.value = { ...currentSnapshot.value }
  }

  function updateSyncTime() {
    lastSyncedAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
  }

  async function loadSettings(showMessage = false) {
    loading.value = true
    try {
      const result = await loadEffectiveSystemSettings()
      settings.value = result.settings
      settingsSource.value = result.source
      markAsSaved()
      updateSyncTime()
      if (showMessage) ElMessage.success('设置已重新加载')
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
    if (value.systemAdminEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.systemAdminEmail)) {
      ElMessage.warning('系统管理员邮箱格式不正确')
      return false
    }
    if (value.systemAdminContactEnabled && value.systemAdminPublicVisible && !value.systemAdminPhone && !value.systemAdminEmail) {
      ElMessage.warning('公开管理员信息时至少填写联系电话或联系邮箱')
      return false
    }
    if (!Number.isInteger(value.archiveIpMaxChanges) || value.archiveIpMaxChanges < 0 || value.archiveIpMaxChanges > 20) {
      ElMessage.warning('每日 IP 切换次数必须是 0 到 20 之间的整数')
      return false
    }
    return true
  }

  async function confirmDeveloperModeEnable() {
    if (!developerModeChanged.value || !settings.value.developerModeEnabled || savedDeveloperModeEnabled.value) return true
    try {
      await ElMessageBox.confirm(
        '启用后，无有效 JWT 的受保护 API 将以虚拟管理员身份执行，同时允许任意浏览器 Origin 跨域访问。仅限隔离的开发或联调环境使用。',
        '确认启用开发者模式',
        { type: 'error', confirmButtonText: '确认启用', cancelButtonText: '取消' },
      )
      return true
    }
    catch {
      return false
    }
  }

  async function handleSave() {
    if (!validateSettings() || !await confirmDeveloperModeEnable()) return
    saving.value = true
    const developerChangeRequested = developerModeChanged.value
    try {
      await saveSystemSettings(serializeSystemSettings(settings.value))
      settingsSource.value = 'server'
      writeLocalSystemSettings(settings.value)
      window.dispatchEvent(new CustomEvent('mrr:login-page-settings-updated', {
        detail: {
          ...settings.value,
          systemAdminContactVisible: settings.value.systemAdminContactEnabled && settings.value.systemAdminPublicVisible,
        },
      }))
      markAsSaved()
      updateSyncTime()
      ElMessage.success(developerChangeRequested
        ? `开发者模式已${settings.value.developerModeEnabled ? '启用' : '关闭'}，后端已即时生效`
        : '系统设置已保存')
    }
    catch (error: any) {
      if (developerChangeRequested) settings.value.developerModeEnabled = savedDeveloperModeEnabled.value
      ElMessage.error(error?.response?.data?.message || error?.message || '服务端保存失败，设置未生效')
    }
    finally {
      saving.value = false
    }
  }

  function handleReset() {
    settings.value = createDefaultSystemSettings()
    ElMessage.info('已恢复默认值，保存后生效')
  }

  async function handleReload() {
    if (isDirty.value) {
      try {
        await ElMessageBox.confirm('重新加载会丢弃当前未保存修改，是否继续？', '重新加载设置', {
          type: 'warning', confirmButtonText: '继续加载', cancelButtonText: '取消',
        })
      }
      catch {
        return
      }
    }
    await loadSettings(true)
  }

  function focusSettingsWorkspace() {
    if (window.innerWidth < 960) return
    shellRef.value?.scrollIntoView({ block: 'start', behavior: 'auto' })
  }

  async function selectSection(section: SettingsSection) {
    activeSection.value = section
    await router.replace({ query: { ...route.query, section } })
    await nextTick()
    focusSettingsWorkspace()
  }

  watch(() => route.query.section, (value) => {
    if (isSettingsSection(value)) activeSection.value = value
  })

  onMounted(() => {
    if (isSettingsSection(route.query.section)) activeSection.value = route.query.section
    void loadSettings()
  })

  return {
    settingsNavItems,
    shellRef,
    activeSection,
    activeMeta,
    loading,
    saving,
    settings,
    sourceMeta,
    lastSyncedAt,
    changedKeys,
    isDirty,
    isServerSettingSection,
    selectSection,
    handleSave,
    handleReload,
    handleReset,
  }
}
