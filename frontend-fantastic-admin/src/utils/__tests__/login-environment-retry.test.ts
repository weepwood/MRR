import { describe, expect, it } from 'vitest'
import { getEnvironmentProbeRetryDelay } from '../login-environment-retry'

describe('getEnvironmentProbeRetryDelay', () => {
  it('按指数间隔递增并在 60 秒封顶', () => {
    expect([1, 2, 3, 4, 5, 6, 7].map(getEnvironmentProbeRetryDelay)).toEqual([
      2_000,
      4_000,
      8_000,
      16_000,
      32_000,
      60_000,
      60_000,
    ])
  })

  it('无效或首次失败计数使用最短重试间隔', () => {
    expect(getEnvironmentProbeRetryDelay(0)).toBe(2_000)
    expect(getEnvironmentProbeRetryDelay(-1)).toBe(2_000)
  })
})
