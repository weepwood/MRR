import type { ExternalArchiveSession } from '@/api/modules/external-archive'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, ref } from 'vue'
import { setArchiveAccessMode } from '../../access-mode'
import { useArchiveImages } from '../useArchiveImages'

const { start, plan, stream, externalDownload, allowed, message } = vi.hoisted(() => ({
  start: vi.fn(),
  plan: vi.fn(),
  stream: vi.fn(),
  externalDownload: vi.fn(),
  allowed: { value: true },
  message: { warning: vi.fn(), error: vi.fn(), success: vi.fn() },
}))
vi.mock('element-plus', () => ({ ElMessage: message }))
vi.mock('@/api/modules/archive-export', () => ({ getArchiveZipExportPlan: plan, downloadArchiveZip: stream }))
vi.mock('@/api/modules/external-archive', () => ({
  downloadExternalArchive: externalDownload,
  externalArchiveSession: { value: null },
  getExternalArchiveImages: vi.fn(),
}))
vi.mock('@/api/modules/image', () => ({ getImgByCode: vi.fn(), updateImageType: vi.fn() }))
vi.mock('@/api/modules/search', () => ({ getPatientByBah: vi.fn(), getArchiveCasesByIdCard: vi.fn(), getArchiveCasesByToken: vi.fn() }))
vi.mock('@/utils/composables/useAuth', () => ({ default: () => ({ auth: () => allowed.value }) }))
vi.mock('../useArchiveSearchHistory', () => ({
  addArchiveSearchHistory: vi.fn(),
  captureArchiveSearchHistorySession: vi.fn(() => null),
}))
vi.mock('../useArchiveExportJob', () => ({
  useArchiveExportJob: () => ({ job: {}, cancelling: false, downloading: false, start, discard: vi.fn() }),
}))

const scopes: ReturnType<typeof effectScope>[] = []
function archive(session: ExternalArchiveSession | null = null) {
  const scope = effectScope()
  scopes.push(scope)
  const value = scope.run(() => useArchiveImages({ externalSession: ref(session) }))!
  value.searchBah.value = 'SYNTHETIC-ARCHIVE'
  value.images.value = [{ id: 1, bah: 'SYNTHETIC-ARCHIVE' }]
  return value
}

beforeEach(() => {
  vi.clearAllMocks()
  setArchiveAccessMode('internal')
  allowed.value = true
  start.mockResolvedValue(undefined)
  plan.mockResolvedValue({ data: { executionMode: 'BACKEND_JOB' } })
})
afterEach(() => { scopes.splice(0).forEach(scope => scope.stop()); setArchiveAccessMode('internal') })

describe('internal archive ZIP download', () => {
  it('starts one existing background job without planning or streaming the archive again', async () => {
    const value = archive()
    await value.handleDownload()
    expect(start).toHaveBeenCalledExactlyOnceWith({ format: 'ZIP', bah: 'SYNTHETIC-ARCHIVE', sjh: undefined })
    expect(plan).not.toHaveBeenCalled()
    expect(stream).not.toHaveBeenCalled()
    expect(value.downloading.value).toBe(false)
  })

  it('rejects missing download permission without requesting any export API', async () => {
    allowed.value = false
    await archive().handleDownload()
    expect(start).not.toHaveBeenCalled()
    expect(plan).not.toHaveBeenCalled()
    expect(message.warning).toHaveBeenCalledWith('当前账号没有病案下载权限')
  })

  it('rejects an empty archive without starting or planning a job', async () => {
    const value = archive()
    value.images.value = []
    await value.handleDownload()
    expect(start).not.toHaveBeenCalled()
    expect(plan).not.toHaveBeenCalled()
    expect(message.warning).toHaveBeenCalledWith('当前档案没有可下载的影像')
  })

  it('restores the loading state and shows a failed task creation without falling back to streaming', async () => {
    start.mockRejectedValueOnce(new Error('synthetic safe task error'))
    const value = archive()
    await value.handleDownload()
    expect(value.downloading.value).toBe(false)
    expect(message.error).toHaveBeenCalledWith('synthetic safe task error')
    expect(stream).not.toHaveBeenCalled()
  })

  it('keeps the external-ticket download path and never creates an internal task', async () => {
    setArchiveAccessMode('external-ticket')
    externalDownload.mockRejectedValueOnce(new Error('synthetic external failure'))
    const value = archive({
      clientId: 'synthetic-client',
      externalUserId: 'synthetic-user',
      allowDownload: true,
      expiresIn: 60,
      cases: [{ bah: 'SYNTHETIC-ARCHIVE' }],
    })
    await value.handleDownload()
    expect(externalDownload).toHaveBeenCalledExactlyOnceWith('SYNTHETIC-ARCHIVE', undefined)
    expect(start).not.toHaveBeenCalled()
    expect(plan).not.toHaveBeenCalled()
    expect(value.downloading.value).toBe(false)
  })

  it('rejects an expired external session without creating an internal job', async () => {
    setArchiveAccessMode('external-ticket')
    await archive().handleDownload()
    expect(start).not.toHaveBeenCalled()
    expect(externalDownload).not.toHaveBeenCalled()
    expect(message.warning).toHaveBeenCalledWith('外部影像会话已失效，请重新发起访问')
  })

  it('keeps the external download grant requirement', async () => {
    setArchiveAccessMode('external-ticket')
    await archive({
      clientId: 'synthetic-client',
      externalUserId: 'synthetic-user',
      allowDownload: false,
      expiresIn: 60,
      cases: [{ bah: 'SYNTHETIC-ARCHIVE' }],
    }).handleDownload()
    expect(start).not.toHaveBeenCalled()
    expect(externalDownload).not.toHaveBeenCalled()
    expect(message.warning).toHaveBeenCalledWith('外部系统未授予批量下载权限')
  })
})
