<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import { ElPagination } from 'element-plus'
import { computed, ref, watch } from 'vue'
import 'element-plus/es/components/pagination/style/css'

// Cursor pagination adapts the data contract only; all controls and geometry
// are provided by the same Element Plus pagination as offset-based tables.
const props = withDefaults(defineProps<{
  page?: number
  canPrevious: boolean
  canNext: boolean
  loading?: boolean
  maxReachablePage?: number
  pageSize?: number
  pageSizes?: number[]
}>(), { loading: false })

const emit = defineEmits<{
  sizeChange: [size: number]
  jump: [page: number]
}>()
const compact = useMediaQuery('(max-width: 760px)')
const currentPage = computed(() => props.page ?? 1)
const reachablePageCount = computed(() => Math.max(currentPage.value, props.maxReachablePage ?? currentPage.value))
const layout = computed(() => ['slot', 'prev', 'pager', 'next', ...(props.maxReachablePage === undefined ? [] : ['jumper']), ...(props.pageSizes?.length ? ['sizes'] : [])].join(', '))
const jumpError = ref(false)
watch(() => [props.page, props.maxReachablePage, props.loading], () => { jumpError.value = false })

function changePage(target: number) {
  if (props.loading || props.page === undefined || !Number.isSafeInteger(target) || target < 1 || target > reachablePageCount.value || target === currentPage.value) { return }
  if ((target > currentPage.value && !props.canNext) || (target < currentPage.value && !props.canPrevious)) { return }
  emit('jump', target)
}
function changeSize(size: number) {
  if (!props.loading && size !== props.pageSize && props.pageSizes?.includes(size)) { emit('sizeChange', size) }
}

// Native ElPagination normally clamps an out-of-range jumper to the last
// page. A cursor range is not a real total: reject it before clamping,
// leaving the current page and backend request unchanged.
function guardJump(event: Event) {
  const input = event.target
  if (!(input instanceof HTMLInputElement) || !input.closest('.el-pagination__jump')) { return }
  const target = Number(input.value)
  if (!/^\d+$/.test(input.value) || !Number.isSafeInteger(target) || target < 1 || target > reachablePageCount.value) {
    jumpError.value = true
    input.value = String(currentPage.value)
  }
  else {
    jumpError.value = false
  }
}
</script>

<template>
  <div class="mrr-pagination-adapter" @change.capture="guardJump">
    <ElPagination
      class="mrr-cursor-pagination"
      aria-label="分页"
      :title="`可前往 1–${reachablePageCount} 页`"
      :current-page="currentPage"
      :page-count="reachablePageCount"
      :page-size="pageSize"
      :page-sizes="pageSizes"
      :pager-count="compact ? 5 : 7"
      :layout="layout"
      :disabled="loading || page === undefined"
      @update:current-page="changePage"
      @update:page-size="changeSize"
    >
      <div class="el-pagination__total mrr-cursor-pagination__summary" role="status">
        <slot name="summary" />
        <span v-if="jumpError" class="mrr-pagination-range-error" role="alert"> · 可前往 1–{{ reachablePageCount }} 页</span>
      </div>
    </ElPagination>
  </div>
</template>
