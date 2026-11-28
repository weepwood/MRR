<script setup lang="ts">
import type {
  ArchiveBoxGroup,
  ArchiveBoxRecord,
  ArchiveBoxRecordPayload,
  ArchiveBoxStatus,
  ArchiveBoxSummary,
} from '@/api/modules/archive-boxes'
import type { MetricTone, MetricValueTone } from '@/components/MrrMetricCard/types'
import type { MrrTableAction } from '@/components/MrrTableActions/types'
import { ElMessage } from 'element-plus'
import { Plus, RefreshCw as Refresh } from 'lucide-vue-next'
import { computed, onMounted, onScopeDispose, reactive, ref, toRef, watch } from 'vue'
import {
  createArchiveBoxRecord,
  deleteArchiveBoxRecord,
  getArchiveBoxGroups,
  getArchiveBoxRecords,
  getArchiveBoxSummary,
  updateArchiveBoxRecord,
} from '@/api/modules/archive-boxes'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import MrrDataTablePanel from '@/components/MrrDataTablePanel/index.vue'
import MrrFilterActionButton from '@/components/MrrFilterActionButton/index.vue'
import MrrFilterBar from '@/components/MrrFilterBar/index.vue'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import MrrStatusTag from '@/components/MrrStatusTag/index.vue'
import MrrTableActions from '@/components/MrrTableActions/index.vue'
import { useCrudList } from '@/composables/useCrudList'
import { useTableActionLayout } from '@/composables/useTableActionLayout'
import { useUserStore } from '@/store/modules/user'
import { actionDialog } from '@/ui/components/ActionDialog'
import useAuth from '@/utils/composables/useAuth'
import { tableSequenceColumn } from '@/utils/table-sequence'

defineOptions({ name: 'ArchiveBoxesPage' })

type ViewMode = 'records' | 'boxes'

interface StatusOption {
  value: ArchiveBoxStatus
  label: string
  tone: 'success' | 'danger' | 'warning' | 'neutral' | 'info'
}

const statusOptions: StatusOption[] = [
  { value: 'NORMAL', label: '正常', tone: 'success' },
  { value: 'MISSING', label: '缺失', tone: 'danger' },
  { value: 'MISPLACED', label: '存放在其他箱子', tone: 'warning' },
  { value: 'CONFLICT', label: '箱号冲突', tone: 'info' },
  { value: 'OTHER', label: '其他异常', tone: 'neutral' },
]

const activeView = ref<ViewMode>('records')
const userStore = useUserStore()
const { auth } = useAuth()
const canRead = computed(() => userStore.isSessionVerified && auth('record:read'))
const canEdit = computed(() => canRead.value && auth('record:edit'))
const session = { key: () => String(userStore.sessionEpoch), allowed: () => canRead.value }
const recordsError = ref('')
const boxesError = ref('')
const summaryError = ref('')
const summary = ref<ArchiveBoxSummary>()
const {
  list: records,
  loadedRowOffset: recordRowOffset,
  pageNum: page,
  pageSize: size,
  total,
  loading: recordsLoading,
  query: filters,
  loadData: loadRecords,
  handleSearch,
  resetFilters,
  handlePageChange: changeRecordPage,
  handleSizeChange: changeRecordSize,
  resultVersion: recordVersion,
} = useCrudList<ArchiveBoxRecord, { keyword: string, bah: string, sjh: string, boxNo: string, status: ArchiveBoxStatus | '' }>({
  defaultQuery: { keyword: '', bah: '', sjh: '', boxNo: '', status: '' },
  defaultPageSize: 100,
  immediate: false,
  session: { ...session, onReset: () => { recordsError.value = '' } },
  fetchApi: async (params, { isCurrent }) => {
    if (isCurrent()) { recordsError.value = '' }
    try {
      return await getArchiveBoxRecords({
        page: params.page,
        size: params.size,
        sortBy: 'updatedAt',
        sortOrder: 'desc',
        ...(params.keyword.trim() && { keyword: params.keyword.trim() }),
        ...(params.bah.trim() && { bah: params.bah.trim() }),
        ...(params.sjh.trim() && { sjh: params.sjh.trim() }),
        ...(params.boxNo.trim() && { boxNo: params.boxNo.trim() }),
        ...(params.status && { status: params.status }),
      })
    }
    catch (cause: unknown) {
      if (isCurrent()) { recordsError.value = cause instanceof Error ? cause.message : '装箱明细加载失败' }
      return { list: [], total: 0, page: params.page, size: params.size }
    }
  },
})
const {
  list: boxes,
  loadedRowOffset: boxRowOffset,
  pageNum: boxPage,
  pageSize: boxSize,
  total: boxTotal,
  loading: boxesLoading,
  query: boxQuery,
  loadData: loadBoxes,
  handleSearch: searchBoxes,
  resetFilters: resetBoxSearch,
  handlePageChange: changeBoxPage,
  handleSizeChange: changeBoxSize,
} = useCrudList<ArchiveBoxGroup, { keyword: string }>({
  defaultQuery: { keyword: '' },
  defaultPageSize: 100,
  immediate: false,
  session: { ...session, onReset: () => { boxesError.value = '' } },
  fetchApi: async (params, { isCurrent }) => {
    if (isCurrent()) { boxesError.value = '' }
    try {
      return await getArchiveBoxGroups({ page: params.page, size: params.size, ...(params.keyword.trim() && { keyword: params.keyword.trim() }) })
    }
    catch (cause: unknown) {
      if (isCurrent()) { boxesError.value = cause instanceof Error ? cause.message : '箱号汇总加载失败' }
      return { list: [], total: 0, page: params.page, size: params.size }
    }
  },
})
const boxKeyword = toRef(boxQuery, 'keyword')
const loading = computed(() => activeView.value === 'records' ? recordsLoading.value : boxesLoading.value)
const error = computed(() => activeView.value === 'records' ? recordsError.value : boxesError.value)
let summaryGeneration = 0
let disposed = false
let operationGeneration = 0
let viewGeneration = 0
const deleting = ref(false)

