import type { SettingsSection } from './composables/useUnifiedSettings'

export interface SettingsChange {
  key: string
  label: string
  section: SettingsSection
  before: string
  after: string
}

interface SettingFieldMeta {
  label: string
  section: SettingsSection
  /** 布尔开关统一显示为「开启 / 关闭」，与页面上的开关语义一致。 */
  type?: 'boolean'
  /** 枚举值的中文显示，未命中时回退到原始值。 */
  options?: Record<string, string>
  /** 数值型字段的单位后缀，直接拼在数值后面，例如 ` px`、`%`。 */
  unitSuffix?: string
}

// 字段的中文显示名与所属分类来自设置表单本身，保证保存确认与表单标签一致。
const settingFields: Record<string, SettingFieldMeta> = {
  systemName: { label: '系统名称', section: 'system' },
  systemShortName: { label: '系统简称', section: 'system' },
  systemEnglishName: { label: '系统英文名称', section: 'system' },
  organizationName: { label: '所属医院或机构', section: 'system' },
  systemDescription: { label: '系统简介', section: 'system' },

  loginEnvironmentLabel: { label: '环境标签', section: 'login-support' },
  loginFooterText: { label: '页脚说明', section: 'login-support' },
  loginFormDescription: { label: '登录区说明', section: 'login-support' },
  loginHelpText: { label: '管理员提示', section: 'login-support' },
  loginFeatureEnabled: { label: '显示功能介绍', section: 'login-support', type: 'boolean' },
  loginFeature1Title: { label: '功能 1 标题', section: 'login-support' },
  loginFeature1Description: { label: '功能 1 说明', section: 'login-support' },
  loginFeature2Title: { label: '功能 2 标题', section: 'login-support' },
  loginFeature2Description: { label: '功能 2 说明', section: 'login-support' },
  loginFeature3Title: { label: '功能 3 标题', section: 'login-support' },
  loginFeature3Description: { label: '功能 3 说明', section: 'login-support' },
  systemAdminContactEnabled: { label: '启用管理员联系信息', section: 'login-support', type: 'boolean' },
  systemAdminPublicVisible: { label: '在登录页公开显示联系方式', section: 'login-support', type: 'boolean' },
  systemAdminDisplayName: { label: '联系人或支持团队', section: 'login-support' },
  systemAdminDepartment: { label: '所属部门', section: 'login-support' },
  systemAdminPhone: { label: '联系电话', section: 'login-support' },
  systemAdminExtension: { label: '分机号', section: 'login-support' },
  systemAdminEmail: { label: '联系邮箱', section: 'login-support' },
  systemAdminServiceHours: { label: '服务时间', section: 'login-support' },
  systemAdminDescription: { label: '补充说明', section: 'login-support' },

  documentationUserGuideUrl: { label: '用户使用手册', section: 'documentation' },
  documentationDeveloperUrl: { label: '开发文档', section: 'documentation' },
  documentationOperationsUrl: { label: '运维指南', section: 'documentation' },

  imageSource: { label: '默认图片来源', section: 'archive', options: { local: '本地图片', oss: 'OSS 图片' } },
  archiveDefaultView: { label: '影像列表', section: 'archive', options: { thumb: '缩略图', list: '列表' } },
  archivePreviewMode: { label: '预览模式', section: 'archive', options: { single: '单页', scroll: '连续滚动' } },
  archiveThumbnailSize: { label: '缩略图宽度', section: 'archive', unitSuffix: ' px' },
  archivePreloadCount: { label: '首批渲染数量', section: 'archive', unitSuffix: ' 张' },
  archiveAutoFit: { label: '自动适应预览区域', section: 'archive', type: 'boolean' },
  archiveRememberSelection: { label: '记住选择状态', section: 'archive', type: 'boolean' },

  archiveWatermarkEnabled: { label: '显示访问水印', section: 'security', type: 'boolean' },
  archiveWatermarkOpacity: { label: '水印透明度', section: 'security', unitSuffix: '%' },
  patientIdCardRevealEnabled: { label: '允许显示完整身份证号', section: 'security', type: 'boolean' },
  patientIdCardCopyEnabled: { label: '允许复制身份证号', section: 'security', type: 'boolean' },
  archiveIpMaxChanges: { label: '每日允许 IP 切换次数', section: 'security', unitSuffix: ' 次' },

  developerModeEnabled: { label: '兼容旧版影像档案袋调用', section: 'developer', type: 'boolean' },
  developerModeApiAccessEnabled: { label: '登录后跳过 API 业务权限', section: 'developer', type: 'boolean' },
  developerModeAllowedSources: { label: '允许访问的客户端 IP / 网段', section: 'developer' },
}

// 声明顺序即清单顺序，与左侧分类、页面分组从上到下保持一致。
const fieldOrder = new Map(Object.keys(settingFields).map((key, index) => [key, index]))

/** 多行输入（如可信来源）在确认清单里压成一行，避免撑破弹窗高度。 */
function normalizeText(value: string): string {
  return value
    .split(/\r?\n/)
    .map(part => part.trim())
    .filter(Boolean)
    .join('、')
}

export function formatSettingValue(key: string, value: string): string {
  const meta = settingFields[key]
  if (!meta) { return normalizeText(value) }
  if (meta.type === 'boolean') { return value === 'true' ? '开启' : '关闭' }

  const text = normalizeText(value)
  if (meta.options) { return (text && meta.options[text]) || text }
  if (!text) { return '' }
  return meta.unitSuffix ? `${text}${meta.unitSuffix}` : text
}

/**
 * 把「哪些键被改过」翻译成可直接展示的清单。
 * 只返回值确实发生变化的项，并按左侧分类顺序排列，与用户在页面上的浏览顺序一致。
 */
export function describeSettingChanges(
  keys: string[],
  current: Record<string, string>,
  saved: Record<string, string>,
): SettingsChange[] {
  return keys
    .filter(key => current[key] !== saved[key])
    .map((key) => {
      const meta = settingFields[key]
      return {
        key,
        label: meta?.label ?? key,
        section: meta?.section ?? 'system',
        before: formatSettingValue(key, saved[key] ?? ''),
        after: formatSettingValue(key, current[key] ?? ''),
      }
    })
    .sort((first, second) => (fieldOrder.get(first.key) ?? Number.MAX_SAFE_INTEGER) - (fieldOrder.get(second.key) ?? Number.MAX_SAFE_INTEGER))
}
