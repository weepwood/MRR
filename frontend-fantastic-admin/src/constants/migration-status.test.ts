import { describe, expect, it } from 'vitest'
import { migrationStatusMeta, migrationStatusTag } from './migration-status'

describe('共享迁移状态展示', () => {
  it('已知扫描状态使用中文且保留统一色义', () => {
    expect(migrationStatusTag('waiting_sjh')).toEqual({ label: '等待上架号', tone: 'neutral' })
    expect(migrationStatusTag('retry_wait')).toEqual({ label: '等待重试', tone: 'warning' })
    expect(migrationStatusTag('failed')).toEqual({ label: '失败', tone: 'danger' })
    expect(migrationStatusTag('verified')).toEqual({ label: '已验证', tone: 'success' })
    expect(migrationStatusMeta('waiting_sjh').label).toBe(migrationStatusTag('waiting_sjh').label)
  })
  it('未知状态不丢失原始内容，不误认继承属性', () => {
    expect(migrationStatusTag('future_very_long_code').label).toBe('future_very_long_code')
    expect(migrationStatusTag('constructor').label).toBe('constructor')
    expect(migrationStatusTag(undefined).label).toBe('-')
  })
})