const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number>()
const form = reactive<ArchiveBoxRecordPayload>({
  bah: '',
  sjh: '',
  boxNo: '',
  expectedBoxNo: '',
  status: 'NORMAL',
  remark: '',
})

const recordActions: MrrTableAction[] = [
  {
    key: 'edit',
    label: '编辑装箱记录',
    icon: 'i-lucide:pencil',
    tone: 'primary',
    placement: 'inline',
  },
  {
    key: 'delete',
    label: '删除装箱记录',
    icon: 'i-lucide:trash-2',
    tone: 'danger',
  },
]
const availableRecordActions = computed(() => recordActions.map(action => ({ ...action, disabled: submitting.value || deleting.value })))
const boxActions: MrrTableAction[] = [
  {
    key: 'view',
    label: '查看箱内病案',
    icon: 'i-lucide:folder-open',
    tone: 'primary',
    placement: 'inline',
  },
]
const {
  maxInlineActions: recordMaxInlineActions,
  actionColumnWidth: recordActionColumnWidth,
} = useTableActionLayout(recordActions.length, 2)
const {
  maxInlineActions: boxMaxInlineActions,
  actionColumnWidth: boxActionColumnWidth,
} = useTableActionLayout(boxActions.length, 1)

const dialogTitle = computed(() => editingId.value ? '编辑装箱记录' : '新增装箱记录')
const isMissing = computed(() => form.status === 'MISSING')

interface MetricCard {
  label: string
  value: number
  note?: string
  icon: string
  tone: MetricTone
  valueTone?: MetricValueTone
}

const metricCards = computed<MetricCard[]>(() => summary.value
  ? [
      {
        label: '装箱病案',
        value: summary.value.totalRecords,
        icon: 'i-lucide:folder-archive',
        tone: 'blue',
      },
      {
        label: '已用箱数',
        value: summary.value.totalBoxes,
        icon: 'i-lucide:package',
        tone: 'green',
      },
      {
        label: '缺失病案',
        value: summary.value.missingCount,
        icon: 'i-lucide:file-question',
        tone: 'danger',
        valueTone: summary.value.missingCount > 0 ? 'danger' : 'default',
      },
      {
        label: '异常记录',
        value: summary.value.abnormalCount,
        icon: 'i-lucide:triangle-alert',
        note: '缺失、错箱、冲突及其他异常',
        tone: 'amber',
        valueTone: summary.value.abnormalCount > 0 ? 'warning' : 'default',
      },
    ]
  : [])

/* 空库/空筛态下数值指标全零时收纳指标区，为表格让出首屏（N5）。 */
const hasMetricActivity = computed(() => metricCards.value.some(card => Number(card.value) > 0))
const resultCount = computed(() => activeView.value === 'records' ? total.value : boxTotal.value)

