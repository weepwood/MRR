import { beforeEach, describe, expect, it, vi } from 'vitest'
import { notify } from '../notification'

const message = vi.hoisted(() => vi.fn())
vi.mock('element-plus', () => ({ ElMessage: message }))

describe('公共通知入口', () => {
  beforeEach(() => message.mockReset())

  it.each(['success', 'warning', 'info', 'error'] as const)('保持 %s 类型及统一默认策略', (type) => {
    notify[type]('示例消息')
    expect(message).toHaveBeenCalledExactlyOnceWith({ message: '示例消息', type, duration: 5000, grouping: true, showClose: false, offset: undefined })
  })

  it('尊重显式常驻、关闭按钮、重复分组及位置策略', () => {
    notify.error('失败', { description: '  请重试  ', duration: 0, grouping: false, showClose: true, offset: 32 })
    expect(message).toHaveBeenCalledExactlyOnceWith({ message: '失败：请重试', type: 'error', duration: 0, grouping: false, showClose: true, offset: 32 })
  })

  it('空说明不添加多余标点', () => {
    notify.info('完成', { description: ' ' })
    expect(message).toHaveBeenCalledWith(expect.objectContaining({ message: '完成' }))
  })
})
