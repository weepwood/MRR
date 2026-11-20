import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getRequest, postRequest } from '../../index'
import {
  getNginxManagedFiles,
  getNginxManagedLogs,
  getNginxManagedStatus,
  testNginxManagedConfig,
} from '../nginx-management'

vi.mock('../../index', () => ({
  getRequest: vi.fn(),
  postRequest: vi.fn(),
}))

describe('Nginx management API', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('uses the fixed same-origin status endpoint', async () => {
    vi.mocked(getRequest).mockResolvedValue({
      data: { enabled: false, supported: false, installed: false },
    })
    const state = await getNginxManagedStatus()
    expect(state.enabled).toBe(false)
    expect(getRequest).toHaveBeenCalledWith('/api/v1/nginx/management/status')
  })

  it('never sends file paths or commands from the browser', async () => {
    vi.mocked(getRequest).mockResolvedValue({ data: [] })
    vi.mocked(postRequest).mockResolvedValue({ data: { valid: true, message: 'OK', durationMs: 2 } })
    await getNginxManagedFiles()
    await testNginxManagedConfig()
    expect(getRequest).toHaveBeenCalledWith('/api/v1/nginx/management/config/files')
    expect(postRequest).toHaveBeenCalledWith('/api/v1/nginx/management/config/test')
  })

  it('sends bounded log selectors and skips request metrics', async () => {
    vi.mocked(getRequest).mockResolvedValue({ data: { type: 'error', lines: [], truncated: false } })
    await getNginxManagedLogs('error', 50)
    expect(getRequest).toHaveBeenCalledWith('/api/v1/nginx/management/logs', {
      params: { type: 'error', lines: 50 },
      skipResponseMetrics: true,
    })
  })

  it('does not treat an empty API response as success', async () => {
    vi.mocked(getRequest).mockResolvedValue({})
    await expect(getNginxManagedStatus()).rejects.toThrow('网关状态查询未返回数据')
  })
})