function statusMeta(status?: string) {
  const option = statusOptions.find(item => item.value === status)
  return {
    label: option?.label ?? (status || '未知'),
    tone: option?.tone ?? 'neutral',
  }
}

function normalizeText(value?: string) {
  return value?.trim() || '-'
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 19)
}

async function loadSummary() {
  if (disposed || !canRead.value) { return }
  const token = ++summaryGeneration
  const current = () => !disposed && token === summaryGeneration && canRead.value
  summaryError.value = ''
  try {
    const response = await getArchiveBoxSummary()
    if (current()) { summary.value = response.data }
  }
  catch (cause: unknown) {
    if (current()) {
      summary.value = undefined
      summaryError.value = cause instanceof Error ? cause.message : '装箱概况加载失败'
    }
  }
}

async function refreshAll() {
  await Promise.all([
    loadSummary(),
    activeView.value === 'records' ? loadRecords() : loadBoxes(),
  ])
}

function openBox(boxNo: string) {
  if (!canRead.value) { return }
  activeView.value = 'records'
  filters.boxNo = boxNo
  void handleSearch()
}

function resetForm() {
  editingId.value = undefined
  Object.assign(form, {
    bah: '',
    sjh: '',
    boxNo: '',
    expectedBoxNo: '',
    status: 'NORMAL',
    remark: '',
  } satisfies ArchiveBoxRecordPayload)
}

function openCreate() {
  if (!canEdit.value || disposed || submitting.value || deleting.value) { return }
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: ArchiveBoxRecord) {
  if (!canEdit.value || disposed || submitting.value || deleting.value || !row.id) { return }
  editingId.value = row.id
  Object.assign(form, {
    bah: row.bah ?? '',
    sjh: row.sjh ?? '',
    boxNo: row.boxNo ?? '',
    expectedBoxNo: row.expectedBoxNo ?? '',
    status: row.status,
    remark: row.remark ?? '',
  } satisfies ArchiveBoxRecordPayload)
  dialogVisible.value = true
}

function buildPayload(): ArchiveBoxRecordPayload | null {
  const bah = form.bah?.trim()
  const sjh = form.sjh?.trim()
  const boxNo = form.boxNo?.trim()

  if (!bah && !sjh) {
    ElMessage.warning('病案号和上架号至少填写一项')
    return null
  }
  if (form.status !== 'MISSING' && !boxNo) {
    ElMessage.warning('非缺失状态必须填写实际箱号')
    return null
  }

  return {
    bah: bah || undefined,
    sjh: sjh || undefined,
    boxNo: form.status === 'MISSING' ? undefined : boxNo || undefined,
    expectedBoxNo: form.expectedBoxNo?.trim() || undefined,
    status: form.status,
    remark: form.remark?.trim() || undefined,
  }
}

async function submitForm() {
  if (!canEdit.value || disposed || !dialogVisible.value || submitting.value || deleting.value) { return }
  const payload = buildPayload()
  if (!payload) { return }
  const id = editingId.value
  const token = operationGeneration
  const current = () => !disposed && canEdit.value && operationGeneration === token
  submitting.value = true
  try {
    if (id) { await updateArchiveBoxRecord(id, payload) }
    else { await createArchiveBoxRecord(payload) }
    if (!current()) { return }
    ElMessage.success(id ? '装箱记录已更新' : '装箱记录已新增')
    dialogVisible.value = false
    await refreshAll()
  }
  catch {
    // API layer reports current failures; leave the form available for retry.
  }
  finally {
    if (current()) { submitting.value = false }
  }
}

async function removeRecord(row: ArchiveBoxRecord) {
  const id = row.id
  if (!id || !canEdit.value || disposed || deleting.value || submitting.value || loading.value || activeView.value !== 'records') { return }
  const token = operationGeneration
  const view = viewGeneration
  const version = recordVersion.value
  const current = () => !disposed && canEdit.value && operationGeneration === token
  deleting.value = true
  try {
    await actionDialog.confirm(
      `确定删除病案号 ${normalizeText(row.bah)}、上架号 ${normalizeText(row.sjh)} 的装箱记录吗？`,
      '删除装箱记录',
      { confirmButtonText: '删除', confirmButtonType: 'danger', cancelButtonText: '取消', type: 'warning' },
    )
    if (!current()) { return }
    if (view !== viewGeneration || version !== recordVersion.value || recordsLoading.value || !records.value.some(record => record.id === id)) {
      ElMessage.warning('装箱列表已变化，请重新确认删除对象')
      return
    }
    await deleteArchiveBoxRecord(id)
    if (!current()) { return }
    ElMessage.success('装箱记录已删除')
    await refreshAll()
  }
  catch {
    // Cancellation or an API failure must not be reported as success.
  }
  finally {
    if (current()) { deleting.value = false }
  }
}

