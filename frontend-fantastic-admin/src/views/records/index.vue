<script setup lang="ts">
import type { ScanRecord } from '@/api/types'
import type { MrrTableAction } from '@/components/MrrTableActions/types'
import { Download, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { batchDownloadRecords, getScanCursor } from '@/api/modules/records'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import MrrTableActions from '@/components/MrrTableActions/index.vue'
import { useTableActionLayout } from '@/composables/useTableActionLayout'
import { getMedicalRecordTypeLabel, MEDICAL_RECORD_TYPES } from '@/constants/medical-record-types'

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
    icon: 'i-ri:eye-line',
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
const cursors = ref<number[]>([0])
const nextCursor = ref<number | undefined>()
const hasMore = ref(false)
let version = 0
async function loadData() {
  const current = ++version
  loading.value = true
  error.value = ''
  try {
    const submitted = appliedQuery.value
    const res = await getScanCursor({ bah: submitted.bah.trim() || undefined, sjh: submitted.sjh.trim() || undefined,
      brxh: submitted.brxh.trim() || undefined, openerNo: submitted.openerNo.trim() || undefined, btype: submitted.btype ? Number(submitted.btype) : undefined },
    cursors.value[pageNum.value - 1] || 0, pageSize.value)
    if (current !== version) return
    if (!Array.isArray(res.data?.list)) throw new Error('记录响应格式异常，请重试')
    list.value = res.data.list
    selectedRows.value = []
    hasMore.value = res.data.hasMore
    nextCursor.value = res.data.nextCursorId
  } catch (exception) {
    if (current === version) { error.value = exception instanceof Error ? exception.message : '记录读取失败'; list.value = []; hasMore.value = false }
  } finally { if (current === version) loading.value = false }
}
function handleSearch() { appliedQuery.value = { ...query }; pageNum.value = 1; cursors.value = [0]; void loadData() }
function resetFilters() { Object.assign(query, { bah: '', brxh: '', sjh: '', openerNo: '', btype: '' }); handleSearch() }
function nextPage() { if (!hasMore.value || nextCursor.value == null) return; cursors.value[pageNum.value] = nextCursor.value; pageNum.value++; void loadData() }
function previousPage() { if (pageNum.value <= 1) return; pageNum.value--; void loadData() }
onMounted(loadData)

const summaryCards = computed(() => [
  { label: '当前页记录数', value: list.value.length, note: '当前筛选结果中已加载的数据条数' },
  { label: '读取方式', value: '游标', note: '按完整编号筛选；未执行全表总数统计' },
  { label: '已选记录', value: selectedRows.value.length, note: '可用于批量打包下载' },
  { label: '当前页码', value: pageNum.value, note: `每页 ${pageSize.value} 条` },
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
  catch (error: any) {
    ElMessage.error(error?.message || '批量下载失败')
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
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">
          Scan Records
        </p>
        <h2>记录管理</h2>
        <p class="subtitle">
          按完整编号检索扫描记录，采用游标读取；病案号、上架号兼容原始前导零。
        </p>
      </div>
      <el-button type="primary" :loading="downloading" @click="handleBatchDownload">
        <el-icon><Download /></el-icon>
        批量下载
      </el-button>
    </div>

    <section class="mrr-metric-grid mrr-metric-grid--four">
      <MrrMetricCard v-for="item in summaryCards" :key="item.label" v-bind="item" />
    </section>

    <el-alert v-if="unapplied" title="筛选已修改，点击查询后生效；翻页仍使用上次已提交条件" type="info" :closable="false" />
    <el-card shadow="never">
      <el-form inline @submit.prevent>
        <el-form-item label="病案号">
          <el-input v-model="query.bah" clearable placeholder="输入病案号" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="病人序号">
          <el-input v-model="query.brxh" clearable placeholder="输入病人序号" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="上架号">
          <el-input v-model="query.sjh" clearable placeholder="输入上架号" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="扫描人员">
          <el-input v-model="query.openerNo" clearable placeholder="输入工号" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.btype" clearable placeholder="全部类型" style="width: 180px;">
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="resetFilters">
            重置
          </el-button>
        </el-form-item>
      </el-form>

      <AppLoading v-if="loading" type="table" :rows="8" />
      <AppError v-else-if="error" :message="error" @retry="loadData" />
      <AppEmpty v-else-if="!list.length" description="暂无扫描记录" />
      <el-table
        v-else
        :data="list"
        stripe
        style="margin-top: 12px;"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="bah" label="病案号" min-width="140" />
        <el-table-column prop="brxh" label="病人序号" min-width="120" />
        <el-table-column prop="sjh" label="上架号" min-width="120" />
        <el-table-column prop="filename" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column prop="btype" label="类型" width="160">
          <template #default="{ row }">
            {{ typeLabel(row.btype) }}
          </template>
        </el-table-column>
        <el-table-column prop="pages" label="页数" width="80" />
        <el-table-column prop="openerNo" label="扫描人员" min-width="110" />
        <el-table-column prop="folder" label="目录" min-width="140" show-overflow-tooltip />
        <el-table-column prop="migrationStatus" label="迁移状态" width="100">
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

      <div class="pager">
        <el-select v-model="pageSize" aria-label="每批条数" style="width:120px" @change="handleSearch"><el-option v-for="size in [10,20,50,100]" :key="size" :value="size" :label="`${size}条/批`" /></el-select>
        <el-button :disabled="pageNum <= 1 || loading" @click="previousPage">上一批</el-button>
        <span>第 {{ pageNum }} 批</span>
        <el-button :disabled="!hasMore || loading" @click="nextPage">下一批</el-button>
      </div>
    </el-card>

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
  </div>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 20px;
}

.page-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

h2 {
  margin: 0;
  font-size: 28px;
}

.subtitle {
  margin: 8px 0 0;
  color: var(--text-secondary);
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.text-muted {
  color: var(--text-secondary);
}
</style>
