import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, ref } from 'vue'
import { setArchiveAccessMode } from '../../access-mode'
import { useArchiveImages } from '../useArchiveImages'

const { query, patient, idCard, update, externalQuery } = vi.hoisted(() => ({
  query: vi.fn(),
  patient: vi.fn(async () => ({ data: [] })),
  idCard: vi.fn(),
  update: vi.fn(),
  externalQuery: vi.fn(),
}))
vi.mock('@/api/modules/archive-export', () => ({ downloadArchiveZip: vi.fn(), getArchiveZipExportPlan: vi.fn() }))
vi.mock('@/api/modules/external-archive', () => ({ downloadExternalArchive: vi.fn(), externalArchiveSession: { value: null }, getExternalArchiveImages: externalQuery }))
vi.mock('@/api/modules/image', () => ({ getImgByCode: query, updateImageType: update }))
vi.mock('@/utils/composables/useAuth', () => ({ default: () => ({ auth: () => true }) }))
vi.mock('@/api/modules/search', () => ({ getPatientByBah: patient, getArchiveCasesByIdCard: idCard, getArchiveCasesByToken: vi.fn() }))
vi.mock('../useArchiveSearchHistory', () => ({ addArchiveSearchHistory: vi.fn(), captureArchiveSearchHistorySession: vi.fn(() => null) }))
vi.mock('../useArchiveExportJob', () => ({ useArchiveExportJob: () => ({ job: {}, cancelling: false, downloading: false, discard: vi.fn() }) }))

describe('archive query results', () => {
  beforeEach(() => {
    setArchiveAccessMode('internal')
    query.mockReset()
    update.mockReset().mockResolvedValue({ data: null })
    externalQuery.mockReset()
    idCard.mockReset()
    patient.mockReset()
    patient.mockResolvedValue({ data: [] })
  })
  afterEach(() => setArchiveAccessMode('internal'))
  it('does not roll back the next archive when a previous classification fails', async () => {
    let reject!: (reason: Error) => void
    update.mockReturnValueOnce(new Promise((_, fail) => { reject = fail }))
    query.mockResolvedValueOnce({ data: [{ id: 1, bah: '123', btype: 1 }] })
      .mockResolvedValueOnce({ data: [{ id: 2, bah: '456', btype: 3 }] })
    const scope = effectScope()
    try {
      const archive = scope.run(() => useArchiveImages({ externalSession: ref(null) }))!
      archive.searchBah.value = '123'
      await archive.loadImages()
      const task = archive.saveImageType(archive.images.value[0], 2)
      archive.searchBah.value = '456'
      await archive.loadImages()
      reject(new Error('synthetic old write failure'))
      await task
      expect(archive.images.value).toMatchObject([{ id: 2, btype: 3 }])
    }
    finally { scope.stop() }
  })
  it('blocks duplicate classification until current write settles and rolls back current failure', async () => {
    let reject!: (reason: Error) => void
    update.mockReturnValue(new Promise((_, fail) => { reject = fail }))
    query.mockResolvedValue({ data: [{ id: 1, bah: '123', btype: 1 }] })
    const scope = effectScope()
    try {
      const archive = scope.run(() => useArchiveImages({ externalSession: ref(null) }))!
      archive.searchBah.value = '123'
      await archive.loadImages()
      const first = archive.saveImageType(archive.images.value[0], 2)
      const second = archive.saveImageType(archive.images.value[0], 3)
      reject(new Error('synthetic failure'))
      await Promise.all([first, second])
      expect(update).toHaveBeenCalledTimes(1)
      expect(archive.images.value).toMatchObject([{ id: 1, btype: 1 }])
      expect(archive.savingType.value).toBe(false)
    }
    finally { scope.stop() }
  })
  it('rejects a late image response after scope disposal', async () => {
    let resolve!: (value: unknown) => void
    query.mockReturnValueOnce(new Promise((yes) => { resolve = yes }))
    const scope = effectScope()
    const archive = scope.run(() => useArchiveImages({ externalSession: ref(null) }))!
    archive.searchBah.value = '123'
    const task = archive.loadImages()
    scope.stop()
    resolve({ data: [{ id: 1, bah: '123' }] })
    await task
    expect(archive.images.value).toEqual([])
    expect(archive.patientList.value).toEqual([])
    expect(archive.loading.value).toBe(false)
  })
  it('invalidates in-flight external image results immediately on session loss', async () => {
    setArchiveAccessMode('external-ticket')
    let resolve!: (value: unknown) => void
    externalQuery.mockReturnValueOnce(new Promise((yes) => { resolve = yes }))
    const session = ref<import('@/api/modules/external-archive').ExternalArchiveSession | null>({
      clientId: 'synthetic-client',
      externalUserId: 'synthetic-user',
      allowDownload: false,
      expiresIn: 60,
      cases: [{ bah: '123', sjh: '456' }],
    })
    const scope = effectScope()
    try {
      const archive = scope.run(() => useArchiveImages({ externalSession: session }))!
      archive.searchBah.value = '123'
      archive.searchSjh.value = '456'
      const task = archive.loadImages()
      session.value = null
      resolve({ data: [{ id: 1, bah: '123', sjh: '456' }] })
      await task
      expect(archive.images.value).toEqual([])
      expect(archive.patientList.value).toEqual([])
      expect(archive.archiveCases.value).toEqual([])
      expect(archive.errorMsg.value).toContain('会话已失效')
    }
    finally { scope.stop() }
  })
  it('keeps current successful classification and its original image identity', async () => {
    query.mockResolvedValue({ data: [{ id: 1, bah: '123', btype: 1 }, { id: 2, bah: '123', btype: 3 }] })
    const scope = effectScope()
    try {
      const archive = scope.run(() => useArchiveImages({ externalSession: ref(null) }))!
      archive.searchBah.value = '123'
      await archive.loadImages()
      await archive.saveImageType(archive.images.value[0], 2)
      expect(update).toHaveBeenCalledExactlyOnceWith(1, { btype: 2 })
      expect(archive.images.value).toMatchObject([{ id: 1, btype: 2 }, { id: 2, btype: 3 }])
      expect(archive.savingType.value).toBe(false)
    }
    finally { scope.stop() }
  })
  it('clears previous external results on replacement and loads only the new session archive', async () => {
    setArchiveAccessMode('external-ticket')
    const session = ref<import('@/api/modules/external-archive').ExternalArchiveSession | null>({
      clientId: 'synthetic-client',
      externalUserId: 'synthetic-first',
      allowDownload: false,
      expiresIn: 60,
      cases: [{ bah: '123', sjh: '456' }],
    })
    externalQuery.mockResolvedValueOnce({ data: [{ id: 1, bah: '123', sjh: '456' }] })
      .mockResolvedValueOnce({ data: [{ id: 2, bah: '789', sjh: '987' }] })
    const scope = effectScope()
    try {
      const archive = scope.run(() => useArchiveImages({ externalSession: session }))!
      archive.searchBah.value = '123'
      archive.searchSjh.value = '456'
      await archive.loadImages()
      expect(archive.images.value).toMatchObject([{ id: 1 }])
      session.value = { ...session.value!, externalUserId: 'synthetic-second', cases: [{ bah: '789', sjh: '987' }] }
      expect(archive.images.value).toEqual([])
      expect(archive.patientList.value).toEqual([])
      expect(archive.archiveCases.value).toMatchObject([{ bah: '789' }])
      archive.searchBah.value = '789'
      archive.searchSjh.value = '987'
      await archive.loadImages()
      expect(archive.images.value).toMatchObject([{ id: 2 }])
    }
    finally { scope.stop() }
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