function handleRecordAction(action: string, row: ArchiveBoxRecord) {
  if (action === 'edit') {
    openEdit(row)
  }
  else if (action === 'delete') {
    void removeRecord(row)
  }
}

function handleBoxAction(action: string, row: ArchiveBoxGroup) {
  if (action === 'view') {
    openBox(row.boxNo)
  }
}

function handleViewChange(value: string | number | boolean | undefined) {
  const view: ViewMode = value === 'boxes' ? 'boxes' : 'records'
  activeView.value = view
  if (view === 'records') {
    void loadRecords()
  }
  else {
    void loadBoxes()
  }
}

function handleRecordSizeChange() {
  void changeRecordSize(size.value)
}

function handleBoxSizeChange() {
  void changeBoxSize(boxSize.value)
}

watch(dialogVisible, (visible) => { if (!visible) { resetForm() } }, { flush: 'sync' })
watch(activeView, () => { viewGeneration++ }, { flush: 'sync' })
function invalidateWrites() {
  operationGeneration++
  submitting.value = false
  deleting.value = false
  dialogVisible.value = false
  resetForm()
}
watch([session.key, canRead], () => {
  summaryGeneration++
  summary.value = undefined
  summaryError.value = ''
  invalidateWrites()
}, { flush: 'sync' })
watch(canEdit, invalidateWrites, { flush: 'sync' })
watch([session.key, canRead], ([, allowed]) => {
  if (allowed) { void refreshAll() }
})
onScopeDispose(() => {
  disposed = true
  summaryGeneration++
  invalidateWrites()
})

onMounted(refreshAll)
</script>

