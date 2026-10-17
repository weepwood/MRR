import api, { getRequest, postRequest, putRequest } from '../index'

export interface ClassificationRule {
  id?: number
  targetType: number
  keyword: string
  matchScope: string
  matchMode: string
  weight: number
  negative: boolean
  required: boolean
  priority: number
  enabled: boolean
}
export interface ClassificationLexicon { term: string, aliases: string[], commonOcrErrors: string[] }
export interface ClassificationBundle { name: string, rules: ClassificationRule[], lexicon: ClassificationLexicon[] }
export interface ClassificationVersion { version: string, name: string, status: string }
export interface ClassificationSuggestion {
  id: number
  scanId: number
  suggestedType: number
  ruleScore: number
  ocrConfidence: number
  reviewStatus: string
  autoEligible: boolean
  reasonCode: string
  matchedRules: string
}
export interface OcrItem { scanId: number, status: string, failureCode: string | null, suggestedType: number | null }

export const getRecordTypes = () => getRequest<{ value: number, label: string }[]>('/api/v1/medical-record-types')
export const getReviewImage = (scanId: number) => api.get<Blob>(`/api/v1/img/content/${scanId}`, { responseType: 'blob' })
export const getClassificationVersions = () => getRequest<ClassificationVersion[]>('/api/v1/classification/rule-sets')
export const getClassificationBundle = (version: string) => getRequest<ClassificationBundle>(`/api/v1/classification/rule-sets/${version}`)
export const createClassificationDraft = (bundle: ClassificationBundle) => postRequest<string>('/api/v1/classification/rule-sets', bundle)
export const saveClassificationDraft = (version: string, bundle: ClassificationBundle) => putRequest<void>(`/api/v1/classification/rule-sets/${version}`, bundle)
export const publishClassificationDraft = (version: string) => postRequest<void>(`/api/v1/classification/rule-sets/${version}/publish`)
export const restoreClassificationVersion = (version: string) => postRequest<string>(`/api/v1/classification/rule-sets/${version}/restore`)
export const testClassificationVersion = (version: string, scanId: number) => postRequest<{ targetType: number | null, score: number, reason: string }>(`/api/v1/classification/rule-sets/${version}/test`, { scanId })
export const getClassificationSuggestions = (id: string, after = 0) => getRequest<ClassificationSuggestion[]>(`/api/v1/classification/jobs/${id}/suggestions`, { params: { after } })
export const getOcrItems = (id: string, after = 0) => getRequest<OcrItem[]>(`/api/v1/ocr/jobs/${id}/items`, { params: { after, limit: 50 } })
export const reviewClassification = (id: number, action: string, type?: number) => postRequest<void>(`/api/v1/classification/suggestions/${id}/review`, { action, type })
