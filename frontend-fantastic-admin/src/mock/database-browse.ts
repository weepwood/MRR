import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { createDemoBrowse } from '../mock-support/database-browse'
import { demoRecords } from '../mock-support/records'

const browse = createDemoBrowse(demoRecords)
export default defineFakeRoute([
  { url: '/api/v1/system/database/browse/tables', response: () => browse.tables() },
  { url: '/api/v1/system/database/browse/tables/:key/rows', response: ({ params, query }) => browse.rows(String(params.key), query ?? {}) },
  { url: '/api/v1/system/database/browse/tables/:key', response: ({ params }) => browse.schema(String(params.key)) },
])
