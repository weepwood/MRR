import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { createDemoArchiveExports } from '../mock-support/archive-exports'
import { demoRecords } from '../mock-support/records'

const exports = createDemoArchiveExports(demoRecords)
export default defineFakeRoute([
  { url: '/api/v1/archive-exports/plan/zip', response: ({ query }) => exports.plan('ZIP', query ?? {}) },
  { url: '/api/v1/archive-exports/plan/pdf', response: ({ query }) => exports.plan('PDF', query ?? {}, true) },
  { url: '/api/v1/archive-exports/plan/pdf/selection', method: 'POST', response: ({ body }) => exports.plan('PDF', body ?? {}) },
  { url: '/api/v1/archive-exports/jobs', method: 'POST', response: ({ body }) => exports.create(body ?? {}) },
  { url: '/api/v1/archive-exports/jobs', response: ({ query }) => exports.list(query ?? {}) },
  { url: '/api/v1/archive-exports/jobs/:id/download', response: ({ params, query }) => exports.download(String(params.id), query?.demoRange) },
  { url: '/api/v1/archive-exports/jobs/:id/cancel', method: 'POST', response: ({ params }) => exports.cancel(String(params.id)) },
  { url: '/api/v1/archive-exports/jobs/:id', response: ({ params }) => exports.get(String(params.id)) },
  { url: '/api/v1/archive-exports/zip', response: ({ query }) => exports.directZip(query ?? {}) },
  { url: '/api/v1/archive-exports/pdf', response: ({ query }) => exports.pdfImages(query ?? {}) },
  { url: '/api/v1/archive-exports/pdf/selection', method: 'POST', response: ({ body }) => exports.pdfImages(body ?? {}) },
])
