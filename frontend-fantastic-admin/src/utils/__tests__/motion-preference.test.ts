import { afterEach, describe, expect, it, vi } from 'vitest'
import { isMotionEnabled } from '../motion-preference'

afterEach(() => {
  vi.unstubAllGlobals()
  document.documentElement.removeAttribute('data-motion')
})

describe('共享动效偏好', () => {
  it('正常偏好允许动效', () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false })))
    expect(isMotionEnabled()).toBe(true)
  })

  it('系统减少动态效果优先于应用开关，且运行时变化立即生效', () => {
    document.documentElement.dataset.motion = 'on'
    const media = { matches: true }
    vi.stubGlobal('matchMedia', vi.fn(() => media))
    expect(isMotionEnabled()).toBe(false)
    media.matches = false
    expect(isMotionEnabled()).toBe(true)
  })

  it('应用关闭动效时始终关闭', () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false })))
    document.documentElement.dataset.motion = 'off'
    expect(isMotionEnabled()).toBe(false)
  })
})
