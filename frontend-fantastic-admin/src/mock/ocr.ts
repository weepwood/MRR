import type { ClassificationBundle } from '@/api/modules/classification'
import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { MEDICAL_RECORD_TYPES } from '@/constants/medical-record-types'
import { createDemoClassificationCatalog } from '../mock-support/classification-catalog'
import { demoOcrStore as store } from '../mock-support/ocr-runtime'
import { boundedInt, demoError, demoOk } from '../mock-support/response'

const catalog = createDemoClassificationCatalog()

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
      const rows = store.suggestions(String(params.id))
      return rows ? demoOk(rows.filter(row => row.id > Number(query.after || 0)).slice(0, 50)) : demoError(404, 'OCR 任务不存在')
    },
  },
  { url: '/api/v1/classification/rule-sets', response: () => catalog.list() },
  { url: '/api/v1/classification/rule-sets/:version', response: ({ params }) => catalog.get(String(params.version)) },
  { url: '/api/v1/classification/rule-sets', method: 'POST', response: ({ body }) => catalog.create(body as unknown as ClassificationBundle) },
  { url: '/api/v1/classification/rule-sets/:version', method: 'PUT', response: ({ params, body }) => catalog.save(String(params.version), body as unknown as ClassificationBundle) },
  { url: '/api/v1/classification/rule-sets/:version/publish', method: 'POST', response: ({ params }) => catalog.publish(String(params.version)) },
  { url: '/api/v1/classification/rule-sets/:version/restore', method: 'POST', response: ({ params }) => catalog.restore(String(params.version)) },
  { url: '/api/v1/classification/rule-sets/:version/test', method: 'POST', response: ({ params, body }) => catalog.test(String(params.version), store.fact(Number(body?.scanId), true)?.text ?? null) },
  { url: '/api/v1/classification/suggestions/:id/review', method: 'POST', response: ({ params, body }) => store.review(Number(params.id), String(body?.action), body?.type) },
  {
    url: '/api/v1/medical-record-types',
    response: () => demoOk(MEDICAL_RECORD_TYPES),
  },
])
