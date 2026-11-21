import { beforeEach, describe, expect, it } from 'vitest'
import { readDemoLoggedOut, writeDemoLoggedOut } from '../auth-storage'

describe('演示模式已退出登录标记', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
  })

  it('默认未标记（访客首次访问仍会被自动注入演示会话）', () => {
    expect(readDemoLoggedOut()).toBe(false)
  })

  it('写入后可读到已退出状态', () => {
    writeDemoLoggedOut(true)
    expect(readDemoLoggedOut()).toBe(true)
    expect(window.sessionStorage.getItem('mrr:demo:logged-out')).toBe('1')
  })

  it('显式登录清除标记', () => {
    writeDemoLoggedOut(true)
    writeDemoLoggedOut(false)
    expect(readDemoLoggedOut()).toBe(false)
    expect(window.sessionStorage.getItem('mrr:demo:logged-out')).toBeNull()
  })

  it('重复写入幂等', () => {
    writeDemoLoggedOut(true)
    writeDemoLoggedOut(true)
    expect(readDemoLoggedOut()).toBe(true)
  })
})
