<script setup lang="ts">
import { Download, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, ref } from 'vue'
import { batchDownloadRecords, getScanByCondition, getScanList } from '@/api/modules/records'
import type { PaginatedResult, ScanRecord } from '@/api/types'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import { useCrudList } from '@/composables/useCrudList'
import { MEDICAL_RECORD_TYPE_OPTIONS, getMedicalRecordTypeLabel } from '@/constants/medical-record-types'

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
  ...MEDICAL_RECORD_TYPE_OPTIONS.map(item => ({
    label: item.label,
    value: String(item.value),
  })),
]

// CRUD 列表逻辑：复用 useCrudList composable，消除重复的分页/查询/重置代码
interface RecordsQuery {
  bah: string
  brxh: string
  sjh: string
  openerNo: string
  btype: string
}

const { list, total, loading, pageNum, pageSize, query, handleSearch, resetFilters, loadData } = useCrudList<
  ScanRecord,
  RecordsQuery
>({
  fetchApi: async (params) => {
    const { page, size, ...rest } = params
    // 构造后端请求体：空字符串转 undefined，btype 字符串转 number
    const request = {
      bah: rest.bah?.trim() || undefined,
      brxh: rest.brxh?.trim() || undefined,
      sjh: rest.sjh?.trim() || undefined,
      openerNo: rest.openerNo?.trim() || undefined,
      btype: rest.btype ? Number(rest.btype) : undefined,
    }
    const hasConditions = Object.values(request).some(v => v !== undefined)
    try {
      error.value = ''
      const res = hasConditions
        ? await getScanByCondition(request, page, size)
        : await getScanList({ page, size }) as unknown as Promise<import('@/api/types').ApiResult<PaginatedResult<ScanRecord>>>
      return res
    }
    catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '加载扫描记录失败'
      error.value = msg
      return { list: [], total: 0, page: 1, size: 20 } as unknown as PaginatedResult<ScanRecord>
    }
  },
  defaultQuery: { bah: '', brxh: '', sjh: '', openerNo: '', btype: '' },
})

const summaryCards = computed(() => [
  { label: '当前页记录数', value: list.value.length, note: '当前筛选结果中已加载的数据条数' },
  { label: '总记录数', value: total.value, note: '符合当前筛选条件的扫描记录总量' },
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
  return getMedicalRecordTypeLabel(value as number | string | null | undefined, '-')
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
          管理扫描记录、按条件检索明细，并支持多选后批量打包下载。
        </p>
      </div>
      <el-button type="primary" :loading="downloading" @click="handleBatchDownload">
        <el-icon><Download /></el-icon>
        批量下载
      </el-button>
    </div>

    <section class="summary-grid">
      <el-card v-for="item in summaryCards" :key="item.label" shadow="never">
        <div class="summary-label">
          {{ item.label }}
        </div>
        <div class="summary-value">
          {{ item.value }}
        </div>
        <div class="summary-note">
          {{ item.note }}
        </div>
      </el-card>
    </section>

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
          <el-select v-model="query.btype" clearable placeholder="全部类型" style="width: 140px;">
            <el-option v-for="item in typeOptions" :key="item.label" :label="item.label" :value="item.value" />
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
        <el-table-column prop="btype" label="类型" width="140">
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
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openDetail(row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="loadData"
        />
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

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.summary-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 800;
  color: var(--text-primary);
}

.summary-note {
  margin-top: 8px;
  font-size: 12px;
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
