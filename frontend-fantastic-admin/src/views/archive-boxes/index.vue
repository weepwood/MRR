<script setup lang="ts">
import type {
  ArchiveBoxGroup,
  ArchiveBoxQuery,
  ArchiveBoxRecord,
  ArchiveBoxRecordPayload,
  ArchiveBoxStatus,
  ArchiveBoxSummary,
} from '@/api/modules/archive-boxes'
import type { MetricTone, MetricValueTone } from '@/components/MrrMetricCard/types'
import type { MrrTableAction } from '@/components/MrrTableActions/types'
import { ElMessage } from 'element-plus'
import { Plus, RefreshCw as Refresh, Search } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
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
import { useTableActionLayout } from '@/composables/useTableActionLayout'
import { actionDialog } from '@/ui/components/ActionDialog'

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
const loading = ref(false)
const error = ref('')
const records = ref<ArchiveBoxRecord[]>([])
const boxes = ref<ArchiveBoxGroup[]>([])
const summary = ref<ArchiveBoxSummary>({
  totalRecords: 0,
  totalBoxes: 0,
  missingCount: 0,
  abnormalCount: 0,
})

const page = ref(1)
const size = ref(100)
const total = ref(0)
const boxPage = ref(1)
const boxSize = ref(100)
const boxTotal = ref(0)
const boxKeyword = ref('')

const filters = reactive({
  keyword: '',
  bah: '',
  sjh: '',
  boxNo: '',
  status: '' as ArchiveBoxStatus | '',
})

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
  note: string
  tone: MetricTone
  valueTone?: MetricValueTone
}

const metricCards = computed<MetricCard[]>(() => [
  {
    label: '装箱病案',
    value: summary.value.totalRecords,
    note: '已登记实体病案数量',
    tone: 'blue',
  },
  {
    label: '已用箱数',
    value: summary.value.totalBoxes,
    note: '存在实际存放记录的箱号',
    tone: 'green',
  },
  {
    label: '缺失病案',
    value: summary.value.missingCount,
    note: '尚未确认实际存放位置',
    tone: 'danger',
    valueTone: summary.value.missingCount > 0 ? 'danger' : 'default',
  },
  {
    label: '异常记录',
    value: summary.value.abnormalCount,
    note: '缺失、错箱、冲突及其他异常',
    tone: 'amber',
    valueTone: summary.value.abnormalCount > 0 ? 'warning' : 'default',
  },
])

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

function buildQuery(): ArchiveBoxQuery {
  return {
    page: page.value,
    size: size.value,
    sortBy: 'updatedAt',
    sortOrder: 'desc',
    ...(filters.keyword.trim() && { keyword: filters.keyword.trim() }),
    ...(filters.bah.trim() && { bah: filters.bah.trim() }),
    ...(filters.sjh.trim() && { sjh: filters.sjh.trim() }),
    ...(filters.boxNo.trim() && { boxNo: filters.boxNo.trim() }),
    ...(filters.status && { status: filters.status }),
  }
}

async function loadSummary() {
  const response = await getArchiveBoxSummary()
  summary.value = response.data ?? {
    totalRecords: 0,
    totalBoxes: 0,
    missingCount: 0,
    abnormalCount: 0,
  }
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const response = await getArchiveBoxRecords(buildQuery())
    const payload = response.data
    records.value = Array.isArray(payload?.list) ? payload.list : []
    total.value = Number(payload?.total || 0)
  }
  catch (cause: unknown) {
    records.value = []
    total.value = 0
    error.value = cause instanceof Error ? cause.message : '装箱明细加载失败'
  }
  finally {
    loading.value = false
  }
}

async function loadBoxes() {
  loading.value = true
  error.value = ''
  try {
    const response = await getArchiveBoxGroups({
      page: boxPage.value,
      size: boxSize.value,
      ...(boxKeyword.value.trim() && { keyword: boxKeyword.value.trim() }),
    })
    const payload = response.data
    boxes.value = Array.isArray(payload?.list) ? payload.list : []
    boxTotal.value = Number(payload?.total || 0)
  }
  catch (cause: unknown) {
    boxes.value = []
    boxTotal.value = 0
    error.value = cause instanceof Error ? cause.message : '箱号汇总加载失败'
  }
  finally {
    loading.value = false
  }
}