<template>
  <MrrPageShell compact>
    <MrrPageHeader
      title="档案装箱管理"
      description="维护实体病案与箱号的对应关系，支持按病案反查位置和按箱号查看箱内病案。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="refreshAll">
          刷新
        </el-button>
        <el-button v-if="canEdit" type="primary" :icon="Plus" :disabled="submitting || deleting" @click="openCreate">
          新增装箱记录
        </el-button>
      </template>
    </MrrPageHeader>

    <AppError v-if="summaryError" size="compact" :message="summaryError" @retry="loadSummary" />
    <section v-else-if="hasMetricActivity" class="mrr-metric-grid mrr-metric-grid--four mrr-metric-grid--dense mrr-metric-grid--mobile-compact" aria-label="装箱概况">
      <MrrMetricCard
        v-for="card in metricCards"
        :key="card.label"
        v-bind="card"
      />
    </section>

    <MrrDataTablePanel
      :title="activeView === 'records' ? '装箱明细' : '箱号列表'"
      :count="!loading && !error && !resultCount ? 0 : undefined"
      :loading="loading"
      :result-key="activeView === 'records' ? records : boxes"
    >
      <template #filters>
        <div class="view-switcher">
          <el-radio-group
            :model-value="activeView"
            aria-label="装箱查看方式"
            @update:model-value="handleViewChange"
          >
            <el-radio-button value="records">
              装箱明细
            </el-radio-button>
            <el-radio-button value="boxes">
              箱号视图
            </el-radio-button>
          </el-radio-group>
          <span class="view-hint">
            {{ activeView === 'records' ? '一条记录对应一份实体病案' : '点击箱号可查看箱内全部病案' }}
          </span>
        </div>

        <MrrFilterBar embedded :focus-ring="false" class="archive-filter-bar">
          <template v-if="activeView === 'records'">
            <div class="mrr-filter-field archive-filter__keyword mrr-filter-field--keyword">
              <el-input id="archive-keyword" v-model="filters.keyword" clearable aria-label="综合搜索" placeholder="编号、箱号、状态或备注" @keyup.enter="handleSearch">
                <template #prefix>
                  <FaIcon name="i-lucide:search" aria-hidden="true" />
                </template>
              </el-input>
            </div>
            <div class="mrr-filter-field archive-filter__field mrr-filter-field--code">
              <el-input id="archive-bah" v-model="filters.bah" clearable aria-label="病案号" @keyup.enter="handleSearch">
                <template #prefix>
                  <label for="archive-bah" class="mrr-filter-prefix">病案号</label>
                </template>
              </el-input>
            </div>
            <div class="mrr-filter-field archive-filter__field mrr-filter-field--code">
              <el-input id="archive-sjh" v-model="filters.sjh" clearable aria-label="上架号" @keyup.enter="handleSearch">
                <template #prefix>
                  <label for="archive-sjh" class="mrr-filter-prefix">上架号</label>
                </template>
              </el-input>
            </div>
            <div class="mrr-filter-field archive-filter__field mrr-filter-field--code">
              <el-input id="archive-box-number" v-model="filters.boxNo" clearable aria-label="实际箱号" @keyup.enter="handleSearch">
                <template #prefix>
                  <label for="archive-box-number" class="mrr-filter-prefix">实际箱号</label>
                </template>
              </el-input>
            </div>
            <div class="mrr-filter-field archive-filter__field">
              <el-select id="archive-status" v-model="filters.status" class="mrr-single-select" clearable aria-label="装箱状态" placeholder="全部状态">
                <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </div>
          </template>
          <div v-else class="mrr-filter-field archive-filter__keyword mrr-filter-field--keyword">
            <el-input id="archive-box-keyword" v-model="boxKeyword" clearable aria-label="搜索箱号" placeholder="搜索箱号" @keyup.enter="searchBoxes">
              <template #prefix>
                <FaIcon name="i-lucide:search" aria-hidden="true" />
              </template>
            </el-input>
          </div>
          <template #actions>
            <MrrFilterActionButton action="search" :pending="loading" :busy="loading" @click="activeView === 'records' ? handleSearch() : searchBoxes()">
              查询
            </MrrFilterActionButton>
            <MrrFilterActionButton action="reset" :pending="loading" :busy="loading" @click="activeView === 'records' ? resetFilters() : resetBoxSearch()">
              重置
            </MrrFilterActionButton>
          </template>
        </MrrFilterBar>
      </template>

      <AppLoading v-if="loading && !(activeView === 'records' ? records.length : boxes.length)" type="table" :rows="8" />
      <AppError v-else-if="error" :message="error" @retry="activeView === 'records' ? loadRecords() : loadBoxes()" />
      <template v-else-if="activeView === 'records'">
        <AppEmpty v-if="!records.length" description="暂无符合条件的装箱记录" />
        <el-table v-else :data="records" stripe>
          <el-table-column v-bind="tableSequenceColumn(recordRowOffset, records.length)" />
          <el-table-column label="病案号" width="124" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="record-code">{{ normalizeText(row.bah) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="上架号" width="124" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="record-code">{{ normalizeText(row.sjh) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="实际箱号" min-width="144">
            <template #default="{ row }">
              <el-button
                v-if="row.boxNo"
                link
                type="primary"
                class="box-link mrr-table-text-link"
                :title="row.boxNo"
                @click="openBox(row.boxNo)"
              >
                {{ row.boxNo }}
              </el-button>
              <span v-else class="empty-value">未确认</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="140" class-name="mrr-table-status-column">
            <template #default="{ row }">
              <MrrStatusTag v-bind="statusMeta(row.status)" />
            </template>
          </el-table-column>
          <el-table-column label="原计划箱号" width="144" show-overflow-tooltip>
            <template #default="{ row }">
              {{ normalizeText(row.expectedBoxNo) }}
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              {{ normalizeText(row.remark) }}
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="176">
            <template #default="{ row }">
              {{ formatDateTime(row.updatedAt) }}
            </template>
          </el-table-column>
          <el-table-column
            v-if="canEdit"
            label="操作"
            :width="recordActionColumnWidth"
            fixed="right"
            align="center"
          >
            <template #default="{ row }">
              <MrrTableActions
                :actions="availableRecordActions"
                :max-inline="recordMaxInlineActions"
                @select="handleRecordAction($event, row as ArchiveBoxRecord)"
              />
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template v-else>
        <AppEmpty v-if="!boxes.length" description="暂无箱号记录" />
        <el-table v-else :data="boxes" stripe>
          <el-table-column v-bind="tableSequenceColumn(boxRowOffset, boxes.length)" />
          <el-table-column label="箱号" min-width="220">
            <template #default="{ row }">
              <el-button link :auto-insert-space="false" class="box-link mrr-inline-action mrr-table-text-link" :title="row.boxNo" @click="openBox(row.boxNo)">
                <span class="i-lucide:inbox" aria-hidden="true" />
                {{ row.boxNo }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column prop="recordCount" label="箱内病案" width="112" />
          <el-table-column label="异常记录" width="112" class-name="mrr-table-status-column">
            <template #default="{ row }">
              <el-tag :type="row.abnormalCount ? 'warning' : 'success'" effect="light">
                {{ row.abnormalCount }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最近更新" width="176">
            <template #default="{ row }">
              {{ formatDateTime(row.updatedAt) }}
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            :width="boxActionColumnWidth"
            fixed="right"
            align="center"
          >
            <template #default="{ row }">
              <MrrTableActions
                :actions="boxActions"
                :max-inline="boxMaxInlineActions"
                @select="handleBoxAction($event, row as ArchiveBoxGroup)"
              />
            </template>
          </el-table-column>
        </el-table>
      </template>

      <template v-if="resultCount > 0 && !error" #pagination>
        <el-pagination
          v-if="activeView === 'records'"
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, prev, pager, next, jumper, sizes"
          @current-change="changeRecordPage"
          @size-change="handleRecordSizeChange"
        />
        <el-pagination
          v-else
          v-model:current-page="boxPage"
          v-model:page-size="boxSize"
          :total="boxTotal"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, prev, pager, next, jumper, sizes"
          @current-change="changeBoxPage"
          @size-change="handleBoxSizeChange"
        />
      </template>
    </MrrDataTablePanel>

    <el-dialog
      v-model="dialogVisible"
      align-center
      :title="dialogTitle"
      width="min(620px, calc(100% - 24px))"
      destroy-on-close
      :close-on-click-modal="!submitting"
      :close-on-press-escape="!submitting"
      :show-close="!submitting"
    >
      <el-form :disabled="submitting" label-position="top" class="mrr-edit-form mrr-edit-form--paired">
        <el-form-item label="病案号">
          <el-input v-model="form.bah" clearable placeholder="可与上架号任选其一" />
        </el-form-item>
        <el-form-item label="上架号">
          <el-input v-model="form.sjh" clearable placeholder="纯数字不足 8 位自动补零" />
        </el-form-item>
        <el-form-item label="实际箱号" :required="!isMissing">
          <el-input
            v-model="form.boxNo"
            clearable
            :disabled="isMissing"
            placeholder="病案当前实际存放箱号"
          />
        </el-form-item>
        <el-form-item label="原计划箱号">
          <el-input v-model="form.expectedBoxNo" clearable placeholder="异常记录建议填写" />
        </el-form-item>
        <el-form-item label="装箱状态" class="mrr-edit-form__full">
          <el-select v-model="form.status">
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" class="mrr-edit-form__full">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="记录错箱、冲突、查找过程或其他说明"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="dialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">
          保存
        </el-button>
      </template>
    </el-dialog>
  </MrrPageShell>
</template>

<style scoped>
.view-switcher {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2) var(--mrr-space-3);
  align-items: center;
  padding-bottom: var(--mrr-space-3);
  margin-bottom: var(--mrr-space-3);

  /* 视图切换是筛选的上一级（决定下面显示哪组字段），用分隔线把两层分开。 */
  border-bottom: 1px solid var(--mrr-shell-divider);
}

.view-switcher :deep(.el-radio-button) {
  --el-radio-button-checked-text-color: var(--mrr-primary-foreground);
}

.view-hint {
  font-size: var(--mrr-font-size-sm);
  color: var(--mrr-muted-foreground);
}

.record-code,
.box-link {
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
}

/*
 * 「未确认」原为危险红：它表示尚待确认存放位置，是待办而非错误，
 * 与指标卡「尚未确认实际存放位置」同口径改为警示色；文字仍保留，不只靠颜色。
 */
.empty-value {
  font-size: var(--mrr-font-size-sm);
  color: var(--mrr-tone-warning);
}

.box-link {
  --mrr-inline-action-weight: 600;

  padding: 0;
  font-weight: 600;
}

.box-link.el-button {
  color: var(--mrr-navigation-active-foreground);
  text-decoration: underline;
  text-underline-offset: 2px;
}
</style>
