import { AxiosError, AxiosHeaders } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import api from '../index'

const { user } = vi.hoisted(() => ({ user: { isLogin: true, isSessionVerified: true, token: 'synthetic-a', requestLogout: vi.fn() } }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => user }))

describe('session-bound history requests', () => {
  beforeEach(() => { Object.assign(user, { isLogin: true, isSessionVerified: true, token: 'synthetic-a' }) })

  it('cancels a queued request before the adapter when the token changes', async () => {
    const adapter = vi.fn()
    const response = api.post('/api/v1/archive-search-history', { bah: 'SYNTHETIC-A' }, {
      authSessionToken: 'synthetic-a',
      skipGlobalError: true,
      skipResponseMetrics: true,
      adapter,
    })
    user.token = 'synthetic-b'
    await expect(response).rejects.toMatchObject({ code: 'ERR_CANCELED' })
    expect(adapter).not.toHaveBeenCalled()
  })

  it('sends matching verified requests through the existing adapter and auth header', async () => {
    const adapter = vi.fn(async config => ({ config, status: 200, statusText: 'OK', headers: new AxiosHeaders(), data: { code: 200, data: [] } }))
    await api.get('/api/v1/archive-search-history', {
      authSessionToken: 'synthetic-a',
      skipResponseMetrics: true,
      adapter,
    })
    expect(adapter).toHaveBeenCalledOnce()
    expect(adapter.mock.calls[0][0].headers.get('Authorization')).toBe('Bearer synthetic-a')
  })

  it('rejects a session that is no longer verified', async () => {
    user.isSessionVerified = false
    const adapter = vi.fn()
    await expect(api.get('/api/v1/archive-search-history', {
      authSessionToken: 'synthetic-a',
      skipGlobalError: true,
      skipResponseMetrics: true,
      adapter,
    })).rejects.toMatchObject({ code: 'ERR_CANCELED' })
    expect(adapter).not.toHaveBeenCalled()
  })

  it.each([401, 428])('does not apply late session A auth failures (%s) to B', async (status) => {
    let fail: (reason: unknown) => void = () => {}
    const adapter = vi.fn(config => new Promise((_, reject) => {
      fail = () => reject(new AxiosError('synthetic failure', 'ERR_BAD_REQUEST', config, {}, {
        config,
        status,
        statusText: 'Denied',
        headers: new AxiosHeaders(),
        data: {},
      }))
    }))
    const response = api.get('/api/v1/archive-search-history', {
      authSessionToken: 'synthetic-a',
      skipResponseMetrics: true,
      adapter,
    })
    await vi.waitFor(() => expect(adapter).toHaveBeenCalled())
    user.token = 'synthetic-b'
    fail(undefined)
    await expect(response).rejects.toMatchObject({ code: 'ERR_CANCELED' })
    expect(user.requestLogout).not.toHaveBeenCalled()
  })
})
