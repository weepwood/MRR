import type { ArchiveBoxRecordPayload } from '@/api/modules/archive-boxes'
import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { createArchiveBoxStore } from '../mock-support/archive-boxes'
import { demoRecords } from '../mock-support/records'
import { demoError, demoOk, demoPage } from '../mock-support/response'

const store = createArchiveBoxStore(demoRecords.scans)

function save(payload: ArchiveBoxRecordPayload, id?: number) {
  try {
    return demoOk(store.save(payload, id))
  }
  catch (error) {
    return demoError(400, error instanceof Error ? error.message : '装箱操作失败')
  }
}

function decode(value: unknown) {
  const raw = String(value ?? '')
  try { return decodeURIComponent(raw) }
  catch { return raw }
}

export default defineFakeRoute([
  {
    url: '/api/v1/archive-box-records',
    response: ({ query }) => demoOk(demoPage(store.list(query), query.page, query.size)),
  },
  {
    url: '/api/v1/archive-box-records/summary',
    response: () => demoOk(store.summary()),
  },
  {
    url: '/api/v1/archive-box-records/boxes',
    response: ({ query }) => demoOk(demoPage(store.groups(query.keyword), query.page, query.size)),
  },
  {
    url: '/api/v1/archive-box-records/record/:code',
    response: ({ params }) => demoOk(store.byCode(decode(params.code))),
  },
  {
    url: '/api/v1/archive-box-records/box/:boxNo',
    response: ({ params }) => demoOk(store.byBox(decode(params.boxNo))),
  },
  {
    url: '/api/v1/archive-box-records/:id',
    response: ({ params }) => {
      const row = store.get(Number(params.id))
      return row ? demoOk(row) : demoError(404, '未找到装箱记录')
    },
  },
  {
    url: '/api/v1/archive-box-records',
    method: 'post',
    response: ({ body }) => save(body),
  },
  {
    url: '/api/v1/archive-box-records/:id',
    method: 'put',
    response: ({ params, body }) => store.get(Number(params.id)) ? save(body, Number(params.id)) : demoError(404, '未找到装箱记录'),
  },
  {
    url: '/api/v1/archive-box-records/:id',
    method: 'delete',
    response: ({ params }) => store.remove(Number(params.id)) ? demoOk('删除成功') : demoError(404, '未找到装箱记录'),
  },
])
