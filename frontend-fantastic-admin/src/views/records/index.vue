<script setup lang="ts">
import type { PaginatedResult, ScanRecord } from '@/api/types'
import { ElMessage } from 'element-plus'
import { computed, ref } from 'vue'
import { batchDownloadRecords, getScanByCondition, getScanList } from '@/api/modules/records'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import MrrDataTablePanel from '@/components/MrrDataTablePanel/index.vue'
import MrrFilterBar from '@/components/MrrFilterBar/index.vue'
import MrrMetricCard from '@/components/MrrMetricCard/index.vue'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import MrrSelectionBar from '@/components/MrrSelectionBar/index.vue'
import MrrStatusTag from '@/components/MrrStatusTag/index.vue'
import { getMedicalRecordTypeLabel, MEDICAL_RECORD_TYPES } from '@/constants/medical-record-types'
import { useCrudList } from '@/composables/useCrudList'

defineOptions({ name: 'RecordsPage' })

type MetricTone = 'blue' | 'green' | 'amber' | 'slate'
type StatusTone = 'success' | 'info' | 'warning' | 'danger' | 'neutral'

interface RecordsQuery {
  bah: string
  brxh: string
  sjh: string
  openerNo: string
  btype: string
}

interface MetricItem {
  label: string
  value: number
  note: string
  tone: MetricTone
  icon: string
}

const migrationStatusMap: Record<string, { label: string, tone: StatusTone }> = {
  not_migrated: { label: '未迁移', tone: 'warning' },
  migrated: { label: '已迁移', tone: 'success' },
  verified: { label: '已验证', tone: 'success' },
}

const downloading = ref(false)
const detailVisible = ref(false)
const currentRecord = ref<ScanRecord | null>(null)
const selectedRows = ref<ScanRecord[]>([])
const tableRef = ref<{ clearSelection: () => void } | null>(null)
const error = ref('')

const typeOptions = [
  { label: '全部类型', value: '' },
  ...MEDICAL_RECORD_TYPES.map(item => ({
    label: item.label,
    value: String(item.value),
  })),
]

const { list, total, loading, pageNum, pageSize, query, handleSearch, resetFilters, loadData } = useCrudList<
  ScanRecord,
  RecordsQuery
>({
  fetchApi: async (params) => {
    const { page, size, ...rest } = params
    const request = {
      bah: rest.bah?.trim() || undefined,
      brxh: rest.brxh?.trim() || undefined,
      sjh: rest.sjh?.trim() || undefined,
      openerNo: rest.openerNo?.trim() || undefined,
      btype: rest.btype ? Number(rest.btype) : undefined,
    }
    const hasConditions = Object.values(request).some(value => value !== undefined)

    try {
      error.value = ''
      return hasConditions
        ? await getScanByCondition(request, page, size)
        : await getScanList({ page, size }) as unknown as Promise<import('@/api/types').ApiResult<PaginatedResult<ScanRecord>>>
    }
    catch (cause: unknown) {
      error.value = cause instanceof Error ? cause.message : '加载扫描记录失败'
      return { list: [], total: 0, page: 1, size: 20 } as unknown as PaginatedResult<ScanRecord>
    }
  },
  defaultQuery: { bah: '', brxh: '', sjh: '', openerNo: '', btype: '' },
})

const migratedCount = computed(() => list.value.filter(item => ['migrated', 'verified'].includes(String(item.migrationStatus || ''))).length)
const pendingMigrationCount = computed(() => list.value.filter(item => item.migrationStatus === 'not_migrated').length)

const summaryCards = computed<MetricItem[]>(() => [
  {
    label: '扫描记录总数',
    value: total.value,
    note: '符合当前筛选条件的全部记录',
    tone: 'blue',
    icon: 'i-ant-design:database-outlined',
  },
  {
    label: '当前页记录',
    value: list.value.length,
    note: `第 ${pageNum.value} 页，每页 ${pageSize.value} 条`,
    tone: 'slate',
    icon: 'i-ant-design:file-search-outlined',
  },
  {
    label: '已迁移或验证',
    value: migratedCount.value,
    note: '当前页已进入迁移完成状态',
    tone: 'green',
    icon: 'i-ant-design:cloud-done-outlined',
  },
  {
    label: '待迁移',
    value: pendingMigrationCount.value,
    note: '当前页尚未完成迁移的记录',
    tone: 'amber',
    icon: 'i-ant-design:clock-circle-outlined',
  },
])

function handleSelectionChange(rows: ScanRecord[]) {
  selectedRows.value = rows
}

function clearSelection() {
  tableRef.value?.clearSelection()
  selectedRows.value = []
}

function runSearch() {
  clearSelection()
  handleSearch()
}

function runReset() {
  clearSelection()
  resetFilters()
}

async function refreshData() {
  clearSelection()
  await loadData()
}

function openDetail(row: ScanRecord) {
  currentRecord.value = row
  detailVisible.value = true
}

async function handleBatchDownload() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先选择要下载的记录')
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
    ElMessage.success('批量下载已开始')
  }
  catch (cause: any) {
    ElMessage.error(cause?.message || '批量下载失败')
  }
  finally {
    downloading.value = false
  }
}

function typeLabel(value: unknown) {
  return value ? getMedicalRecordTypeLabel(value as number | string) : '-'
}

