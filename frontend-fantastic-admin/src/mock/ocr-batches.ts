import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { createOcrBatchStore } from '../mock-support/ocr-batches'
import { demoOcrStore } from '../mock-support/ocr-runtime'
import { demoRecords } from '../mock-support/records'
import { demoError, demoOk } from '../mock-support/response'

const store = createOcrBatchStore(demoRecords.scans, demoOcrStore)

export default defineFakeRoute([
  {
    url: '/api/v1/ocr/batches/preview',
    method: 'post',
    response: ({ body }) => store.preview(body),
  },
  {
    url: '/api/v1/ocr/batches',
    method: 'post',
    response: ({ body }) => store.create(body),
  },
  {
    url: '/api/v1/ocr/batches',
    response: ({ query }) => demoOk(store.list(String(query.after ?? ''))),
  },
  {
    url: '/api/v1/ocr/batches/:id',
    response: ({ params }) => {
      const batch = store.get(String(params.id))
      return batch ? demoOk(batch) : demoError(404, '历史批次不存在')
    },
  },
  {
    url: '/api/v1/ocr/batches/:id/jobs',
    response: ({ params, query }) => {
      const jobs = store.children(String(params.id), String(query.after ?? ''))
      return jobs ? demoOk(jobs) : demoError(404, '历史批次不存在')
    },
  },
  {
    url: '/api/v1/ocr/batches/:id/rollback/preview',
    response: ({ params }) => store.get(String(params.id)) ? demoOk([]) : demoError(404, '历史批次不存在'),
  },
  {
    url: '/api/v1/ocr/batches/:id/rollback',
    method: 'post',
    response: ({ params }) => store.get(String(params.id)) ? demoError(409, '演示批次没有自动归类记录') : demoError(404, '历史批次不存在'),
  },
  {
    url: '/api/v1/ocr/batches/:id/:action',
    method: 'post',
    response: ({ params }) => store.action(String(params.id), String(params.action)),
  },
])
