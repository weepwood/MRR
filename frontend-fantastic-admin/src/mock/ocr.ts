import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { MEDICAL_RECORD_TYPES } from '@/constants/medical-record-types'
import { createOcrStore } from '../mock-support/ocr-jobs'
import { demoRecords } from '../mock-support/records'
import { boundedInt, demoError, demoOk } from '../mock-support/response'

const store = createOcrStore(demoRecords.scans)

export default defineFakeRoute([
  {
    url: '/api/v1/scan/:id/ocr',
    response: ({ params, query }) => {
      const fact = store.fact(Number(params.id), String(query.full) === 'true')
      return fact ? demoOk(fact) : demoError(404, '没有有效关联影像')
    },
  },
  {
    url: '/api/v1/archive-records/:id/ocr/jobs',
    method: 'post',
    response: ({ params, body }) => store.create(Number(params.id), body),
  },
  {
    url: '/api/v1/archive-records/:id/ocr/jobs',
    response: ({ params }) => demoOk(store.list(Number(params.id))),
  },
  {
    url: '/api/v1/ocr/jobs/:id',
    response: ({ params }) => {
      const job = store.job(String(params.id))
      return job ? demoOk(job) : demoError(404, 'OCR 任务不存在')
    },
  },
  {
    url: '/api/v1/ocr/jobs/:id/cancel',
    method: 'post',
    response: ({ params }) => {
      const job = store.cancel(String(params.id))
      return job ? demoOk(job) : demoError(404, 'OCR 任务不存在')
    },
  },
  {
    url: '/api/v1/ocr/jobs/:id/items',
    response: ({ params, query }) => {
      const items = store.items(String(params.id))
      return items ? demoOk(items.filter(item => item.scanId > Number(query.after || 0)).slice(0, boundedInt(query.limit, 50, 50))) : demoError(404, 'OCR 任务不存在')
    },
  },
  {
    url: '/api/v1/classification/jobs/:id/suggestions',
    response: ({ params, query }) => {
      const suggestions = store.suggestions(String(params.id))
      return suggestions ? demoOk(suggestions.filter(row => row.id > Number(query.after || 0)).slice(0, 50)) : demoError(404, 'OCR 任务不存在')
    },
  },
  {
    url: '/api/v1/classification/suggestions/:id/review',
    method: 'post',
    response: ({ params, body }) => store.review(Number(params.id), body),
  },
  {
    url: '/api/v1/medical-record-types',
    response: () => demoOk(MEDICAL_RECORD_TYPES),
  },
])
