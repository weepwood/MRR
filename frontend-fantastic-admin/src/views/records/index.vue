<script setup lang="ts">
import type { ScanRecord } from '@/api/types'
import type { MetricTone } from '@/components/MrrMetricCard/types'
import type { MrrTableAction } from '@/components/MrrTableActions/types'
import { Download } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { batchDownloadRecords, getScanCursor } from '@/api/modules/records'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import MrrDataTablePanel from '@/components/MrrDataTablePanel/index.vue'
import MrrFilterActionButton from '@/components/MrrFilterActionButton/index.vue'
import MrrFilterBar from '@/components/MrrFilterBar/index.vue'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import MrrTableActions from '@/components/MrrTableActions/index.vue'
import { useTableActionLayout } from '@/composables/useTableActionLayout'
import { getMedicalRecordTypeLabel, MEDICAL_RECORD_TYPES } from '@/constants/medical-record-types'
import { notify } from '@/utils/notification'

defineOptions({ name: 'RecordsPage' })

const migrationStatusMap: Record<string, { label: string, type: string }> = {
  not_migrated: { label: '未迁移', type: 'info' },
  migrated: { label: '已迁移', type: 'success' },
  verified: { label: '已验证', type: 'success' },
}

// 业务状态（非 CRUD 通用部分）
const downloading = ref(false)
const detailVisible = ref(false)
const currentRecord = ref<ScanRecord | null>(null)
const selectedRows = ref<ScanRecord[]>([])

const error = ref('')

const typeOptions = [
  { label: '全部类型', value: '' },
  ...MEDICAL_RECORD_TYPES.map(item => ({
    label: item.label,
    value: String(item.value),
  })),
]

const recordActions: MrrTableAction[] = [
  {
    key: 'detail',
    label: '查看详情',
    icon: 'i-lucide:eye',
    tone: 'primary',
    placement: 'inline',
  },
]
const {
  maxInlineActions: recordMaxInlineActions,
  actionColumnWidth: recordActionColumnWidth,
} = useTableActionLayout(recordActions.length, 1)

