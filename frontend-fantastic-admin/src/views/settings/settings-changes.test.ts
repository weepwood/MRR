import { describe, expect, it } from 'vitest'
import { describeSettingChanges, formatSettingValue } from './settings-changes'

describe('formatSettingValue', () => {
  it('把布尔开关显示为开启或关闭', () => {
    expect(formatSettingValue('archiveWatermarkEnabled', 'true')).toBe('开启')
    expect(formatSettingValue('archiveWatermarkEnabled', 'false')).toBe('关闭')
  })

  it('翻译枚举值并保留数值单位', () => {
    expect(formatSettingValue('imageSource', 'oss')).toBe('OSS 图片')
    expect(formatSettingValue('archiveDefaultView', 'list')).toBe('列表')
    expect(formatSettingValue('archiveThumbnailSize', '240')).toBe('240 px')
    expect(formatSettingValue('archiveWatermarkOpacity', '14')).toBe('14%')
  })

  it('多行输入压成一行，空值返回空串', () => {
    expect(formatSettingValue('developerModeAllowedSources', '127.0.0.1\r\n::1')).toBe('127.0.0.1、::1')
    expect(formatSettingValue('organizationName', '  ')).toBe('')
  })

  it('未知字段回退到原始值', () => {
    expect(formatSettingValue('unknownField', ' raw ')).toBe('raw')
  })
})

describe('describeSettingChanges', () => {
  const saved = {
    systemName: 'MRR',
    systemDescription: '旧简介',
    archiveWatermarkEnabled: 'false',
    archiveThumbnailSize: '200',
  }

  it('只列出真正发生变化的项并按表单顺序排列', () => {
    const current = {
      ...saved,
      systemDescription: '新简介',
      archiveWatermarkEnabled: 'true',
      archiveThumbnailSize: '260',
    }
    const changes = describeSettingChanges(Object.keys(current), current, saved)

    expect(changes.map(item => item.key)).toEqual(['systemDescription', 'archiveThumbnailSize', 'archiveWatermarkEnabled'])
    expect(changes[0]).toMatchObject({ label: '系统简介', before: '旧简介', after: '新简介' })
    expect(changes[1].after).toBe('260 px')
    expect(changes[2]).toMatchObject({ label: '显示访问水印', before: '关闭', after: '开启' })
  })

  it('没有变化时返回空清单，空值保留为空串交给界面占位', () => {
    expect(describeSettingChanges(Object.keys(saved), saved, saved)).toEqual([])

    const changes = describeSettingChanges(['organizationName'], { organizationName: '信息科' }, { organizationName: '' })
    expect(changes).toHaveLength(1)
    expect(changes[0]).toMatchObject({ before: '', after: '信息科' })
  })
})
