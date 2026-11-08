import { describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { useCrudList } from '../useCrudList'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}
const result = (value: string) => ({ list: [value], total: 1, page: 1, size: 20 })

describe('cRUD列表请求归属', () => {
  it.each(['handleSearch', 'resetFilters', 'handlePageChange', 'handleSizeChange'] as const)('%s后旧响应不覆盖当前结果', async (action) => {
    const old = deferred<ReturnType<typeof result>>()
    const fetchApi = vi.fn().mockReturnValueOnce(old.promise).mockResolvedValue(result('new'))
    const scope = effectScope()
    const list = scope.run(() => useCrudList({ fetchApi, defaultQuery: { keyword: 'test' }, immediate: false }))!
    try {
      const first = list.loadData()
      if (action === 'handlePageChange' || action === 'handleSizeChange') {
        await list[action](2)
      }
      else { await list[action]() }
      old.resolve(result('old'))
      await first
      expect(list.list.value).toEqual(['new'])
      expect(list.total.value).toBe(1)
    }
    finally { scope.stop() }
  })

  it('旧finally不结束新请求loading，过期失败不会抛给当前页面', async () => {
    const old = deferred<ReturnType<typeof result>>()
    const current = deferred<ReturnType<typeof result>>()
    const fetchApi = vi.fn().mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise)
    const scope = effectScope()
    const list = scope.run(() => useCrudList({ fetchApi, defaultQuery: {}, immediate: false }))!
    try {
      const first = list.loadData()
      const second = list.loadData()
      expect(fetchApi.mock.calls[0][1].isCurrent()).toBe(false)
      expect(fetchApi.mock.calls[1][1].isCurrent()).toBe(true)
      old.reject(new Error('old error'))
      await expect(first).resolves.toBeUndefined()
      expect(list.loading.value).toBe(true)
      current.resolve(result('new'))
      await second
      expect(list.loading.value).toBe(false)
    }
    finally { scope.stop() }
  })

  it('销毁后忽略结果、禁止新请求并使上下文失效', async () => {
    const pending = deferred<ReturnType<typeof result>>()
    const fetchApi = vi.fn().mockReturnValue(pending.promise)
    const scope = effectScope()
    const list = scope.run(() => useCrudList({ fetchApi, defaultQuery: {}, immediate: false }))!
    const first = list.loadData()
    const isCurrent = fetchApi.mock.calls[0][1].isCurrent
    expect(isCurrent()).toBe(true)
    scope.stop()
    expect(isCurrent()).toBe(false)
    pending.resolve(result('old'))
    await first
    await list.loadData()
    expect(list.list.value).toEqual([])
    expect(list.loading.value).toBe(false)
    expect(fetchApi).toHaveBeenCalledTimes(1)
  })

  it('当前失败仍抛出，loading正常结束', async () => {
    const fetchApi = vi.fn().mockRejectedValue(new Error('current failure'))
    const scope = effectScope()
    const list = scope.run(() => useCrudList({ fetchApi, defaultQuery: {}, immediate: false }))!
    try {
      await expect(list.loadData()).rejects.toThrow('current failure')
      expect(list.loading.value).toBe(false)
    }
    finally { scope.stop() }
  })
})