async function refreshAll() {
  await Promise.all([
    loadSummary(),
    activeView.value === 'records' ? loadRecords() : loadBoxes(),
  ])
}

function handleSearch() {
  page.value = 1
  void loadRecords()
}

function resetFilters() {
  filters.keyword = ''
  filters.bah = ''
  filters.sjh = ''
  filters.boxNo = ''
  filters.status = ''
  handleSearch()
}

function searchBoxes() {
  boxPage.value = 1
  void loadBoxes()
}

function openBox(boxNo: string) {
  activeView.value = 'records'
  filters.boxNo = boxNo
  page.value = 1
  void loadRecords()
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
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: ArchiveBoxRecord) {
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
  const payload = buildPayload()
  if (!payload) {
    return
  }

  submitting.value = true
  try {
    if (editingId.value) {
      await updateArchiveBoxRecord(editingId.value, payload)
      ElMessage.success('装箱记录已更新')
    }
    else {
      await createArchiveBoxRecord(payload)
      ElMessage.success('装箱记录已新增')
    }
    dialogVisible.value = false
    await refreshAll()
  }
  finally {
    submitting.value = false
  }
}

async function removeRecord(row: ArchiveBoxRecord) {
  if (!row.id) {
    return
  }

  try {
    await actionDialog.confirm(
      `确定删除上架号 ${normalizeText(row.sjh)} 的装箱记录吗？`,
      '删除装箱记录',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  }
  catch {
    return
  }

  await deleteArchiveBoxRecord(row.id)
  ElMessage.success('装箱记录已删除')
  await refreshAll()
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
  error.value = ''
  if (view === 'records') {
    void loadRecords()
  }
  else {
    void loadBoxes()
  }
}

function handleRecordSizeChange() {
  page.value = 1
  void loadRecords()
}

function handleBoxSizeChange() {
  boxPage.value = 1
  void loadBoxes()
}

function resetBoxSearch() {
  boxKeyword.value = ''
  searchBoxes()
}

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
        <el-button type="primary" :icon="Plus" @click="openCreate">
          新增装箱记录
        </el-button>
      </template>
    </MrrPageHeader>

    <section v-if="hasMetricActivity" class="mrr-metric-grid mrr-metric-grid--four mrr-metric-grid--dense" aria-label="装箱概况">
      <MrrMetricCard
        v-for="card in metricCards"
        :key="card.label"
        v-bind="card"
      />
    </section>

    <MrrDataTablePanel
      :title="activeView === 'records' ? '装箱明细' : '箱号列表'"
      :count="loading || error ? undefined : resultCount"
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

        <MrrFilterBar embedded class="mrr-filter-bar--labelled archive-filter-bar">
          <template v-if="activeView === 'records'">
            <div class="mrr-filter-field archive-filter__keyword">
              <label for="archive-keyword">综合搜索</label>
              <el-input id="archive-keyword" v-model="filters.keyword" clearable aria-label="综合搜索" placeholder="编号、箱号、状态或备注" @keyup.enter="handleSearch">
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </div>
            <div class="mrr-filter-field archive-filter__field">
              <label for="archive-bah">病案号</label>
              <el-input id="archive-bah" v-model="filters.bah" clearable aria-label="病案号" @keyup.enter="handleSearch" />
            </div>
            <div class="mrr-filter-field archive-filter__field">
              <label for="archive-sjh">上架号</label>
              <el-input id="archive-sjh" v-model="filters.sjh" clearable aria-label="上架号" @keyup.enter="handleSearch" />
            </div>
            <div class="mrr-filter-field archive-filter__field">
              <label for="archive-box-number">实际箱号</label>
              <el-input id="archive-box-number" v-model="filters.boxNo" clearable aria-label="实际箱号" @keyup.enter="handleSearch" />
            </div>
            <div class="mrr-filter-field archive-filter__field">
              <label for="archive-status">装箱状态</label>
              <el-select id="archive-status" v-model="filters.status" clearable aria-label="装箱状态" placeholder="全部状态">
                <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </div>
          </template>
          <div v-else class="mrr-filter-field archive-filter__keyword">
            <label for="archive-box-keyword">搜索箱号</label>
            <el-input id="archive-box-keyword" v-model="boxKeyword" clearable aria-label="搜索箱号" @keyup.enter="searchBoxes">
              <template #prefix>
                <el-icon><Search /></el-icon>
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

      <AppLoading v-if="loading" type="table" :rows="8" />
      <AppError v-else-if="error" :message="error" @retry="activeView === 'records' ? loadRecords() : loadBoxes()" />
      <template v-else-if="activeView === 'records'">
        <AppEmpty v-if="!records.length" description="暂无符合条件的装箱记录" />
        <el-table v-else :data="records" stripe>
          <el-table-column label="病案号" min-width="110">
            <template #default="{ row }">
              <span class="record-code">{{ normalizeText(row.bah) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="上架号" min-width="110">
            <template #default="{ row }">
              <span class="record-code">{{ normalizeText(row.sjh) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="实际箱号" min-width="135">
            <template #default="{ row }">
              <el-button
                v-if="row.boxNo"
                link
                type="primary"
                class="box-link"
                @click="openBox(row.boxNo)"
              >
                {{ row.boxNo }}
              </el-button>
              <span v-else class="empty-value">未确认</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="145">
            <template #default="{ row }">
              <MrrStatusTag v-bind="statusMeta(row.status)" />
            </template>
          </el-table-column>
          <el-table-column label="原计划箱号" min-width="135">
            <template #default="{ row }">
              {{ normalizeText(row.expectedBoxNo) }}
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="185" show-overflow-tooltip>
            <template #default="{ row }">
              {{ normalizeText(row.remark) }}
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.updatedAt) }}
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            :width="recordActionColumnWidth"
            fixed="right"
            align="center"
          >
            <template #default="{ row }">
              <MrrTableActions
                :actions="recordActions"
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
          <el-table-column label="箱号" min-width="220">
            <template #default="{ row }">
              <button class="box-number-button" type="button" @click="openBox(row.boxNo)">
                <span class="i-lucide:inbox" aria-hidden="true" />
                {{ row.boxNo }}
              </button>
            </template>
          </el-table-column>
          <el-table-column prop="recordCount" label="箱内病案" width="140" />
          <el-table-column label="异常记录" width="140">
            <template #default="{ row }">
              <el-tag :type="row.abnormalCount ? 'warning' : 'success'" effect="light">
                {{ row.abnormalCount }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最近更新" width="190">
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
          layout="sizes, prev, pager, next, jumper"
          @current-change="loadRecords"
          @size-change="handleRecordSizeChange"
        />
        <el-pagination
          v-else
          v-model:current-page="boxPage"
          v-model:page-size="boxSize"
          :total="boxTotal"
          :page-sizes="[10, 20, 50, 100]"
          layout="sizes, prev, pager, next, jumper"
          @current-change="loadBoxes"
          @size-change="handleBoxSizeChange"
        />
      </template>
    </MrrDataTablePanel>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="min(620px, calc(100% - 24px))"
      top="min(15vh, 6rem)"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form label-position="top" class="record-form">
        <div class="form-grid">
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
          <el-form-item label="装箱状态" class="form-span">
            <el-select v-model="form.status">
              <el-option
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="备注" class="form-span">
            <el-input
              v-model="form.remark"
              type="textarea"
              :rows="4"
              maxlength="1000"
              show-word-limit
              placeholder="记录错箱、冲突、查找过程或其他说明"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">
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
  margin-bottom: var(--mrr-space-3);
}

.view-switcher :deep(.el-radio-button) {
  --el-radio-button-checked-text-color: var(--mrr-primary-foreground);
}

.view-hint {
  font-size: 13px;
  color: var(--mrr-muted-foreground);
}

.archive-filter__field {
  flex: 1 1 116px;
}

.archive-filter__keyword {
  flex: 2 1 204px;
}

.record-code,
.box-link,
.box-number-button {
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
}

.empty-value {
  font-size: 13px;
  color: var(--el-color-danger);
}

.box-link {
  padding: 0;
  font-weight: 700;
}

.box-number-button {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 0;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.box-link.el-button,
.box-number-button {
  color: var(--mrr-navigation-active-foreground);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.box-number-button:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 3px;
  border-radius: 4px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--mrr-space-4);
}

.form-span {
  grid-column: 1 / -1;
}

.record-form :deep(.el-select) {
  width: 100%;
}

@media (max-width: 760px) {
  .archive-filter__keyword {
    flex: 1 1 100%;
  }
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
