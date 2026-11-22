import { afterEach, describe, expect, it, vi } from 'vitest'
import { COPY_FAILURE_MESSAGE, copyText } from '@/utils/copy-text'

const message = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: message }))

function mockClipboard(writeText?: (text: string) => Promise<void>) {
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: writeText ? { writeText } : undefined,
  })
}

describe('copyText', () => {
  afterEach(() => {
    mockClipboard(undefined)
    message.success.mockReset()
    message.error.mockReset()
  })

  it('写入剪贴板并提示成功', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    mockClipboard(writeText)
    await expect(copyText('ERR-001', { success: '错误编号已复制' })).resolves.toBe(true)
    expect(writeText).toHaveBeenCalledWith('ERR-001')
    expect(message.success).toHaveBeenCalledWith('错误编号已复制')
  })

  it('空白文本不写入也不提示', async () => {
    const writeText = vi.fn()
    mockClipboard(writeText)
    await expect(copyText('   ')).resolves.toBe(false)
    await expect(copyText(null)).resolves.toBe(false)
    expect(writeText).not.toHaveBeenCalled()
    expect(message.success).not.toHaveBeenCalled()
    expect(message.error).not.toHaveBeenCalled()
  })

  it('写入失败或缺少 Clipboard API 时给出统一指引', async () => {
    mockClipboard(vi.fn().mockRejectedValue(new Error('denied')))
    await expect(copyText('abc')).resolves.toBe(false)
    mockClipboard(undefined)
    await expect(copyText('abc')).resolves.toBe(false)
    expect(message.error).toHaveBeenCalledTimes(2)
    expect(message.error).toHaveBeenCalledWith(COPY_FAILURE_MESSAGE)
  })

  it('success 为 false 时不提示成功', async () => {
    mockClipboard(vi.fn().mockResolvedValue(undefined))
    await copyText('abc', { success: false })
    expect(message.success).not.toHaveBeenCalled()
  })
})
