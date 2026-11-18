import type { SettingsSection } from './composables/useUnifiedSettings'
import { settingsNavItems } from './composables/useUnifiedSettings'

interface SettingGroup {
  title: string
  keywords: string
  anchor: string
}

export interface SettingsSearchResult {
  title: string
  section: SettingsSection
  category?: string
  anchor?: string
}

// Index UI labels and synonyms only. Use visible group headings for optional or collapsed controls.
const settingGroups: Partial<Record<SettingsSection, SettingGroup[]>> = {
  'system': [
    { title: '系统标识', keywords: '系统名称 系统简称 系统英文名称 所属医院或机构 系统简介', anchor: 'identity' },
  ],
  'login-support': [
    { title: '登录页展示', keywords: '登录文案 环境标签 页脚说明 登录区说明 管理员提示 功能介绍', anchor: 'login-copy' },
    { title: '系统管理员与技术支持', keywords: '联系方式 联系电话 分机号 联系邮箱 服务时间 登录页公开显示', anchor: 'support-contact' },
  ],
  'documentation': [
    { title: '帮助中心文档入口', keywords: '用户使用手册 开发文档 运维指南 文档链接', anchor: 'help-links' },
  ],
  'archive': [
    { title: '默认图片来源', keywords: 'OSS 本地图片 影像来源', anchor: 'image-source' },
    { title: '默认浏览方式', keywords: '影像列表 预览模式 缩略图宽度 首批渲染数量 单页 连续滚动', anchor: 'archive-view' },
    { title: '目录路由规则', keywords: '影像目录 目录匹配 精确目录 年月目录 通用节点', anchor: 'directory-routing' },
    { title: '自动适应预览区域', keywords: '原始尺寸 图片自适应', anchor: 'archive-auto-fit' },
    { title: '记住选择状态', keywords: '恢复已选影像页', anchor: 'archive-selection' },
  ],
  'ocr': [
    { title: '启用本地 OCR', keywords: 'OCR 开关 新扫描自动 OCR 自动归类', anchor: 'ocr-switches' },
    { title: 'OCR 引擎与资源限制', keywords: '白名单配置名称 识别语言 最大并发 单页超时 最大输出 OCR 低质量阈值 批量确认最低阈值', anchor: 'ocr-engine' },
    { title: 'OCR 字典与分类规则', keywords: '识别词库 词典 别名 智能分类', anchor: 'ocr-rules' },
  ],
  'security': [
    { title: '显示访问水印', keywords: '水印开关', anchor: 'watermark' },
    { title: '水印透明度', keywords: '水印浓度', anchor: 'watermark-opacity' },
    { title: '允许显示完整身份证号', keywords: '身份证号脱敏', anchor: 'id-card-display' },
    { title: '允许复制身份证号', keywords: '身份证号复制', anchor: 'id-card-copy' },
    { title: '每日允许 IP 切换次数', keywords: '访问限制 IP 限制', anchor: 'archive-ip-limit' },
  ],
  'developer': [
    { title: '兼容旧版影像档案袋调用', keywords: '旧接口 匿名只读', anchor: 'legacy-archive' },
    { title: '登录后跳过 API 业务权限', keywords: '权限旁路', anchor: 'developer-api' },
    { title: '允许访问的客户端 IP / 网段', keywords: '白名单 可信来源 CIDR', anchor: 'trusted-sources' },
  ],
  'external-links': [
    { title: '添加外部网站', keywords: '外链 添加网址', anchor: 'add-external-link' },
  ],
  'appearance': [
    { title: '主题与页面样式', keywords: '主题主色 颜色主题 暗黑 深色模式 圆角系数 页面标题风格 动态效果', anchor: 'theme' },
    { title: '导航与顶栏', keywords: '导航栏模式 主导航点击模式 次导航折叠 折叠按钮 导航快捷键 顶栏定位', anchor: 'navigation' },
    { title: '工作区组件', keywords: '标签栏 工具栏 面包屑 导航搜索 全屏 页面刷新 主题切换 快捷键', anchor: 'workspace' },
    { title: '主页、版权与其他', keywords: '主页名称 底部版权 备案号 权限控制 载入进度条 色弱模式 动态页面标题', anchor: 'other' },
  ],
}

function normalize(value: string) {
  return value.normalize('NFKC').trim().toLowerCase()
}

const entries = settingsNavItems.flatMap((item) => {
  const groups = (settingGroups[item.key] ?? []).map(group => ({
    result: { title: group.title, section: item.key, category: item.title, anchor: group.anchor },
    title: normalize(group.title),
    keywords: normalize(group.keywords),
  }))
  const keywords = item.key === 'runtime'
    ? `${item.description} 登录失败次数 登录锁定 注册请求 限流窗口 会话有效期 访问日志保留 自动清理`
    : item.description
  return [
    { result: { title: item.title, section: item.key, category: '' }, title: normalize(item.title), keywords: normalize(keywords) },
    ...groups,
  ]
})

export function searchSettings(value: string): SettingsSearchResult[] {
  const query = normalize(value)
  if (!query) { return [] }

  const terms = query.split(/\s+/)
  return entries
    .flatMap(({ result, title, keywords }) => {
      if (title === query) { return [{ result, rank: 0 }] }
      if (title.includes(query)) { return [{ result, rank: 1 }] }
      if (keywords.includes(query)) { return [{ result, rank: 2 }] }
      const text = `${title} ${keywords} ${normalize(result.category)}`
      return terms.length > 1 && terms.every(term => text.includes(term)) ? [{ result, rank: 3 }] : []
    })
    .sort((first, second) => first.rank - second.rank)
    .map(item => item.result)
}
