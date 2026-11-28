import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
function sources(dir: string): string[] {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const file = path.join(dir, entry.name)
    if (entry.isDirectory()) { return entry.name === '__tests__' ? [] : sources(file) }
    return /\.(?:vue|ts|tsx|css|scss)$/.test(entry.name) && !/\.test\.[jt]sx?$/.test(entry.name) ? [file] : []
  })
}

describe('唯一基础控件归属', () => {
  it('无调用的历史辅助组件不再恢复', () => {
    for (const component of ['FaAuth', 'FaFixedActionBar', 'FaPageHeader', 'FaPasswordStrength', 'FaSearchBar']) {
      expect(fs.existsSync(path.join(root, 'ui/components', component))).toBe(false)
    }
    const legacy = sources(root).filter(file => /\bFa(?:Auth|FixedActionBar|PageHeader|PasswordStrength|SearchBar)\b/.test(fs.readFileSync(file, 'utf8')))
    expect(legacy.map(file => path.relative(root, file))).toEqual([])
  })

  it('无业务调用的旧通知与上传预览链不恢复', () => {
    for (const component of ['FaNotification', 'FaFileUpload', 'FaImageUpload', 'FaImagePreview']) {
      expect(fs.existsSync(path.join(root, 'ui/components', component))).toBe(false)
    }
    const legacy = sources(root).filter(file => /\b(?:Fa(?:Notification|FileUpload|ImageUpload|ImagePreview)|useFa(?:Notification|ImagePreview)|faNotification)\b/.test(fs.readFileSync(file, 'utf8')))
    expect(legacy.map(file => path.relative(root, file))).toEqual([])
  })

  it('头像、进度、分隔线及无调用卡片和折叠不恢复旧实现', () => {
    for (const component of ['FaAvatar', 'FaProgress', 'FaDivider', 'FaCard', 'FaCollapsible']) {
      expect(fs.existsSync(path.join(root, 'ui/components', component))).toBe(false)
    }
    const legacy = sources(root).filter(file => /\bFa(?:Avatar|Progress|Divider|Card|Collapsible)\b/.test(fs.readFileSync(file, 'utf8')))
    expect(legacy.map(file => path.relative(root, file))).toEqual([])
  })

  it('账户与标签页菜单不恢复旧实现', () => {
    for (const component of ['FaDropdown', 'FaContextMenu']) {
      expect(fs.existsSync(path.join(root, 'ui/components', component))).toBe(false)
    }
    const legacy = sources(root).filter(file => /\bFa(?:Dropdown|ContextMenu)\b/.test(fs.readFileSync(file, 'utf8')))
    expect(legacy.map(file => path.relative(root, file))).toEqual([])
    expect(fs.readFileSync(path.join(root, 'components/AccountButton/index.vue'), 'utf8')).toContain('<el-dropdown')
    expect(fs.readFileSync(path.join(root, 'layouts/components/Topbar/Tabbar/index.vue'), 'utf8')).toContain('virtual-triggering')
  })

  it('对话框和抽屉使用唯一底层实现', () => {
    for (const component of ['FaModal', 'FaDrawer']) {
      expect(fs.existsSync(path.join(root, 'ui/components', component))).toBe(false)
    }
    const legacy = sources(root).filter(file => /\bFa(?:Modal|Drawer)\b/.test(fs.readFileSync(file, 'utf8')))
    expect(legacy.map(file => path.relative(root, file))).toEqual([])
    expect(fs.readFileSync(path.join(root, 'components/MrrDialog/index.vue'), 'utf8')).toContain('<el-dialog')
    expect(fs.readFileSync(path.join(root, 'components/MrrDrawer/index.vue'), 'utf8')).toContain('<el-drawer')
  })

  it('提示与轻量气泡不恢复旧浮层实现', () => {
    for (const component of ['FaTooltip', 'FaPopover']) {
      expect(fs.existsSync(path.join(root, 'ui/components', component))).toBe(false)
    }
    const legacy = sources(root).filter(file => /\bFa(?:Tooltip|Popover)\b/.test(fs.readFileSync(file, 'utf8')))
    expect(legacy.map(file => path.relative(root, file))).toEqual([])
    const policy = fs.readFileSync(path.join(root, 'components/MrrTooltip/index.vue'), 'utf8')
    expect(policy).toContain('<el-tooltip')
    expect(policy).not.toMatch(/reka-ui/)
  })

  it('所有按钮与按钮组已迁移，不恢复旧实现或样式', () => {
    for (const component of ['FaButton', 'FaButtonGroup']) {
      expect(fs.existsSync(path.join(root, 'ui/components', component))).toBe(false)
    }
    const legacy = sources(root).filter(file => /\bFaButton(?:Group)?\b|\.fa-button-(?:control|group)\b/.test(fs.readFileSync(file, 'utf8')))
    expect(legacy.map(file => path.relative(root, file))).toEqual([])
  })

  it('输入和文本域已迁移，不恢复旧实现', () => {
    for (const component of ['FaInput', 'FaTextarea']) {
      expect(fs.existsSync(path.join(root, 'ui/components', component))).toBe(false)
    }
    const legacy = sources(root).filter(file => /\bFa(?:Input|Textarea)\b|\.fa-(?:input|textarea)-control\b/.test(fs.readFileSync(file, 'utf8')))
    expect(legacy.map(file => path.relative(root, file))).toEqual([])
  })

  it('已迁移的全局按钮调用不得恢复 FaButton', () => {
    const migrated = [
      'components/AccountButton/index.vue',
      'layouts/components/AppSetting/index.vue',
      'layouts/components/Logo/index.vue',
      'layouts/components/SubSidebar/index.vue',
      'layouts/components/Topbar/Toolbar/ColorScheme/index.vue',
      'layouts/components/Topbar/Toolbar/Fullscreen/index.vue',
      'layouts/components/Topbar/Toolbar/NavSearch/index.vue',
      'layouts/components/Topbar/Toolbar/PageReload/index.vue',
      'layouts/components/Topbar/Toolbar/leftSide.vue',
    ]
    for (const file of migrated) {
      const source = fs.readFileSync(path.join(root, file), 'utf8')
      expect(source, file).not.toMatch(/\bFaButton\b/)
      expect(source, file).toContain('<el-button')
    }
  })

  it('页签已迁移，不恢复旧实现或直接导入', () => {
    expect(fs.existsSync(path.join(root, 'ui/components/FaTabs'))).toBe(false)
    const legacy = sources(root).filter(file => /\bFaTabs\b/.test(fs.readFileSync(file, 'utf8')))
    expect(legacy.map(file => path.relative(root, file))).toEqual([])
  })

  it('选择器及滑块已迁移，不恢复旧实现或旧样式锚点', () => {
    for (const component of ['FaSelect', 'FaSlider']) {
      expect(fs.existsSync(path.join(root, 'ui/components', component))).toBe(false)
    }
    const legacy = sources(root).filter(file => /\bFa(?:Select|Slider)\b|\.fa-(?:select|slider)\b/.test(fs.readFileSync(file, 'utf8')))
    expect(legacy.map(file => path.relative(root, file))).toEqual([])
  })

  it('复选框已全量迁移，不恢复旧实现或直接导入', () => {
    expect(fs.existsSync(path.join(root, 'ui/components/FaCheckbox'))).toBe(false)
    const legacy = sources(root).filter(file => /\bFaCheckbox\b|\.fa-checkbox\b/.test(fs.readFileSync(file, 'utf8')))
    expect(legacy.map(file => path.relative(root, file))).toEqual([])
  })

  it('开关已经迁移到 Element Plus，不能恢复旧实现或旧样式锚点', () => {
    expect(fs.existsSync(path.join(root, 'ui/components/FaSwitch'))).toBe(false)
    const legacy = sources(root).filter(file => /\bFaSwitch\b|\.fa-switch\b/.test(fs.readFileSync(file, 'utf8')))
    expect(legacy.map(file => path.relative(root, file))).toEqual([])
  })
  it('已迁移的业务字段不恢复重复原生实现', () => {
    const fields = [
      ['views/data-relations/ArchiveManifestCard.vue', /<select\b/, '<el-select'],
      ['views/statistics/archive/components/OcrPanel.vue', /<textarea\b/, '<el-input'],
      ['views/statistics/archive/components/ArchiveQueryFailure.vue', /<input\b/, '<el-input'],
      ['layouts/components/Topbar/Toolbar/NavSearch/search.vue', /<input\b/, '<el-input'],
    ] as const
    for (const [file, legacy, control] of fields) {
      const source = fs.readFileSync(path.join(root, file), 'utf8')
      expect(source, file).not.toMatch(legacy)
      expect(source, file).toContain(control)
    }
  })

  it('业务图标尺寸与设置滑块使用共享归属', () => {
    const read = (file: string) => fs.readFileSync(path.join(root, file), 'utf8')
    expect(read('assets/styles/tokens.css')).toContain('--mrr-control-icon-size: 16px')
    expect(read('assets/styles/tokens.css')).toContain('--mrr-button-icon-gap: 6px')
    expect(read('assets/styles/element-plus/foundation-buttons.css')).toContain('var(--mrr-control-icon-size)')
    expect(read('ui/components/FaIcon/index.vue')).toContain('mrr-icon')
    expect(read('assets/styles/components/form-controls.css')).toContain('.mrr-settings-slider-control')
    for (const file of ['ArchiveSettings.vue', 'SecuritySettings.vue']) {
      const source = read(`views/settings/components/${file}`)
      expect(source).toContain('mrr-settings-slider-control')
      expect(source).not.toMatch(/\.slider-control\s*:deep/)
    }
  })
  it('业务页头只允许公共实现和唯一尺寸', () => {
    const css = fs.readFileSync(path.join(root, 'assets/styles/components/page-header.css'), 'utf8')
    expect(css).not.toContain('mrr-page-header--compact')
    expect(css).not.toContain('--mrr-summary-min-height')
    for (const file of ['views/settings/components/UnifiedSettingsPage.vue', 'views/help/index.vue']) {
      const source = fs.readFileSync(path.join(root, file), 'utf8')
      expect(source).toContain('<MrrPageShell')
      expect(source).toContain('<MrrPageHeader')
      expect(source).not.toMatch(/<header class="(?:page-header|help-header)"/)
    }
  })
})
