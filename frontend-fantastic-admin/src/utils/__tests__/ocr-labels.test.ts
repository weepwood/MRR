import { describe, expect, it } from 'vitest'
import { ocrAutoStatusLabels, ocrItemStatusLabels, ocrJobStatusLabels, ocrLabel, ocrPageStatusLabels, ocrReasonLabels, ocrReviewStatusLabels } from '../ocr-labels'

describe('ocr label maps', () => {
  it('covers every status emitted by the OCR job lifecycle', () => {
    for (const status of ['PENDING', 'RUNNING', 'PAUSED', 'CANCEL_REQUESTED', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELLED', 'INTERRUPTED']) {
      expect(ocrLabel(ocrJobStatusLabels, status)).not.toBe(status)
    }
  })
  it('covers page, item, review and automatic statuses shown in review tables', () => {
    for (const status of ['PENDING', 'PROCESSING', 'SUCCEEDED', 'LOW_CONFIDENCE', 'FAILED', 'SKIPPED', 'STALE', 'INTERRUPTED']) {
      expect(ocrLabel(ocrPageStatusLabels, status)).not.toBe(status)
    }
    for (const status of ['PENDING', 'PROCESSING', 'NO_MATCH', 'SUGGESTED', 'LOW_CONFIDENCE', 'FAILED', 'SKIPPED', 'CANCELLED']) {
      expect(ocrLabel(ocrItemStatusLabels, status)).not.toBe(status)
    }
    for (const status of ['PENDING', 'ACCEPTED', 'REJECTED', 'OVERRIDDEN']) {
      expect(ocrLabel(ocrReviewStatusLabels, status)).not.toBe(status)
    }
    for (const status of ['PENDING', 'APPLIED', 'REVIEW_REQUIRED']) {
      expect(ocrLabel(ocrAutoStatusLabels, status)).not.toBe(status)
    }
  })
  it('covers classification reasons and batch waiting reasons', () => {
    for (const reason of ['NO_MATCH', 'CONFLICT', 'EXACT_TITLE', 'REVIEW_REQUIRED', 'READY', 'OUTSIDE_WINDOW', 'P95_UNAVAILABLE',
      'MEASUREMENT_UNAVAILABLE', 'MEMORY_PRESSURE', 'CPU_PRESSURE', 'IMAGE_LATENCY', 'WAITING_AUTO_APPLY',
      'AUTO_APPLY_DISABLED', 'QUEUE_FULL', 'USER_PAUSED', 'AUTH_OR_QUEUE_BLOCKED']) {
      expect(ocrLabel(ocrReasonLabels, reason)).not.toBe(reason)
    }
  })
  it('passes unknown or empty codes through unchanged', () => {
    expect(ocrLabel(ocrJobStatusLabels, 'FUTURE_CODE')).toBe('FUTURE_CODE')
    expect(ocrLabel(ocrPageStatusLabels, undefined)).toBe('')
    expect(ocrLabel(ocrReasonLabels, null)).toBe('')
  })
})
