import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as exports from '../archive-export'
import { getBrowseRows, getBrowseSchema, getBrowseTables } from '../database-browse'
import * as image from '../image'
import * as ocr from '../ocr'
import * as quality from '../quality-case-review'
import { deleteSetting, getSetting, setSetting } from '../settings'
import user from '../user'

const http = vi.hoisted(() => ({
  getRequest: vi.fn(),
  postRequest: vi.fn(),
  putRequest: vi.fn(),
  deleteRequest: vi.fn(),
  revokeAuthSession: vi.fn(),
  get: vi.fn(),
}))
vi.mock('../../index', () => ({ ...http, default: { get: http.get } }))

beforeEach(() => {
  vi.resetAllMocks()
  vi.spyOn(Date, 'now').mockReturnValue(123456)
})
afterEach(() => {
  vi.restoreAllMocks()
  window.history.replaceState({}, '', '/')
})

describe('实际业务 API 请求契约（仅传输替身）', () => {
  it('登录、注册和首次修改密码保持各自的协议与错误处理', () => {
    const registration = { username: 'synthetic', password: 'synthetic-password', displayName: '测试' }
    const change = { currentPassword: 'synthetic-old', newPassword: 'synthetic-new', confirmPassword: 'synthetic-new' }
    user.login({ account: registration.username, password: registration.password })
    user.register(registration)
    user.permission('synthetic-session')
    user.passwordEdit({ password: change.currentPassword, newPassword: change.newPassword })
    user.requiredPasswordChange(change)
    user.logout('synthetic-session')
    expect(http.postRequest.mock.calls).toEqual([
      ['/api/v1/auth/login', { username: registration.username, password: registration.password }, { skipGlobalError: true }],
      ['/api/v1/auth/register', registration, { skipGlobalError: true }],
      ['/api/v1/auth/password/edit', { password: change.currentPassword, newPassword: change.newPassword }],
      ['/api/v1/auth/password/required-change', change, { skipGlobalError: true }],
    ])
    expect(http.getRequest).toHaveBeenCalledExactlyOnceWith('/api/v1/auth/me', { authSessionToken: 'synthetic-session', retry: false, skipGlobalError: true })
    expect(http.revokeAuthSession).toHaveBeenCalledExactlyOnceWith('synthetic-session')
  })

  it('账号列表默认分页与筛选参数完整传递，审核与账号状态操作分离', () => {
    const create = { username: 'synthetic', roleCode: 'READER', status: 'active' as const, temporaryPasswordValidHours: 1 }
    const reset = { administratorPassword: 'synthetic', temporaryPasswordValidHours: 1 }
    const update = { displayName: '测试', roleCode: 'READER', status: 'active' }
    user.getUsers()
    user.getUsers({ page: 3, size: 50, keyword: 'synthetic', roleCode: 'READER', status: 'pending' })
    user.createUser(create)
    user.approveRegistration(7, { roleCode: 'READER' })
    user.rejectRegistration(8, { rejectReason: '测试' })
    user.reopenRegistration(8)
    user.resetUserPassword(9, reset)
    user.updateUser(9, update)
    user.disableUser(10)
    user.getRoles()
    user.updateRole('READER', { name: '测试角色' })
    expect(http.getRequest.mock.calls).toEqual([
      ['/api/v1/auth/users', { params: { page: 1, size: 20, keyword: undefined, roleCode: undefined, status: undefined } }],
      ['/api/v1/auth/users', { params: { page: 3, size: 50, keyword: 'synthetic', roleCode: 'READER', status: 'pending' } }],
      ['/api/v1/auth/roles'],
    ])
    expect(http.postRequest.mock.calls).toEqual([
      ['/api/v1/auth/users', create],
      ['/api/v1/auth/users/7/registration/approve', { roleCode: 'READER' }],
      ['/api/v1/auth/users/8/registration/reject', { rejectReason: '测试' }],
      ['/api/v1/auth/users/8/registration/reopen', {}],
      ['/api/v1/auth/users/9/password/reset', reset],
    ])
    expect(http.putRequest.mock.calls).toEqual([
      ['/api/v1/auth/users/9', update],
      ['/api/v1/auth/roles/READER', { name: '测试角色' }],
    ])
    expect(http.deleteRequest).toHaveBeenCalledExactlyOnceWith('/api/v1/auth/users/10')
  })

  it('影像查询保留组合键、显式调用方优先级和刷新语义', () => {
    window.history.replaceState({}, '', '/?userid=synthetic-url')
    image.getImgApiByBah('000123')
    image.getImgByCode('000123', '000456')
    image.getImgByCode('000123', '000456', true, ' synthetic-explicit ')
    expect(http.getRequest.mock.calls).toEqual([
      ['/api/v1/img/000123'],
      ['/api/v1/img/search', { params: { bah: '000123', sjh: '000456', userid: 'synthetic-url' }, headers: undefined }],
      ['/api/v1/img/search', {
        params: { bah: '000123', sjh: '000456', userid: 'synthetic-explicit', _: 123456 },
        headers: { 'Cache-Control': 'no-cache', 'Pragma': 'no-cache' },
      }],
    ])
  })

  it('三条历史原图入口保持二进制契约，分类写入使用独立 ID', async () => {
    const blob = new Blob(['synthetic'], { type: 'image/png' })
    http.get.mockResolvedValue(blob)
    expect(await image.getImage('000123', '7', 'folder', 'synthetic.png')).toBe(blob)
    expect(await image.getImgByCx('synthetic')).toBe(blob)
    expect(await image.getImageFromOss(9)).toBe(blob)
    image.updateImageType(9, { btype: 3 })
    expect(http.get.mock.calls).toEqual([
      ['/api/v1/img/image/000123/7/folder/synthetic.png', { responseType: 'blob' }],
      ['/api/v1/img/image/synthetic', { responseType: 'blob' }],
      ['/api/v1/img/oss-image/9', { responseType: 'blob' }],
    ])
    expect(http.putRequest).toHaveBeenCalledExactlyOnceWith('/api/v1/img/updateImageType/9', { btype: 3 })
  })

  it('导出规划区分完整病案与精确选中影像，任务 ID 正确编码', () => {
    exports.getArchiveZipExportPlan('000123', '')
    exports.getArchivePdfExportPlan('000123', '000456', 2)
    exports.getSelectedImagesPdfExportPlan([1, '002'])
    exports.createArchiveExportJob({ format: 'PDF', ids: [1, '002'], idempotencyKey: 'synthetic-job' })
    exports.listActiveArchiveExportJobs('PDF')
    exports.getArchiveExportJob('synthetic/id')
    exports.cancelArchiveExportJob('synthetic/id')
    expect(http.getRequest.mock.calls).toEqual([
      ['/api/v1/archive-exports/plan/zip', { params: { bah: '000123', sjh: undefined } }],
      ['/api/v1/archive-exports/plan/pdf', { params: { bah: '000123', sjh: '000456', selectedCount: 2 } }],
      ['/api/v1/archive-exports/jobs', { params: { format: 'PDF', limit: 5 } }],
      ['/api/v1/archive-exports/jobs/synthetic%2Fid', { retry: true, maxRetryCount: 2, skipGlobalError: true }],
    ])
    expect(http.postRequest.mock.calls).toEqual([
      ['/api/v1/archive-exports/plan/pdf/selection', { ids: ['1', '002'] }],
      ['/api/v1/archive-exports/jobs', { format: 'PDF', ids: ['1', '002'], idempotencyKey: 'synthetic-job' }],
      ['/api/v1/archive-exports/jobs/synthetic%2Fid/cancel'],
    ])
  })

  it('oCR 默认不请求全文，任务创建保留选中影像与独立请求标识', () => {
    ocr.getOcrFact(7)
    ocr.getOcrFact(7, true)
    ocr.createOcrJob(8, 7)
    ocr.getOcrJob('synthetic-job')
    ocr.getOcrJobs(8)
    ocr.cancelOcrJob('synthetic-job')
    expect(http.getRequest.mock.calls).toEqual([
      ['/api/v1/scan/7/ocr', { params: { full: false } }],
      ['/api/v1/scan/7/ocr', { params: { full: true } }],
      ['/api/v1/ocr/jobs/synthetic-job'],
      ['/api/v1/archive-records/8/ocr/jobs'],
    ])
    expect(http.postRequest.mock.calls).toEqual([
      ['/api/v1/archive-records/8/ocr/jobs', { scanId: 7, idempotencyKey: expect.stringMatching(/^ocr-123456-[a-z0-9]+$/) }],
      ['/api/v1/ocr/jobs/synthetic-job/cancel'],
    ])
  })

  it('质量复核传递取消信号、游标与期望修订号，不自动重试决策', () => {
    const signal = new AbortController().signal
    const base = '/api/v1/system/data-quality/case-reviews'
    quality.getQualityReview(7, signal)
    quality.listQualityReviews('cursor', signal)
    quality.getQualityReviewEvents('case', 3, signal)
    quality.recheckQualityReview('case', signal)
    quality.decideQualityReview(7, 4, 'CONFIRM')
    expect(http.getRequest.mock.calls).toEqual([
      [`${base}/issue/7`, { signal, skipGlobalError: true }],
      [base, { params: { before: 'cursor' }, signal, skipGlobalError: true }],
      [`${base}/case/events`, { params: { before: 3 }, signal, skipGlobalError: true }],
      [`${base}/case/recheck`, { signal, skipGlobalError: true }],
    ])
    expect(http.postRequest).toHaveBeenCalledExactlyOnceWith(`${base}/issue/7`, undefined, { params: { expectedRevision: 4, action: 'CONFIRM' }, retry: false, skipGlobalError: true })
  })

  it('只读数据库浏览解包响应、编码表标识并原样传递游标', async () => {
    const signal = new AbortController().signal
    const params = { page: 3, size: 20, cursor: 'synthetic', order: 'desc' as const }
    const schema = { tableKey: 'synthetic/table', omittedColumnCount: 8 }
    const page = { tableKey: 'synthetic/table', rows: [{ id: 7 }] }
    http.getRequest.mockResolvedValueOnce({ data: undefined }).mockResolvedValueOnce({ data: schema }).mockResolvedValueOnce({ data: page })
    expect(await getBrowseTables(signal)).toEqual([])
    expect(await getBrowseSchema('synthetic/table', signal)).toEqual(schema)
    expect(await getBrowseRows('synthetic/table', params, signal)).toEqual(page)
    const base = '/api/v1/system/database/browse/tables'
    const options = { signal, retry: false, skipGlobalError: true }
    expect(http.getRequest.mock.calls).toEqual([
      [base, options],
      [`${base}/synthetic%2Ftable`, options],
      [`${base}/synthetic%2Ftable/rows`, { ...options, params }],
    ])
    expect(http.postRequest).not.toHaveBeenCalled()
  })

  it('单项设置读取、保存与移除覆盖值使用各自请求方法', () => {
    const path = '/api/v1/settings/synthetic%2Fkey'
    getSetting('synthetic/key')
    setSetting('synthetic/key', 'false')
    deleteSetting('synthetic/key')
    expect(http.getRequest).toHaveBeenCalledExactlyOnceWith(path)
    expect(http.putRequest).toHaveBeenCalledExactlyOnceWith(path, { value: 'false' })
    expect(http.deleteRequest).toHaveBeenCalledExactlyOnceWith(path)
  })
})
