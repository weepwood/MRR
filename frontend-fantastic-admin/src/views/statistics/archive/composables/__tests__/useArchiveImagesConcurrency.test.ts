import { beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, ref } from 'vue'
import { useArchiveImages } from '../useArchiveImages'

const { query, patient } = vi.hoisted(() => ({ query: vi.fn(), patient: vi.fn(async () => ({ data: [] })) }))
vi.mock('@/api/modules/archive-export', () => ({ downloadArchiveZip: vi.fn(), getArchiveZipExportPlan: vi.fn() }))
vi.mock('@/api/modules/external-archive', () => ({ downloadExternalArchive: vi.fn(), externalArchiveSession: { value: null }, getExternalArchiveImages: vi.fn() }))
vi.mock('@/api/modules/image', () => ({ getImgByCode: query, updateImageType: vi.fn() }))
vi.mock('@/utils/composables/useAuth', () => ({ default: () => ({ auth: () => true }) }))
vi.mock('@/api/modules/search', () => ({ getPatientByBah: patient, getArchiveCasesByIdCard: vi.fn(), getArchiveCasesByToken: vi.fn() }))
vi.mock('../useArchiveSearchHistory', () => ({ addArchiveSearchHistory: vi.fn(), captureArchiveSearchHistorySession: vi.fn(() => null) }))
vi.mock('../useArchiveExportJob', () => ({ useArchiveExportJob: () => ({ job: {}, cancelling: false, downloading: false, discard: vi.fn() }) }))

describe('archive query results', () => {
  beforeEach(() => { query.mockReset(); patient.mockReset(); patient.mockResolvedValue({ data: [] }) })
  it('clears old patient loading when the next search only uses an SJH', async () => {
    let finish: (value: { data: never[] }) => void = () => {}
    patient.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
    query.mockResolvedValueOnce({ data: [{ id: 1, bah: '123' }] }).mockResolvedValueOnce({ data: [{ id: 2, bah: '10000001', sjh: '456' }] })
    const scope = effectScope()
    const archive = scope.run(() => useArchiveImages({ externalSession: ref(null) }))!
    archive.searchBah.value = '123'
    const old = archive.loadImages()
    await Promise.resolve(); await Promise.resolve()
    expect(archive.patientLoading.value).toBe(true)
    archive.searchBah.value = '10000001'; archive.searchSjh.value = '456'
    await archive.loadImages()
    expect(archive.patientLoading.value).toBe(false)
    finish({ data: [] }); await old
    expect(archive.images.value.map(image => image.id)).toEqual([2])
    scope.stop()
  })
  it('ignores a slower previous response after a new query finishes', async () => {
    let resolveOld: (value: unknown) => void = () => {}
    query.mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve }))
      .mockResolvedValueOnce({ data: [{ id: 2, bah: '456' }], meta: { requestId: 'new-request' } })
    const scope = effectScope()
    const archive = scope.run(() => useArchiveImages({ externalSession: ref(null) }))!
    archive.searchBah.value = '123'
    const previous = archive.loadImages()
    archive.searchBah.value = '456'
    await archive.loadImages()
    resolveOld({ data: [{ id: 1, bah: '123' }], meta: { requestId: 'old-request' } })
    await previous
    expect(archive.images.value.map(image => image.id)).toEqual([2])
    expect(archive.requestId.value).toBe('new-request')
    scope.stop()
  })
  it('shows malformed responses as failures instead of claiming there are no images', async () => {
    query.mockResolvedValue({ data: { unexpected: true }, meta: { requestId: 'schema-error' } })
    const scope = effectScope()
    const archive = scope.run(() => useArchiveImages({ externalSession: ref(null) }))!
    archive.searchBah.value = '123'
    await archive.loadImages()
    expect(archive.errorMsg.value).toContain('响应格式异常')
    expect(archive.requestId.value).toBe('schema-error')
    scope.stop()
  })
})
