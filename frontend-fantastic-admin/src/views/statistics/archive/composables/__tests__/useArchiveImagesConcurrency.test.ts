import { beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, ref } from 'vue'
import { useArchiveImages } from '../useArchiveImages'

const { query, patient, idCard } = vi.hoisted(() => ({
  query: vi.fn(),
  patient: vi.fn(async () => ({ data: [] })),
  idCard: vi.fn(),
}))
vi.mock('@/api/modules/archive-export', () => ({ downloadArchiveZip: vi.fn(), getArchiveZipExportPlan: vi.fn() }))
vi.mock('@/api/modules/external-archive', () => ({ downloadExternalArchive: vi.fn(), externalArchiveSession: { value: null }, getExternalArchiveImages: vi.fn() }))
vi.mock('@/api/modules/image', () => ({ getImgByCode: query, updateImageType: vi.fn() }))
vi.mock('@/utils/composables/useAuth', () => ({ default: () => ({ auth: () => true }) }))
vi.mock('@/api/modules/search', () => ({ getPatientByBah: patient, getArchiveCasesByIdCard: idCard, getArchiveCasesByToken: vi.fn() }))
vi.mock('../useArchiveSearchHistory', () => ({ addArchiveSearchHistory: vi.fn(), captureArchiveSearchHistorySession: vi.fn(() => null) }))
vi.mock('../useArchiveExportJob', () => ({ useArchiveExportJob: () => ({ job: {}, cancelling: false, downloading: false, discard: vi.fn() }) }))

describe('archive query results', () => {
  beforeEach(() => {
    query.mockReset()
    idCard.mockReset()
    patient.mockReset()
    patient.mockResolvedValue({ data: [] })
  })
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
  it('does not restore old diagnostics after local validation invalidates a pending request', async () => {
    let resolveOld: (value: unknown) => void = () => {}
    query.mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve }))
    const scope = effectScope()
    const archive = scope.run(() => useArchiveImages({ externalSession: ref(null) }))!
    archive.searchBah.value = '123'
    const previous = archive.loadImages()

    archive.resetImageLookup('NEEDS_SJH')
    resolveOld({ data: [], meta: { requestId: '0123456789abcdef', reasonCode: 'NO_MATCHING_IMAGES' } })
    await previous

    expect(archive.requestId.value).toBe('')
    expect(archive.lookupReason.value).toBe('NEEDS_SJH')
    expect(archive.submittedImageQuery.value).toBeNull()
    scope.stop()
  })
  it('keeps the image failure context when an old identity lookup fails late', async () => {
    let failOld: (reason: unknown) => void = () => {}
    idCard.mockImplementationOnce(() => new Promise((_, reject) => { failOld = reject }))
    query.mockResolvedValueOnce({ data: [], meta: { requestId: '0123456789abcdef', reasonCode: 'NO_MATCHING_IMAGES' } })
    const scope = effectScope()
    const archive = scope.run(() => useArchiveImages({ externalSession: ref(null) }))!
    const previous = archive.loadArchiveCasesByIdCard('SYNTH-ID')

    archive.searchBah.value = '123'
    await archive.loadImages()
    failOld(new Error('过期的身份查询错误'))

    expect(await previous).toBeUndefined()
    expect(archive.errorMsg.value).toBe('该病案暂未查询到影像')
    expect(archive.requestId.value).toBe('0123456789abcdef')
    expect(archive.submittedImageQuery.value).toEqual({ bah: '123', sjh: '' })
    expect(archive.idCardLoading.value).toBe(false)
    scope.stop()
  })
  it('keeps patient details with the latest selected archive', async () => {
    let resolveOld: (value: unknown) => void = () => {}
    query.mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve }))
      .mockResolvedValueOnce({ data: [] })
    const scope = effectScope()
    const archive = scope.run(() => useArchiveImages({ externalSession: ref(null) }))!
    archive.searchBah.value = '123'
    const previous = archive.loadImages(false, { bah: '123', sjh: '', name: '合成患者甲' })
    archive.searchBah.value = '456'
    await archive.loadImages(false, { bah: '456', sjh: '', name: '合成患者乙' })
    resolveOld({ data: [] })
    await previous

    expect(archive.patientList.value).toMatchObject([{ bah: '456', name: '合成患者乙' }])
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
