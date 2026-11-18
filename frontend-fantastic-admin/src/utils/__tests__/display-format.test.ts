import { describe, expect, it } from 'vitest'
import { isRedactedLogText } from '../display-format'

describe('日志正文占位符识别', () => {
  it('识别后端历史占位符，避免被当成渲染失败', () => {
    expect(isRedactedLogText('[MESSAGE OMITTED]')).toBe(true)
    expect(isRedactedLogText('  [LEGACY MESSAGE OMITTED]  ')).toBe(true)
    expect(isRedactedLogText('[ERROR MESSAGE OMITTED]')).toBe(true)
  })

  it('真实正文、空值与缺失值都不算占位符', () => {
    expect(isRedactedLogText('Tomcat initialized with port 18045 (http)')).toBe(false)
    expect(isRedactedLogText('查询病案失败 bah=[REDACTED]')).toBe(false)
    expect(isRedactedLogText('')).toBe(false)
    expect(isRedactedLogText(null)).toBe(false)
    expect(isRedactedLogText(undefined)).toBe(false)
    expect(isRedactedLogText(123)).toBe(false)
  })
})
