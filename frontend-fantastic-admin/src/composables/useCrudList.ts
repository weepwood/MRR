import type { Ref } from 'vue'
import { onMounted, reactive, ref } from 'vue'
import type { ApiResult, PaginatedResult } from '@/api/types'

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
  fetchApi: (params: Q & { page: number; size: number }) => Promise<ApiResult<PaginatedResult<T>> | PaginatedResult<T>>
  defaultQuery: Q
  immediate?: boolean
  defaultPageSize?: number
}

export function useCrudList<T, Q extends Record<string, any>>(opts: CrudListOptions<T, Q>) {
  const list = ref<T[]>([]) as Ref<T[]>
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(opts.defaultPageSize ?? 20)
  const loading = ref(false)
  const query = reactive({ ...opts.defaultQuery }) as Q

  async function loadData() {
    loading.value = true
    try {
      const params = {
        ...query,
        page: pageNum.value,
        size: pageSize.value,
      } as Q & { page: number; size: number }
      const res = await opts.fetchApi(params)
      const pageData = ('data' in res ? res.data : res) as PaginatedResult<T> | undefined
      list.value = pageData?.list ?? []
      total.value = pageData?.total ?? 0
    }
    finally {
      loading.value = false
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
    query,
    loadData,
    handleSearch,
    resetFilters,
    handlePageChange,
    handleSizeChange,
  }
}
