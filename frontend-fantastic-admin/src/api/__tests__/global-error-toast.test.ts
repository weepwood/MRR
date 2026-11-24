import { AxiosError, AxiosHeaders } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import api from '../index'

const { user, elMessageError } = vi.hoisted(() => ({
  user: { isLogin: true, isSessionVerified: true, token: 'synthetic-a', requestLogout: vi.fn() },
  elMessageError: vi.fn(),
}))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => user }))
vi.mock('element-plus', () => ({ ElMessage: { error: elMessageError } }))

function dispatchUnhandledRejection(reason: unknown) {
  const event = new Event('unhandledrejection') as PromiseRejectionEvent
  Object.defineProperty(event, 'reason', { value: reason })
  window.dispatchEvent(event)
}

describe('global request error toast', () => {
  it('guides the operator to pass the request id to the administrator', async () => {
    let captured: unknown
    const adapter = vi.fn((config) => {
      throw new AxiosError('synthetic failure', 'ERR_BAD_REQUEST', config, {}, {
        config,
        status: 400,
        statusText: 'Bad Request',
        headers: new AxiosHeaders({ 'x-request-id': 'synthetic-abc123' }),
        data: { code: 400, message: '请求参数有误' },
      })
    })

    void api.get('/api/v1/synthetic', { skipResponseMetrics: true, adapter }).catch((error: unknown) => {
      captured = error
    })
    await vi.waitFor(() => expect(captured).toBeDefined())

    dispatchUnhandledRejection(captured)

    expect(elMessageError).toHaveBeenCalledOnce()
    const message = elMessageError.mock.calls[0][0].message
    expect(message).toContain('请求参数有误')
    expect(message).toContain('synthetic-abc123')
    expect(message).toContain('报障时请提供给管理员')
  })

  it('falls back to the plain message without guidance when the response carries no request id', async () => {
    let captured: unknown
    const adapter = vi.fn((config) => {
      throw new AxiosError('synthetic failure', 'ERR_BAD_REQUEST', config, {}, {
        config,
        status: 413,
        statusText: 'Payload Too Large',
        headers: new AxiosHeaders(),
        data: { code: 413, message: '提交内容过大，请调整后重试' },
      })
    })

    void api.get('/api/v1/synthetic-bulk', { skipResponseMetrics: true, adapter }).catch((error: unknown) => {
      captured = error
    })
    await vi.waitFor(() => expect(captured).toBeDefined())

    dispatchUnhandledRejection(captured)

    expect(elMessageError).toHaveBeenCalledOnce()
    const message = elMessageError.mock.calls[0][0].message
    expect(message).not.toContain('报障时请提供给管理员')
    expect(message).not.toContain('请求 ID')
    expect(message).toBe('提交内容过大，请调整后重试')
  })
})