function migrationMeta(status: unknown) {
  const value = String(status || '')
  return migrationStatusMap[value] || { label: value || '未知', tone: 'neutral' as const }
}
</script>

<template>
  <MrrPageShell width="fluid">
    <MrrPageHeader
      title="扫描影像记录"
      description="按病案、患者、上架号、扫描人员和病案类型检索影像记录，并对选中记录执行批量下载。"
      icon="i-ant-design:database-outlined"
    >
      <template #actions>
        <el-button :loading="loading" @click="refreshData">
          <FaIcon name="i-ri:refresh-line" />
          刷新数据
        </el-button>
      </template>
    </MrrPageHeader>

    <section class="mrr-metric-grid" aria-label="扫描记录概览">
      <MrrMetricCard
        v-for="item in summaryCards"
        :key="item.label"
        :label="item.label"
        :value="item.value"
        :note="item.note"
        :tone="item.tone"
        :icon="item.icon"
      />
    </section>

    <MrrDataTablePanel
      title="扫描记录列表"
      description="筛选条件、批量操作、记录结果和分页保持在同一任务区域。"
      icon="i-ant-design:unordered-list-outlined"
      :count="total"
    >
      <template #filters>
        <MrrFilterBar variant="embedded">
          <el-input
            v-model="query.bah"
            class="records-filter records-filter--code"
            clearable
            aria-label="病案号"
            placeholder="病案号"
            @keyup.enter="runSearch"
          />
          <el-input
            v-model="query.brxh"
            class="records-filter records-filter--code"
            clearable
            aria-label="病人序号"
            placeholder="病人序号"
            @keyup.enter="runSearch"
          />
          <el-input
            v-model="query.sjh"
            class="records-filter records-filter--code"
            clearable
            aria-label="上架号"
            placeholder="上架号"
            @keyup.enter="runSearch"
          />
          <el-input
            v-model="query.openerNo"
            class="records-filter records-filter--operator"
            clearable
            aria-label="扫描人员工号"
            placeholder="扫描人员工号"
            @keyup.enter="runSearch"
          />
          <el-select
            v-model="query.btype"
            class="records-filter records-filter--type"
            clearable
            aria-label="病案类型"
            placeholder="全部类型"
          >
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>

          <template #actions>
            <el-button type="primary" :loading="loading" @click="runSearch">
              <FaIcon name="i-ri:search-line" />
              查询
            </el-button>
            <el-button @click="runReset">
              <FaIcon name="i-ri:restart-line" />
              重置
            </el-button>
          </template>
        </MrrFilterBar>
      </template>

      <MrrSelectionBar
        :count="selectedRows.length"
        :total="list.length"
        label="当前页已选择"
        @clear="clearSelection"
      >
        <el-button type="primary" :loading="downloading" @click="handleBatchDownload">
          <FaIcon name="i-ri:download-2-line" />
          批量下载
        </el-button>
      </MrrSelectionBar>

      <div v-if="loading" class="records-state">
        <AppLoading type="table" :rows="8" />
      </div>
      <div v-else-if="error" class="records-state">
        <AppError :message="error" @retry="refreshData" />
      </div>
      <div v-else-if="!list.length" class="records-state">
        <AppEmpty description="暂无符合条件的扫描记录" />
      </div>

      <el-table
        v-else
        ref="tableRef"
        :data="list"
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="bah" label="病案号" min-width="140" />
        <el-table-column prop="brxh" label="病人序号" min-width="120" />
        <el-table-column prop="sjh" label="上架号" min-width="120" />
        <el-table-column prop="filename" label="文件名" min-width="240" show-overflow-tooltip />
        <el-table-column prop="btype" label="病案类型" min-width="160">
          <template #default="{ row }">
            {{ typeLabel(row.btype) }}
          </template>
        </el-table-column>
        <el-table-column prop="pages" label="页数" width="80" align="right" />
        <el-table-column prop="openerNo" label="扫描人员" min-width="110" />
        <el-table-column prop="folder" label="目录" min-width="180" show-overflow-tooltip />
        <el-table-column prop="migrationStatus" label="迁移状态" width="120">
          <template #default="{ row }">
            <MrrStatusTag
              :status="row.migrationStatus"
              :label="migrationMeta(row.migrationStatus).label"
              :tone="migrationMeta(row.migrationStatus).tone"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="92" fixed="right" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #pagination>
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="runSearch"
          @current-change="refreshData"
        />
      </template>
    </MrrDataTablePanel>

    <el-dialog v-model="detailVisible" title="扫描记录详情" width="min(720px, calc(100vw - 32px))" :close-on-click-modal="false">
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
        <el-descriptions-item label="病案类型">
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
          <MrrStatusTag
            :status="currentRecord.migrationStatus"
            :label="migrationMeta(currentRecord.migrationStatus).label"
            :tone="migrationMeta(currentRecord.migrationStatus).tone"
          />
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
.records-filter--code {
  width: 152px;
}

.records-filter--operator {
  width: 176px;
}

.records-filter--type {
  width: 190px;
}

.records-state {
  min-height: 420px;
}

@media (width <= 760px) {
  .records-filter {
    flex: 1 1 100%;
    width: 100%;
  }
}
</style>
