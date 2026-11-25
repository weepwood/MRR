import type { ClassificationBundle } from '../classification'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createClassificationDraft,
  getClassificationBundle,
  getClassificationSuggestions,
  getClassificationVersions,
  getOcrItems,
  getRecordTypes,
  getReviewImage,
  publishClassificationDraft,
  restoreClassificationVersion,
  reviewClassification,
  saveClassificationDraft,
  testClassificationVersion,
} from '../classification'

const http = vi.hoisted(() => ({ get: vi.fn(), getRequest: vi.fn(), postRequest: vi.fn(), putRequest: vi.fn() }))
vi.mock('../../index', () => ({ default: { get: http.get }, getRequest: http.getRequest, postRequest: http.postRequest, putRequest: http.putRequest }))
const bundle: ClassificationBundle = { name: '合成规则', rules: [], lexicon: [] }

describe('oCR 与分类 API 契约', () => {
  beforeEach(() => vi.resetAllMocks())
  afterEach(() => vi.unstubAllEnvs())

  it('原图请求返回解包 Blob 并传递取消信号；仅 Pages 使用 fetch', async () => {
    const blob = new Blob(['synthetic'], { type: 'image/png' })
    http.get.mockResolvedValue(blob)
    const signal = new AbortController().signal
    vi.stubEnv('MODE', 'production')
    expect(await getReviewImage(7, signal)).toBe(blob)
    expect(http.get).toHaveBeenLastCalledWith('/api/v1/img/content/7', { adapter: undefined, responseType: 'blob', signal, skipGlobalError: true })
    vi.stubEnv('MODE', 'pages')
    expect(await getReviewImage(8, signal)).toBe(blob)
    expect(http.get).toHaveBeenLastCalledWith('/api/v1/img/content/8', { adapter: 'fetch', responseType: 'blob', signal, skipGlobalError: true })
  })

  it('只读分类目录及规则版本查询使用对应资源', () => {
    getRecordTypes()
    getClassificationVersions()
    getClassificationBundle('v7')
    expect(http.getRequest.mock.calls).toEqual([
      ['/api/v1/medical-record-types'],
      ['/api/v1/classification/rule-sets'],
      ['/api/v1/classification/rule-sets/v7'],
    ])
    expect(http.postRequest).not.toHaveBeenCalled()
    expect(http.putRequest).not.toHaveBeenCalled()
  })

  it('草稿保存、发布、恢复及试算分别使用既有接口，不混淆动作', () => {
    createClassificationDraft(bundle)
    saveClassificationDraft('v7', bundle)
    publishClassificationDraft('v7')
    restoreClassificationVersion('v6')
    testClassificationVersion('v7', 42)
    expect(http.postRequest.mock.calls).toEqual([
      ['/api/v1/classification/rule-sets', bundle],
      ['/api/v1/classification/rule-sets/v7/publish'],
      ['/api/v1/classification/rule-sets/v6/restore'],
      ['/api/v1/classification/rule-sets/v7/test', { scanId: 42 }],
    ])
    expect(http.putRequest).toHaveBeenCalledExactlyOnceWith('/api/v1/classification/rule-sets/v7', bundle)
  })

  it('建议与OCR项分页保留游标，人工审核仅发送明确动作及类型', () => {
    getClassificationSuggestions('job', 13)
    getOcrItems('job', 21)
    reviewClassification(29, 'REJECT')
    expect(http.getRequest.mock.calls).toEqual([
      ['/api/v1/classification/jobs/job/suggestions', { params: { after: 13 } }],
      ['/api/v1/ocr/jobs/job/items', { params: { after: 21, limit: 50 } }],
    ])
    expect(http.postRequest).toHaveBeenCalledExactlyOnceWith('/api/v1/classification/suggestions/29/review', { action: 'REJECT', type: undefined })
  })
})