// 大表按稳定 ID 游标读取，显示已加载范围，不把估算当精确总数。
const list = ref<ScanRecord[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const query = reactive({ bah: '', brxh: '', sjh: '', openerNo: '', btype: '' })
const appliedQuery = ref({ ...query })
const unapplied = computed(() => JSON.stringify(query) !== JSON.stringify(appliedQuery.value))
const appliedSummary = computed(() => {
  const fields = [
    ['病案号', appliedQuery.value.bah.trim()],
    ['病人序号', appliedQuery.value.brxh.trim()],
    ['上架号', appliedQuery.value.sjh.trim()],
    ['扫描人员', appliedQuery.value.openerNo.trim()],
    ['类型', appliedQuery.value.btype ? typeLabel(appliedQuery.value.btype) : ''],
  ]
  return fields.filter(([, value]) => value).map(([label, value]) => `${label}：${value}`).join('；') || '全部记录'
})
const cursors = ref<number[]>([0])
const nextCursor = ref<number | undefined>()
const hasMore = ref(false)
let version = 0
async function loadData() {
  const current = ++version
  loading.value = true
  list.value = []
  selectedRows.value = []
  error.value = ''
  try {
    const submitted = appliedQuery.value
    const res = await getScanCursor({ bah: submitted.bah.trim() || undefined, sjh: submitted.sjh.trim() || undefined, brxh: submitted.brxh.trim() || undefined, openerNo: submitted.openerNo.trim() || undefined, btype: submitted.btype ? Number(submitted.btype) : undefined }, cursors.value[pageNum.value - 1] || 0, pageSize.value)
    if (current !== version) { return }
    if (!Array.isArray(res.data?.list)) { throw new Error('记录响应格式异常，请重试') }
    list.value = res.data.list
    selectedRows.value = []
    hasMore.value = res.data.hasMore
    nextCursor.value = res.data.nextCursorId
  }
  catch (exception) {
    if (current === version) { error.value = exception instanceof Error ? exception.message : '记录读取失败'; list.value = []; hasMore.value = false }
  }
  finally {
    if (current === version) { loading.value = false }
  }
}
function reloadFirstBatch() {
  pageNum.value = 1
  cursors.value = [0]
  hasMore.value = false
  void loadData()
}
function handleSearch() { appliedQuery.value = { ...query }; reloadFirstBatch() }
function resetFilters() { Object.assign(query, { bah: '', brxh: '', sjh: '', openerNo: '', btype: '' }); handleSearch() }
function nextPage() {
  if (!hasMore.value || nextCursor.value == null) { return } cursors.value[pageNum.value] = nextCursor.value; pageNum.value++; void loadData()
}
function previousPage() {
  if (pageNum.value <= 1) { return } pageNum.value--; void loadData()
}
onMounted(loadData)

const summaryCards = computed(() => [
  {
    label: '当前批记录数',
    value: list.value.length,
    note: '当前筛选结果中已加载的数据条数',
    tone: 'blue' as MetricTone,
  },
  {
    // 「读取方式：游标」是常量枚举，占指标卡位会稀释真指标的信噪比；
    // 读取方式说明保留在筛选栏下方提示文案中。
    label: '已选记录',
    value: selectedRows.value.length,
    note: '可用于批量打包下载',
    tone: 'violet' as MetricTone,
  },
  {
    label: '当前批次',
    value: pageNum.value,
    note: `每批 ${pageSize.value} 条`,
    tone: 'amber' as MetricTone,
  },
])

function handleSelectionChange(rows: ScanRecord[]) {
  selectedRows.value = rows
}

function openDetail(row: ScanRecord) {
  currentRecord.value = row
  detailVisible.value = true
}

function handleRecordAction(action: string, row: ScanRecord) {
  if (action === 'detail') {
    openDetail(row)
  }
}

async function handleBatchDownload() {
  if (!selectedRows.value.length) {
    notify.warning('请先选择要下载的记录')
    return
  }

  downloading.value = true
  try {
    const result = await batchDownloadRecords(selectedRows.value.map(row => row.id!))
    const blob = result instanceof Blob ? result : result?.data
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `scan-batch-${Date.now()}.zip`
    link.click()
    URL.revokeObjectURL(url)
    notify.success('批量下载已开始')
  }
  catch (error: any) {
    notify.error(error?.message || '批量下载失败')
  }
  finally {
    downloading.value = false
  }
}

function typeLabel(value: unknown) {
  return value ? getMedicalRecordTypeLabel(value as number | string) : '-'
}
</script>

<template>
  <MrrPageShell class="records-page">
    <MrrPageHeader
      title="记录管理"
      eyebrow="Scan Records"
      description="按完整编号检索扫描记录，采用游标读取；病案号、上架号兼容原始前导零。"
    >
      <template #actions>
        <el-button type="primary" :loading="downloading" @click="handleBatchDownload">
          <el-icon><Download /></el-icon>
          批量下载
        </el-button>
      </template>
    </MrrPageHeader>

    <section class="mrr-metric-grid" aria-label="当前批次概况">
      <MrrMetricCard v-for="item in summaryCards" :key="item.label" v-bind="item" />
    </section>

    <MrrDataTablePanel title="扫描记录" description="按条件查询，结果按批次读取。">
      <template #filters>
        <MrrFilterBar embedded class="records-filter-bar">
          <div class="records-filter__field">
            <label for="records-bah">病案号</label>
            <el-input id="records-bah" v-model="query.bah" clearable aria-label="病案号" placeholder="病案号" @keyup.enter="handleSearch" />
          </div>
          <div class="records-filter__field">
            <label for="records-brxh">病人序号</label>
            <el-input id="records-brxh" v-model="query.brxh" clearable aria-label="病人序号" placeholder="病人序号" @keyup.enter="handleSearch" />
          </div>
          <div class="records-filter__field">
            <label for="records-sjh">上架号</label>
            <el-input id="records-sjh" v-model="query.sjh" clearable aria-label="上架号" placeholder="上架号" @keyup.enter="handleSearch" />
          </div>
          <div class="records-filter__field">
            <label for="records-opener">扫描人员</label>
            <el-input id="records-opener" v-model="query.openerNo" clearable aria-label="扫描人员" placeholder="扫描人员工号" @keyup.enter="handleSearch" />
          </div>
          <div class="records-filter__field">
            <label for="records-btype">类型</label>
            <el-select id="records-btype" v-model="query.btype" clearable aria-label="类型" placeholder="全部类型">
              <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </div>
          <template #actions>
            <MrrFilterActionButton type="primary" action="search" :pending="loading" :busy="loading" @click="handleSearch">
              查询
            </MrrFilterActionButton>
            <MrrFilterActionButton action="reset" :pending="loading" :busy="loading" @click="resetFilters">
              重置
            </MrrFilterActionButton>
          </template>
        </MrrFilterBar>
      </template>

      <template #toolbar>
        <div class="records-query-context" aria-live="polite">
          <p><strong>已提交条件：</strong>{{ appliedSummary }}</p>
          <p v-if="unapplied" class="records-query-context__pending">
            筛选条件已修改，点击“查询”后生效；翻批仍使用已提交条件。
          </p>
        </div>
      </template>

      <AppLoading v-if="loading && !list.length" type="table" :rows="8" />
      <AppError v-else-if="error" :message="error" @retry="loadData" />
      <AppEmpty v-else-if="!list.length" description="当前条件未找到扫描记录" action-text="重置条件" @action="resetFilters" />
      <el-table
        v-else
        v-loading="loading"
        :data="list"
        stripe
        class="records-table"
        @selection-change="handleSelectionChange"
      >
        <!-- 列宽全部走 min-width，由 el-table 按容器宽度分配：
             原来 6 个固定 width 列 + 5 个 min-width 列合计 1294px，
             而 1600 视口下容器只有 1244px，导致右侧「操作」列被挤出可视区，
             必须横向滚动才能点到。全部改为 min-width 后总宽自适应，不再溢出。 -->
        <el-table-column type="selection" width="48" />
        <el-table-column prop="bah" label="病案号" min-width="130" />
        <el-table-column prop="brxh" label="病人序号" min-width="110" />
        <el-table-column prop="sjh" label="上架号" min-width="110" />
        <el-table-column prop="filename" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="btype" label="类型" min-width="120">
          <template #default="{ row }">
            {{ typeLabel(row.btype) }}
          </template>
        </el-table-column>
        <el-table-column prop="pages" label="页数" min-width="72" />
        <el-table-column prop="openerNo" label="扫描人员" min-width="110" />
        <el-table-column prop="folder" label="目录" min-width="130" show-overflow-tooltip />
        <el-table-column prop="migrationStatus" label="迁移状态" min-width="96">
          <template #default="{ row }">
            <el-tag
              v-if="row.migrationStatus"
              :type="(migrationStatusMap[row.migrationStatus]?.type || 'info') as any"
              size="small"
            >
              {{ migrationStatusMap[row.migrationStatus]?.label || row.migrationStatus }}
            </el-tag>
            <span v-else class="text-muted">-</span>
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
              @select="handleRecordAction($event, row)"
            />
          </template>
        </el-table-column>
      </el-table>

      <template #pagination>
        <div class="pager">
          <el-select v-model="pageSize" aria-label="每批条数" class="records-page-size-select" @change="reloadFirstBatch">
            <el-option v-for="size in [10, 20, 50, 100]" :key="size" :value="size" :label="`${size}条/批`" />
          </el-select>
          <div class="pager__navigation">
            <el-button :disabled="pageNum <= 1 || loading" @click="previousPage">
              上一批
            </el-button>
            <span>第 {{ pageNum }} 批</span>
            <el-button :disabled="!hasMore || loading" @click="nextPage">
              下一批
            </el-button>
          </div>
        </div>
      </template>
    </MrrDataTablePanel>

    <el-dialog v-model="detailVisible" title="记录详情" width="720px" :close-on-click-modal="false">
      <el-descriptions v-if="currentRecord" :column="2" border>
        <el-descriptions-item label="病案号">
          {{ currentRecord.bah || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="病人序号">
          {{ currentRecord.brxh || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="上架号">
          {{ currentRecord.sjh || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="文件名">
          {{ currentRecord.filename || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="类型">
          {{ typeLabel(currentRecord.btype) }}
        </el-descriptions-item>
        <el-descriptions-item label="页数">
          {{ currentRecord.pages || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="扫描人员">
          {{ currentRecord.openerNo || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="上传标记">
          {{ currentRecord.uploadFlag || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="目录" :span="2">
          {{ currentRecord.folder || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="迁移状态">
          <el-tag
            v-if="currentRecord.migrationStatus"
            :type="(migrationStatusMap[currentRecord.migrationStatus]?.type || 'info') as any"
            size="small"
          >
            {{ migrationStatusMap[currentRecord.migrationStatus]?.label || currentRecord.migrationStatus }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="文件大小">
          {{ currentRecord.fileSize ? `${(currentRecord.fileSize / 1024).toFixed(1)} KB` : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="MD5" :span="2">
          {{ currentRecord.checksumMd5 || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </MrrPageShell>
</template>

<style scoped>
.pager {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  justify-content: flex-end;
  width: 100%;
}

.records-filter__field {
  display: grid;
  flex: 1 1 150px;
  gap: var(--mrr-space-1);
  min-width: 0;
}

.records-filter__field label {
  font-size: 12px;
  font-weight: 600;
  color: var(--mrr-foreground);
}

.records-filter-bar :deep(.mrr-filter-bar__actions) {
  flex: 0 0 auto;
  flex-wrap: nowrap;
  align-self: flex-end;
}

.records-query-context {
  display: grid;
  gap: var(--mrr-space-1);
  min-width: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--mrr-muted-foreground);
  overflow-wrap: anywhere;
}

.records-query-context p {
  margin: 0;
}

.records-query-context strong {
  color: var(--mrr-foreground);
}

.records-query-context__pending {
  padding-left: var(--mrr-space-2);
  color: var(--mrr-foreground);
  border-left: 3px solid var(--mrr-tone-warning);
}

@media (max-width: 760px) {
  .records-filter__field {
    flex: 1 1 100%;
    width: 100%;
  }
}

.records-page-size-select {
  width: 120px;
}

.pager__navigation {
  display: flex;
  flex: 0 0 auto;
  gap: var(--mrr-space-2);
  align-items: center;
  white-space: nowrap;
}

.text-muted {
  color: var(--text-secondary);
}
</style>
