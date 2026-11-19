import { describe, expect, it, vi } from 'vitest'
import { describeBrowseError } from '../modules/database-browse'

// database-browse.ts 会经 api/index.ts 间接加载 user store，进而拖入 router 的虚拟模块；
// 与 src/api/__tests__/session-bound-request.test.ts 一致，这里只保留最小替身。
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({ isLogin: false, isSessionVerified: false, token: '', requestLogout: vi.fn() }),
}))

/** 模拟请求层在 HTTP 非 2xx 时原样抛出的 AxiosError（业务码只在 response.data）。 */
function httpError(status: number, body: unknown) {
  const error = new Error(`Request failed with status code ${status}`)
  return Object.assign(error, {
    isAxiosError: true,
    code: 'ERR_BAD_REQUEST',
    response: { status, data: body },
  })
}

describe('describeBrowseError', () => {
  it('功能关闭（3007）时提示未开启，而不是 Axios 原生英文报错', () => {
    const view = describeBrowseError(httpError(404, { code: 3007, message: '资源不存在' }))
    expect(view.message).toBe('该功能未开启，请联系管理员')
    expect(view.featureDisabled).toBe(true)
  })

  it('字符串 code 不会被误判成业务码', () => {
    const view = describeBrowseError(httpError(500, {}))
    expect(view.featureDisabled).toBe(false)
    expect(view.message).toBe('服务器内部异常，请稍后重试')
  })

  it('业务错误对象直接携带数字 code 时给出可执行提示', () => {
    const view = describeBrowseError({ code: 3003 })
    expect(view.message).toBe('该表必须提供过滤条件后才会返回数据')
    expect(view.featureDisabled).toBe(false)
  })

  it('未登记表 3001 沿用响应体中文文案，不误报功能关闭', () => {
    const view = describeBrowseError(httpError(404, { code: 3001, message: '资源不存在' }))
    expect(view.message).toBe('资源不存在')
    expect(view.featureDisabled).toBe(false)
  })

  it('普通错误回落到项目统一的中文提示', () => {
    const view = describeBrowseError(new Error('boom'))
    expect(view.message).toBe('请求失败，请稍后重试')
    expect(view.featureDisabled).toBe(false)
  })
})
