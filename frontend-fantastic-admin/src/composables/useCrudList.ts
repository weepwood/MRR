import type { Ref } from 'vue'
import type { ApiResult, PaginatedResult } from '@/api/types'
import { getCurrentScope, onMounted, onScopeDispose, reactive, ref } from 'vue'

/**
 * CRUD 列表通用 composable。
 *
 * 消除 records / logs / audit-images 等 CRUD 视图中重复的分页、查询、重置逻辑。
 * 每个 CRUD 视图原本有 ~60 行重复的 loadData / handleSearch / resetFilters / handlePageChange，
 * 使用本 composable 后可降至 ~5 行调用。
 *
 * @param opts.fetchApi        返回 Promise<ApiResult<PaginatedResult<T>>> 的 API 函数
 * @param opts.defaultQuery    查询条件默认值（重置时恢复）
 * @param opts.immediate       是否在 onMounted 时自动加载（默认 true）
 * @param opts.defaultPageSize 默认每页条数（默认 20）
 *
 * @example
 * ```ts
 * const { list, total, loading, query, handleSearch, resetFilters } = useCrudList<
 *   ScanRecord,
 *   { bah?: string; btype?: number }
 * >({
 *   fetchApi: recordsApi.getList,
 *   defaultQuery: { bah: '', btype: undefined },
 * })
 * ```
 */
interface CrudListOptions<T, Q extends Record<string, any>> {
  fetchApi: (params: Q & { page: number, size: number }, context: CrudFetchContext) => Promise<CrudFetchResult<T>>
  defaultQuery: Q
  immediate?: boolean
  defaultPageSize?: number
}

interface CrudFetchContext {
  isCurrent: () => boolean
  /**
   * 静默刷新（自动轮询）标记。静默请求失败时 fetchApi 可返回 undefined，
   * 列表保留现有数据不进入空态/错误态，轮询下一轮再尝试。
   */
  silent: boolean
}

type CrudFetchResult<T> = ApiResult<PaginatedResult<T>> | PaginatedResult<T> | undefined

export function useCrudList<T, Q extends Record<string, any>>(opts: CrudListOptions<T, Q>) {
  const list = ref<T[]>([]) as Ref<T[]>
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(opts.defaultPageSize ?? 20)
  const loading = ref(false)
  /**
   * 结果代次：只在「非静默」请求的结果写入列表后 +1。传给 MrrDataTablePanel 的 result-key，
   * 用户发起的查询、翻页会让新结果逐行淡入；30 秒静默轮询不加，避免阅读时整表周期性闪动。
   */
  const resultVersion = ref(0)
  const query = reactive({ ...opts.defaultQuery }) as Q
  let generation = 0
  let disposed = false

  if (getCurrentScope()) {
    onScopeDispose(() => {
      disposed = true
      generation++
      loading.value = false
    })
  }

  async function loadData(options?: { silent?: boolean }) {
    if (disposed) { return }
    const silent = options?.silent === true
    const token = ++generation
    const isCurrent = () => !disposed && token === generation
    if (!silent) { loading.value = true }
    try {
      const params = {
        ...query,
        page: pageNum.value,
        size: pageSize.value,
      } as Q & { page: number, size: number }
      const res = await opts.fetchApi(params, { isCurrent, silent })
      if (!isCurrent()) { return }
      // 静默刷新失败（fetchApi 返回 undefined）：保留现有列表，等待下一轮轮询。
      if (res === undefined) { return }
      const pageData = ('data' in res ? res.data : res) as PaginatedResult<T> | undefined
      list.value = pageData?.list ?? []
      total.value = pageData?.total ?? 0
      if (!silent) { resultVersion.value += 1 }
    }
    catch (error) {
      // 静默刷新不抛错也不清数据，失败静默保留旧值。
      if (isCurrent() && !silent) { throw error }
    }
    finally {
      if (isCurrent()) { loading.value = false }
    }
  }

  function handleSearch() {
    pageNum.value = 1
    return loadData()
  }

  function resetFilters() {
    // 清空现有查询字段并恢复默认值
    Object.keys(query).forEach((key) => {
      delete (query as any)[key]
    })
    Object.assign(query, opts.defaultQuery)
    return handleSearch()
  }

  function handlePageChange(page: number) {
    pageNum.value = page
    return loadData()
  }

  function handleSizeChange(size: number) {
    pageSize.value = size
    pageNum.value = 1
    return loadData()
  }

  if (opts.immediate !== false) {
    onMounted(loadData)
  }

  return {
    list,
    total,
    pageNum,
    pageSize,
    loading,
    resultVersion,
    query,
    loadData,
    handleSearch,
    resetFilters,
    handlePageChange,
    handleSizeChange,
  }
}
